package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;

import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;
import com.appdynamics.mulesoft.agent.listener.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class MuleServiceDeploymentInterceptor extends MyBaseInterceptor {

    // getters on the mule artifact that has been deployed
    IReflector getArtifactName;
    IReflector getArtifactId;
    IReflector getMuleVersion;
    IReflector getArtifactDescriptorName;
    IReflector getStatus;
    IReflector get;

    // getters and actions to setup our notification listeners
    IReflector registerListener;
    IReflector artifactContextAttribute;
    IReflector getRegistry;
    IReflector isListenerRegistered; //artifact.getMuleContext().getNotificationManager().isListenerRegistered( Listener )

    private static final Map<String, Map<String, Object>> notificationListenerDict = new HashMap<>();

    private static final String PIPELINE_MESSAGE_NOTIFICATION_LISTENER_KEY = "PIPELINE_MESSAGE";
    private static final String MESSAGE_PROCESSOR_NOTIFICATION_LISTENER_KEY = "MESSAGE_PROCESSOR_NOTIFICATION";
    private static final String EXCEPTION_NOTIFICATION_LISTENER_KEY = "EXCEPTION_NOTIFICATION";
    private static final String MANAGEMENT_NOTIFICATION_LISTENER_KEY = "MANAGEMENT_NOTIFICATION";
    private static final String CONNECTION_NOTIFICATION_LISTENER_KEY = "CONNECTION_NOTIFICATION";
    private static final String TRANSACTION_NOTIFICATION_LISTENER_KEY = "TRANSACTION_NOTIFICATION";
    private static final String CONNECTOR_MESSAGE_NOTIFICATION_LISTENER_KEY = "CONNECTOR_NOTIFICATION";

    private static final String APPLICATION_DEPLOYMENT_KEY = "APPLICATION_DEPLOYMENT";

    public MuleServiceDeploymentInterceptor() {
        super();

        getArtifactName = makeInvokeInstanceMethodReflector("getArtifactName"); //artifact.getArtifactName())
        getArtifactId = makeInvokeInstanceMethodReflector("getArtifactId"); //artifact.getArtifactId())
        getMuleVersion = makeInvokeInstanceMethodChainReflector("getDescriptor", "getMinMuleVersion", "toCompleteNumericVersion"); //artifact.getDescriptor().getMinMuleVersion().toCompleteNumericVersion())
        getArtifactDescriptorName = makeInvokeInstanceMethodChainReflector("getDescriptor", "getName"); //artifact.getDescriptor().getName())
        getStatus = makeInvokeInstanceMethodReflector("getStatus"); //enum

        registerListener = makeInvokeInstanceMethodReflector("registerListener", org.mule.runtime.api.notification.NotificationListener.class.getCanonicalName());
        artifactContextAttribute = makeAccessFieldValueReflector("artifactContext");
        getRegistry = makeInvokeInstanceMethodChainReflector("getMuleContext", "getRegistry");
        get = makeInvokeInstanceMethodReflector("get", String.class.getCanonicalName());

        isListenerRegistered = getNewReflectionBuilder()
                .invokeInstanceMethod("getMuleContext", true)
                .invokeInstanceMethod("getNotificationManager", true)
                .invokeInstanceMethod("isListenerRegistered", true, org.mule.runtime.api.notification.NotificationListener.class.getCanonicalName()) //returns Boolean
                .build();
    }


    @Override
    public Object onMethodBegin(Object artifact, String className, String methodName, Object[] params) {
        return null;
    }

    @Override
    public void onMethodEnd(Object state, Object artifact, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        Map<String, String> details = getDetails(artifact, exception);
        boolean failure = exception != null;
        Object status = getReflectiveObject(artifact, getStatus);
        /* init == CREATED || CREATED ???
            start == STARTED || doInit == DEPLOYMENT_FAILED
            stop == STOPPED || ???
         */
        getLogger().info(String.format("MuleServiceDeploymentInterceptor %s.%s(): %s status: %s failure? %s",
                className, methodName, details.get("artifactName"), status == null ? "NULL" : status.toString(), failure));

        String successText = null;
        String failureText = null;

        switch (methodName) {
            case "install":
                successText = "is installed: ";
                failureText = "failed during install: ";
                break;
            case "start":
                if (!failure)
                    deployAppdListenersOnApplication(artifact);
                break;
            case "doInit":
                successText = "started: ";
                failureText = "failed to start: ";
                break;
            case "stop":
                successText = "stopped: ";
                failureText = "failed to stop: ";
                break;
            default:
                break;
        }

        if (successText != null && failureText != null)
            publishEvent(String.format("A mule API %s %s", failure ? failureText : successText, details.get("artifactName")),
                    (failure ? "ERROR" : "INFO"), APPLICATION_DEPLOYMENT_KEY, details);
    }

    Map<String, String> getDetails(Object artifact) {
        return getDetails(artifact, null);
    }

    Map<String, String> getDetails(Object artifact, Throwable exception) {
        Map<String, String> details = new HashMap<>();

        details.put("artifactName", getReflectiveString(artifact, getArtifactName, "UNKNOWN-NAME"));
        details.put("artifactId", getReflectiveString(artifact, getArtifactId, "UNKNOWN-ID"));
        details.put("minMuleVersion", getReflectiveString(artifact, getMuleVersion, "UNKNOWN-VERSION"));
        details.put("artifactDescriptorName", getReflectiveString(artifact, getArtifactDescriptorName, "UNKNOWN-DESCRIPTORNAME"));
        details.put("applicationStatus", Optional.ofNullable(getReflectiveObject(artifact, getStatus)).orElse("UNKOWN-STATUS").toString());

        if (exception != null) {
            // get a string version of the stack trace
            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                exception.printStackTrace(pw);
                details.put("exceptionMessage", exception.getMessage());
                details.put("stackTrace", sw.toString());
            }
        }
        return details;
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule.Builder(
                "org.mule.runtime.module.deployment.impl.internal.application.DefaultMuleApplication")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("(install)|(start)|(stop)|(doInit)")
                .methodStringMatchType(SDKStringMatchType.REGEX)
                .build()
        );
        return rules;
    }

    @Override
    public Map<String, String> getListOfCustomProperties() {
        HashMap<String, String> map = new HashMap<>();

        map.put(PIPELINE_MESSAGE_NOTIFICATION_LISTENER_KEY + "-enabled", "true");
        map.put(MESSAGE_PROCESSOR_NOTIFICATION_LISTENER_KEY + "-enabled", "true");
        map.put(EXCEPTION_NOTIFICATION_LISTENER_KEY + "-enabled", "true");
        map.put(MANAGEMENT_NOTIFICATION_LISTENER_KEY + "-enabled", "true");
        map.put(CONNECTION_NOTIFICATION_LISTENER_KEY + "-enabled", "true");
        map.put(TRANSACTION_NOTIFICATION_LISTENER_KEY + "-enabled", "true");
        map.put(CONNECTOR_MESSAGE_NOTIFICATION_LISTENER_KEY + "-enabled", "true");

        return map;
    }

    private void deployAppdListenersOnApplication(Object artifact) {
        Object artifactContext = getReflectiveObject(artifact, artifactContextAttribute);
        Object registry = getReflectiveObject(artifactContext, getRegistry);
        String artifactId = getReflectiveString(artifact, getArtifactId, "UNKNOWN-ID");
        Object notificationRegistry = getReflectiveObject(registry, get, "_muleNotificationListenerRegistry");

        // get the existing listeners dictionary for this api, or create a new dictionary if there isn't one yet
        Map<String, Object> notificationListeners = notificationListenerDict.computeIfAbsent(artifactId, k -> new HashMap<>());

        registerListenerInMulesoft(artifactId, MESSAGE_PROCESSOR_NOTIFICATION_LISTENER_KEY,
                AppDMessageProcessorNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(MESSAGE_PROCESSOR_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
        registerListenerInMulesoft(artifactId, PIPELINE_MESSAGE_NOTIFICATION_LISTENER_KEY,
                AppDPipelineMessageNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(PIPELINE_MESSAGE_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
        registerListenerInMulesoft(artifactId, EXCEPTION_NOTIFICATION_LISTENER_KEY,
                AppDExceptionNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(EXCEPTION_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
        registerListenerInMulesoft(artifactId, MANAGEMENT_NOTIFICATION_LISTENER_KEY,
                AppDManagementNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(MANAGEMENT_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
        registerListenerInMulesoft(artifactId, CONNECTION_NOTIFICATION_LISTENER_KEY,
                AppDConnectionNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(CONNECTION_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
        registerListenerInMulesoft(artifactId, TRANSACTION_NOTIFICATION_LISTENER_KEY,
                AppDTransactionNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(TRANSACTION_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
        registerListenerInMulesoft(artifactId, CONNECTOR_MESSAGE_NOTIFICATION_LISTENER_KEY,
                AppDConnectorMessageNotificationListener.class.getCanonicalName(),
                notificationListeners.getOrDefault(CONNECTOR_MESSAGE_NOTIFICATION_LISTENER_KEY, null),
                notificationRegistry);
//        registerListenerInMulesoft(artifactId, MuleContextNotificationListenerKey,
//                AppDMuleContextNotificationListener.class.getCanonicalName(),
//                notificationListeners.getOrDefault(MuleContextNotificationListenerKey, null),
//                notificationRegistry);
    }

    private void registerListenerInMulesoft(String artifactId, String notificationKey, String className, Object notificationListener, Object notificationRegistry) {
        try {
            ClassLoader classLoader = notificationRegistry.getClass().getClassLoader();
            if (getProperty(notificationKey + "-enabled", "false").equalsIgnoreCase("true") && notificationListener == null) {
                // create a new instance of our listener
                notificationListener = classLoader.loadClass(className).getConstructor(String.class).newInstance(artifactId);

                // add this new listener to the listeners dictionary for this api
                notificationListenerDict.get(artifactId).put(notificationKey, notificationListener);

                registerListener.execute(
                        classLoader,
                        notificationRegistry,
                        new Object[]{notificationListener}
                );
                getLogger().info(String.format("Registered Notification Listener: %s for api: %s", className, artifactId));
            }
        } catch (Exception e) {
            getLogger().warn(String.format("Error executing registration method: %s", e), e);
        }
    }

    /*
    TODO Fix this because we can't seem to call it correctly:
    MuleServiceDeploymentInterceptor - Error executing isListenerRegistered method: com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException: java.lang.IllegalArgumentException
com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException: java.lang.IllegalArgumentException

     */
    private boolean isListenerAlreadyLoaded(Object notificationRegistry, Object listener) {
        boolean loaded = false;
        if (listener == null) return false;
        try {
            Class<?> notificationListenerInstance = notificationRegistry.getClass().getClassLoader().loadClass(org.mule.runtime.api.notification.NotificationListener.class.getCanonicalName());
            Boolean result = (Boolean) isListenerRegistered.execute(notificationRegistry.getClass().getClassLoader(), notificationRegistry, new Object[]{notificationListenerInstance.cast(listener)});
            if (result != null) loaded = result;
        } catch (ReflectorException | ClassNotFoundException e) {
            getLogger().warn(String.format("Error executing isListenerRegistered method: %s", e), e);
        }
        return loaded;
    }
}
