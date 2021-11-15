package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ServletContext;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.mulesoft.agent.Scheduler;
import com.appdynamics.mulesoft.agent.TransactionDictionary;

import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSideInterceptor extends MyBaseInterceptor {
    private static final ConcurrentHashMap<String, TransactionDictionary> transactionsMap = new ConcurrentHashMap<>();
    private final Scheduler scheduler;
    IReflector getException; // ExceptionNotification
    IReflector getRequestAttributes; // getEvent().getMessage().getAttributes().getValue()
    IReflector getRequestAttributesType; // getEvent().getMessage().getAttributes().getType().getName()

    // org.mule.extension.http.api.HttpRequestAttributes
    // https://github.com/mulesoft/mule-http-connector/blob/master/src/main/java/org/mule/extension/http/api/HttpRequestAttributes.java
    IReflector getScheme;
    IReflector getMethod;
    IReflector getRequestUri;
    IReflector getQueryParams;
    IReflector getLocalAddress;
    IReflector getRemoteAddress;
    IReflector getHeaders;

    // https://github.com/mulesoft/mule-jms-connector/blob/master/src/main/java/org/mule/extensions/jms/api/message/JmsHeaders.java
    IReflector getJmsDestinationName;
    IReflector isJmsDestinationTypeTopic;

    // https://github.com/estebanwasinger/jms-commons/blob/master/src/main/java/org/mule/jms/commons/api/message/JmsMessageProperties.java
    IReflector getJmsUserProperty;

    IReflector getConfigurationProperty;
    IReflector getLocationUri;

    private static final String NON_HTTP_JMS_TRANS_PROPERTY_KEY = "TRACK_NON_HTTP_JMS_TRANS";
    private static final String KEEP_JMS_CONNECTION_PARAMS_KEY = "KEEP_JMS_CONNECTION_PARAMS";

    private static boolean ignoreOtherRequestTypes = true;
    private static boolean keepJmsConnectionParams = false;

    public AgentSideInterceptor() {
        super();
        scheduler = Scheduler.getInstance(30000L, 120000L, transactionsMap);
        scheduler.start();

        getException = makeInvokeInstanceMethodReflector("getException");

        getRequestAttributes = getNewReflectionBuilder().invokeInstanceMethod("getEvent", true) // returns
                // org.mule.runtime.api.event.Event
                .invokeInstanceMethod("getMessage", true) // returns org.mule.runtime.api.message.Message
                .invokeInstanceMethod("getAttributes", true) // returns org.mule.runtime.api.metadata.TypedValue<T>
                .invokeInstanceMethod("getValue", true) // returns
                // com.mulesoft.mule.runtime.gw.analytics.model.HttpRequestAttributes
                // OR org.mule.jms.commons.api.message.JmsAttributes
                .build();

        getRequestAttributesType = getNewReflectionBuilder().invokeInstanceMethod("getEvent", true) // returns
                // org.mule.runtime.api.event.Event
                .invokeInstanceMethod("getMessage", true) // returns org.mule.runtime.api.message.Message
                .invokeInstanceMethod("getAttributes", true) // returns org.mule.runtime.api.metadata.TypedValue<T>
                .invokeInstanceMethod("getDataType", true).invokeInstanceMethod("getType", true)// returns Class<?>
                .invokeInstanceMethod("getName", true) // returns String
                .build();

        getScheme = makeInvokeInstanceMethodReflector("getScheme"); // String
        getMethod = makeInvokeInstanceMethodReflector("getMethod"); // String
        getRequestUri = makeInvokeInstanceMethodReflector("getRequestUri"); // String
        getQueryParams = makeInvokeInstanceMethodReflector("getQueryParams"); // Map<String,String>
        getLocalAddress = makeInvokeInstanceMethodReflector("getLocalAddress"); // String
        getRemoteAddress = makeInvokeInstanceMethodReflector("getRemoteAddress"); // String
        getHeaders = makeInvokeInstanceMethodReflector("getHeaders"); // =~ Map<String,String> OR JmsHeaders

        getJmsDestinationName = getNewReflectionBuilder().invokeInstanceMethod("getJMSDestination", true) // returns a
                // JmsDestination
                // Object
                .invokeInstanceMethod("getDestination", true) // returns String
                .build();
        isJmsDestinationTypeTopic = getNewReflectionBuilder().invokeInstanceMethod("getJMSDestination", true) // returns
                // a
                // JmsDestination
                // Object
                .invokeInstanceMethod("getDestinationType", true) // returns DestinationType
                .invokeInstanceMethod("isTopic", true) // returns boolean
                .build();
        getJmsUserProperty = getNewReflectionBuilder().invokeInstanceMethod("getProperties", true) // returns a
                // https://github.com/estebanwasinger/jms-commons/blob/master/src/main/java/org/mule/jms/commons/api/message/JmsMessageProperties.java
                // Object
                .invokeInstanceMethod("getUserProperties", true) // returns Map<String,Object>
                .build();

        getConfigurationProperty = makeInvokeInstanceMethodReflector("getConfigurationProperty",
                String.class.getCanonicalName()); // "_muleConfigurationAttributesResolver"
        getLocationUri = makeInvokeInstanceMethodReflector("getLocationUri"); // returns String, need to split on last
        // "/"

        String trackOtherRequestTypes = getProperty(NON_HTTP_JMS_TRANS_PROPERTY_KEY + "-enabled");
        AgentSideInterceptor.ignoreOtherRequestTypes = !trackOtherRequestTypes.equalsIgnoreCase("true"); // this must be
        // inverse
        // since the
        // prop is to
        // track and
        // the bool is
        // to ignore

        String keepJmsConnectionParams = getProperty(KEEP_JMS_CONNECTION_PARAMS_KEY + "-enabled");
        AgentSideInterceptor.keepJmsConnectionParams = !keepJmsConnectionParams.equalsIgnoreCase("true"); // this must
        // be inverse
        // since the
        // prop is to
        // track and
        // the bool is
        // to ignore
    }

    @Override
    public Object onMethodBegin(Object appDynamicsNotificationHandler, String className, String methodName,
            Object[] params) {
        getLogger().debug("AgentSideInterceptor.onMethodBegin() for " + className + "." + methodName + "()");
        Object notification = params[0];
        String transactionId = (String) params[1];
        String artifactId = (String) params[2];
        String flowName = (String) params[3];
        TransactionDictionary transactionDictionary = getTransactionMap(transactionId);

        // the domain is prepended to the api name, but we can drop the default domain
        // because we don't need it
        String formattedArtifactId = artifactId.replace("domain/default/app/", "");

        if (transactionDictionary != null) {
            // default the flow name just in case the current event didn't have a flow name
            if (flowName == null)
                flowName = transactionDictionary.lastFlowName;
        } else if (!methodName.equals("handleFlowStartEvent") && !methodName.startsWith("handleTransaction")
                && !methodName.startsWith("handleMessage") && !methodName.startsWith("handleConnection")
                && !methodName.startsWith("handleManagement") && !ignoreOtherRequestTypes) {
            getLogger().warn(String.format(
                    "AgentSideInterceptor.onMethodBegin() for %s.%s() Transaction Dictionary is null? for transaction id: %s",
                    className, methodName, transactionId));
        }

        switch (methodName) {
            case "handleFlowStartEvent": {

                // check for an existing transaction & start one if necessary
                if ((transactionDictionary == null || !transactionDictionary.isStarted()) && newTransaction(
                        transactionId, transactionDictionary, notification, className, methodName, flowName)) {

                    // for some reason, we were told to skip this transaction,
                    // so remove the dictionary since we aren't going to track this transaction
                    // (must be done to prevent lots of warnings)
                    transactionsMap.remove(transactionId);
                    return null;
                }
                // should never happen, but have to make the compiler happy
                else if (transactionDictionary == null)
                    return null;

                // start a segment for each flow
                TransactionDictionary.FlowSegment segment;
                if (!transactionDictionary.isExistingSegment(flowName)) {
                    Transaction segmentTransaction = AppdynamicsAgent
                            .startSegmentNoHandoff(transactionDictionary.getTransaction().getUniqueIdentifier());

                    segment = transactionDictionary.addSegment(flowName, segmentTransaction);
                } else {
                    segment = transactionDictionary.getSegment(flowName);
                }

                // gather data for data collectors and analytics
                if (flowName != null && segment != null && segment.number != null) {
                    Transaction transactionSegment = segment.transactionSegment;
                    if (transactionSegment != null) {
                        transactionSegment.collectData(String.format("Flow|%s|%s", formattedArtifactId, segment.number),
                                flowName, snapshotDatascopeOnly);
                        transactionSegment.collectData("Mule-APIFlow",
                                String.format("%s|%s", formattedArtifactId, flowName), analyticsDatascopeOnly);
                        transactionSegment.collectData(
                                String.format("Flow|%s|%s|StartTimestamp", formattedArtifactId, segment.number),
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS zzz")
                                        .withZone(ZoneId.systemDefault())),
                                snapshotDatascopeOnly);
                    }
                } else {
                    getLogger().info(String.format("Segment is null for flow: %s", flowName));
                }

                // process any events captured before the flow start event fired:
                List<AbstractMap.SimpleImmutableEntry<String, String>> listOfData = transactionDictionary
                        .drainQueueForFlow(flowName);
                if (listOfData != null)
                    for (AbstractMap.SimpleImmutableEntry<String, String> pair : listOfData) {
                        getLogger().debug("Drained queue for flow: " + flowName + " sending data to snapshot "
                                + pair.getKey() + " = " + pair.getValue());
                        if (segment != null) {
                            Transaction transactionSegment = segment.transactionSegment;
                            if (transactionSegment != null)
                                transactionSegment.collectData(pair.getKey(), pair.getValue(), snapshotDatascopeOnly);
                        }
                    }
                break;
            }
            case "handleExceptionEvent": // fall through
                break;
            case "handleFlowEndEvent": {
                if (transactionDictionary == null)
                    return null;

                // find the segment for the current flow
                TransactionDictionary.FlowSegment segment = transactionDictionary.getSegment(flowName);
                if (segment == null) {
                    getLogger().info(
                            String.format("%s OOPS: Current Segment is null??? flowname: %s", methodName, flowName));
                } else {
                    // let the agent know the current segment is over
                    segment.transactionSegment.endSegment();
                    getLogger().debug(String.format("%s = %s", segment, segment.getTimerDuration()));
                }

                // check for this odd condition
                if (isFakeTransaction(transactionDictionary.getTransaction())) {
                    getLogger().info(
                            String.format("%s Transaction ended before the handleFlowEndEvent fired!", methodName));
                }

                if (segment != null && segment.number == 0) {
                    getLogger()
                            .debug(String.format("Removing transaction because end of segment 0: %s", transactionId));
                    segment.transactionSegment.end();
                    transactionDictionary.finish();
                }
                break;
            }
            case "handleProcessorStartEvent": {
                String stepName = (String) params[4];
                String spanType = (String) params[5];
                // String spanId = (String) params[6];

                if (stepName == null)
                    stepName = "UNKNOWN-STEP";
                if (spanType == null)
                    spanType = "UNKNOWN-SPANTYPE";
                if (transactionDictionary == null)
                    return null;

                Transaction transaction = transactionDictionary.getTransaction();
                if (transaction == null) {
                    getLogger().info(String.format(
                            "handleProcessorStartEvent OOPS: Current Transaction is null??? flowname: %s stepname: %s",
                            flowName, stepName));
                } else if (isFakeTransaction(transaction)) {
                    getLogger().info(String.format(
                            "handleProcessorStartEvent OOPS: Current Transaction is fake??? flowname: %s stepname: %s",
                            flowName, stepName));
                } else {
                    // find the segment this processor should belong to
                    TransactionDictionary.FlowSegment segment = transactionDictionary.getSegment(flowName);

                    // collect a data collector for this processor
                    if (segment != null) {
                        transaction
                                .collectData(
                                        String.format("Flow|%s|%s|%s|Processors", formattedArtifactId, segment.number,
                                                flowName),
                                        String.format("%s-%s", stepName, spanType), snapshotDatascopeOnly);
                    } else {
                        transaction.collectData(
                                String.format("Flow|%s|UNKNOWN_STEP|%s|Processors", formattedArtifactId, flowName),
                                String.format("%s-%s", stepName, spanType), snapshotDatascopeOnly);
                    }
                }
                break;
            }
            case "handleProcessorEndEvent": {
                // String stepName = (String) params[3];
                // String spanType = (String) params[4];
                // String spanId = (String) params[5];

                break;
            }
            case "handleManagementComponentQueueExhausted": {
                String eventName = (String) params[1];
                String actionIdentifier = (String) params[2];
                Object source = params[4];

                // not really sure what this is...
                getLogger().debug(String.format(
                        "handleManagementComponentQueueExhausted: %s action identifier: %s notification: %s api: %s source: %s",
                        eventName, actionIdentifier, notification, formattedArtifactId, source));

                break;
            }
            case "handleMessageRequestReceived": { // fires when a listener triggers in the code
                String eventName = (String) params[1];
                String actionIdentifier = (String) params[2];
                transactionId = (String) params[6];

                // find the location of the listener and split it so we know the processor type
                String locationUri = getReflectiveString(notification, getLocationUri, "x/UNKNOWN-REQUESTTYPE");
                String requestType = locationUri.substring(locationUri.lastIndexOf('/') + 1);

                getLogger().debug(String.format(
                        "handleMessageRequestReceived: %s action identifier: %s notification: %s api: %s requestType: %s",
                        eventName, actionIdentifier, notification, formattedArtifactId, requestType));

                // retrieve/create the current transaction dictionary for the mule correlation
                // id
                transactionDictionary = transactionsMap.computeIfAbsent(transactionId,
                        s -> new TransactionDictionary(s));

                // add the request type to the dictionary (http:listener, ibm-mq:listener,
                // scheduler...)
                transactionDictionary.setSourceType(requestType);

                break;
            }
            case "handleMessageRequestResponse": {

                ///
                /// Failed attempt to force the agent to stop the transaction when the listener
                /// sends the response
                ///

                transactionId = (String) params[6];
                transactionDictionary = getTransactionMap(transactionId);
                if (transactionDictionary == null)
                    return null;
                // getLogger().debug(String.format("%s.%s() ending transaction with id: %s uuid:
                // %s", className,
                // methodName, params[6],
                // transactionDictionary.getTransaction().getUniqueIdentifier()));
                // transactionDictionary.getTransaction().end();

                Transaction tran = AppdynamicsAgent.getTransaction();
                if (!isFakeTransaction(tran)) {
                    // getLogger().debug("found another transaction, uuid: " +
                    // tran.getUniqueIdentifier());
                    // tran.end();
                }

                // getLogger().debug(String.format("%s.%s() ended transaction with id: %s and
                // uuid: %s", className,
                // methodName, params[6],
                // transactionDictionary.getTransaction().getUniqueIdentifier()));
                break;
            }
            default: {
                break;
            }
        }
        return null;

    }

    private boolean newTransaction(String transactionId, TransactionDictionary transactionDictionary,
            Object notification, String className, String methodName, String flowName) {
        Object requestAttributes = getReflectiveObject(notification, getRequestAttributes);
        Transaction appDynTransaction = null;
        String appdCorrelationHeader = getCorrelationHeader(requestAttributes);

        if (isHttpRequest(requestAttributes) || isJmsRequest(requestAttributes)) {

            // instead of creating a new transaction, just use the existing one
            appDynTransaction = AppdynamicsAgent.getTransaction();

            // ignore this call if there isn't already a transaction running
            if (isFakeTransaction(appDynTransaction)) {
                return true;
            }
            /*
             * JMS if we want custom data, we need to reflect it out:
             * https://github.com/mulesoft/mule-jms-connector/tree/master/src/main/java/org/
             * mule/extensions/jms/api/message
             * https://github.com/estebanwasinger/jms-commons/tree/master/src/main/java/org/
             * mule/jms/commons/api/message
             */
        } else { // edge case, unknown transaction request type?

            // check if there is an existing transaction (controller side POJO rule or
            // something)
            appDynTransaction = AppdynamicsAgent.getTransaction();

            // look up the request type
            String requestType;
            if (transactionDictionary == null || transactionDictionary.getSourceType() == null)
                requestType = "x/UNKNOWN-REQUEST-TYPE";
            else
                requestType = transactionDictionary.getSourceType();

            // check the property from the file on whether to track non http or jms
            // transactions
            if (!ignoreOtherRequestTypes) {

                if (flowName == null)
                    flowName = "UNKNOWN-FLOW";

                // start a transaction if there isn't one already
                if (isFakeTransaction(appDynTransaction)) {
                    appDynTransaction = AppdynamicsAgent.startTransaction(requestType + "." + flowName,
                            appdCorrelationHeader, EntryTypes.POJO, false);
                }

                getLogger().info(String.format(
                        "AgentSideInterceptor.onMethodBegin() for %s.%s() Oops, Unknown request type %s for flow %s agent found active transaction id: %s",
                        className, methodName, requestType, flowName,
                        appDynTransaction == null ? "NULL" : appDynTransaction.getUniqueIdentifier()));
            } else {
                getLogger().debug(String.format(
                        "AgentSideInterceptor.onMethodBegin() for %s.%s() Ignoring request type %s for flow %s agent found active transaction id: %s",
                        className, methodName, requestType, flowName,
                        appDynTransaction == null ? "NULL" : appDynTransaction.getUniqueIdentifier()));
                return true;
            }
        }

        // either create a new dictionary to hold the transaction or add the transaction
        // to the existing dict
        if (transactionDictionary == null) {
            transactionDictionary = new TransactionDictionary(appDynTransaction, transactionId);
            transactionsMap.put(transactionId, transactionDictionary);
        } else {
            transactionDictionary.setTransaction(appDynTransaction);
        }

        // collect the correlation id for tracing in the controller
        transactionDictionary.getTransaction().collectData("Mule-transactionId", transactionId, dataScopes);

        // ALWAYS mark the transaction as started now
        transactionDictionary.start();

        return false;

    }

    private String getJmsBTName(Object requestAttributes) {
        // find the jms headers from the request
        Object jmsHeaders = getReflectiveObject(requestAttributes, getHeaders);

        // find the jms destination header
        String jmsDestinationName = getReflectiveString(jmsHeaders, getJmsDestinationName, "UNKNOWN-DESTINATION");

        // remove the connection params from the end of the destination name
        jmsDestinationName = removeJmsConnectionParams(jmsDestinationName);

        // find if this is a queue vs topic
        String jmsDestinationType = "QUEUE";
        boolean isTypeTopic = (boolean) getReflectiveObject(jmsHeaders, isJmsDestinationTypeTopic);
        if (isTypeTopic)
            jmsDestinationType = "TOPIC";

        return "JMS." + jmsDestinationType + "." + jmsDestinationName;
    }

    private String removeJmsConnectionParams(String jmsDestinationName) {
        if (!keepJmsConnectionParams) {
            int iend = jmsDestinationName.indexOf("?");
            if (iend != -1) {
                return jmsDestinationName.substring(0, iend);
            }
        }
        return jmsDestinationName;
    }

    private ServletContext buildServletContext(Object httpRequestAttributes) {
        if (!isHttpRequest(httpRequestAttributes))
            return null;
        ServletContext.ServletContextBuilder contextBuilder = new ServletContext.ServletContextBuilder();
        String scheme = getReflectiveString(httpRequestAttributes, getScheme, "https");
        String uri = getReflectiveString(httpRequestAttributes, getRequestUri, "UNKNOWN-URI");
        try {
            contextBuilder.withURL(scheme + "://" + uri);
        } catch (MalformedURLException e) {
            getLogger().info("Bad URL: " + scheme + "://" + uri + " Abandoning!");
            return null;
        }

        contextBuilder.withRequestMethod(getReflectiveString(httpRequestAttributes, getMethod, "GET"));
        contextBuilder.withHeaders((Map<String, String>) getReflectiveObject(httpRequestAttributes, getHeaders));
        contextBuilder
                .withParameters((Map<String, String[]>) getReflectiveObject(httpRequestAttributes, getQueryParams));
        contextBuilder.withHostOriginatingAddress(
                getReflectiveString(httpRequestAttributes, getRemoteAddress, "UNKNOWN-REMOTEADDRESS"));
        contextBuilder
                .withHostValue(getReflectiveString(httpRequestAttributes, getLocalAddress, "UNKNOWN-LOCALADDRESS"));

        return contextBuilder.build();
    }

    private String getCorrelationHeader(Object requestAttributes) {
        String correlationHeader = null;

        // getting this header only works for HTTP, not JMS
        if (isHttpRequest(requestAttributes)) {
            Map<String, String> headers = (Map<String, String>) getReflectiveObject(requestAttributes, getHeaders);
            if (headers != null) {
                correlationHeader = headers.getOrDefault(CORRELATION_HEADER_KEY, null);
            }
        } else if (isJmsRequest(requestAttributes)) {
            Map<String, Object> userHeaders = (Map<String, Object>) getReflectiveObject(requestAttributes,
                    getJmsUserProperty);
            Object correlationHeaderObj = userHeaders.getOrDefault(CORRELATION_HEADER_KEY, null);
            if (correlationHeaderObj != null)
                correlationHeader = correlationHeaderObj.toString();
        }
        if (correlationHeader != null)
            getLogger().debug("Found AppD Correlation Header: " + correlationHeader);

        return correlationHeader;
    }

    private boolean isHttpRequest(Object request) {
        return request != null && request.getClass().getCanonicalName().contains("Http");
    }

    private boolean isJmsRequest(Object request) {
        return request != null && request.getClass().getCanonicalName().contains("JmsAttributes");
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params,
            Throwable exception, Object returnVal) {
        // no op
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<>();

        rules.add(new Rule.Builder("com.appdynamics.mulesoft.agent.listener.AppDynamicsNotificationHandler")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS).methodMatchString("handle")
                .methodStringMatchType(SDKStringMatchType.STARTSWITH).build());
        return rules;
    }

    private static TransactionDictionary getTransactionMap(String muleCorrelationId) {
        if (muleCorrelationId == null)
            return null;
        return transactionsMap.get(muleCorrelationId);
    }

    @Override
    public Map<String, String> getListOfCustomProperties() {
        HashMap<String, String> map = new HashMap<>();

        map.put(NON_HTTP_JMS_TRANS_PROPERTY_KEY + "-enabled", "false");
        map.put(KEEP_JMS_CONNECTION_PARAMS_KEY + "-enabled", "false");

        return map;
    }

    private static void removeTransaction(String muleCorrelationId) {
        transactionsMap.remove(muleCorrelationId);
    }
}
