package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.apm.appagent.api.DataScope;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.template.AGenericInterceptor;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflectionBuilder;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;
import com.appdynamics.mulesoft.agent.listener.ListenerProxy;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public abstract class MyBaseInterceptor extends AGenericInterceptor {
    protected static final String CORRELATION_HEADER_KEY = "singularityheader";
    protected boolean initialized = false;
    protected Set<DataScope> dataScopes = null;
    protected Set<DataScope> snapshotDatascopeOnly = null;
    protected Set<DataScope> analyticsDatascopeOnly = null;
    protected static final String DISABLE_ANALYTICS_COLLECTION_PROPERTY = "disablePluginAnalytics";
    protected static String PLUGIN_PROPERTIES_FILE_NAME = "CustomPlugin.properties";
    private Properties properties;

    public MyBaseInterceptor() {
        super();
        initialize();
    }

    public abstract Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params);

    public abstract void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal);

    public abstract List<Rule> initializeRules();

    protected boolean isInitialized() {
        return this.initialized;
    }

    protected void initialize() {
        dataScopes = new HashSet<>();
        dataScopes.add(DataScope.SNAPSHOTS);
        if (System.getProperty(DISABLE_ANALYTICS_COLLECTION_PROPERTY, "false").equalsIgnoreCase("false")) {
            dataScopes.add(DataScope.ANALYTICS);
            this.getLogger().info("Enabling Analytics Collection of Plugin Custom Data, to disable add JVM property -D" + DISABLE_ANALYTICS_COLLECTION_PROPERTY + "=true");
        }
        snapshotDatascopeOnly = new HashSet<>();
        snapshotDatascopeOnly.add(DataScope.SNAPSHOTS);
        analyticsDatascopeOnly = new HashSet<>();
        analyticsDatascopeOnly.add(DataScope.ANALYTICS);
        loadProperties();
        saveProperties();
        this.initialized = true;
    }

    protected void loadProperties() {
        if (this.properties == null) {
            this.properties = new Properties();
            String defaultProperty = "true";
            if (System.getProperty(DISABLE_ANALYTICS_COLLECTION_PROPERTY, "false").equalsIgnoreCase("true")) {
                defaultProperty = "false";
            }
            for (Rule rule : this.getRules()) {
                this.properties.setProperty(rule.getClassMatchString() + "-enableAnalyticsData", defaultProperty);
            }
            for (String customPropertyKey : getListOfCustomProperties().keySet()) {
                this.properties.setProperty(customPropertyKey, getListOfCustomProperties().get(customPropertyKey));
            }
            File configFile = new File(this.getAgentPluginDirectory() + System.getProperty("file.separator", "/") + PLUGIN_PROPERTIES_FILE_NAME);

            if (configFile.canRead()) {
                try (InputStream is = new FileInputStream(configFile)) {
                    this.properties.load(is);
                } catch (Exception e) {
                }
            }
        }
    }

    protected Map<String, String> getListOfCustomProperties() {
        return new HashMap<>();
    }

    protected boolean isAnalyticsEnabledForClass(String className) {
        return this.properties.getProperty(className + "-enableAnalyticsData", "true").equalsIgnoreCase("true");
    }

    protected String getProperty(String propertyName, String... defaultPropertyValue) {
        String value = null;
        if (defaultPropertyValue.length > 0) value = defaultPropertyValue[0];
        return this.properties.getProperty(propertyName, value);
    }

    protected void setProperty(String propertyName, String value) {
        this.properties.setProperty(propertyName, value);
    }


    protected void saveProperties() {
        File configFile = new File(this.getAgentPluginDirectory() + System.getProperty("file.separator", "/") + PLUGIN_PROPERTIES_FILE_NAME);
        try {
            if (!configFile.canRead()) configFile.createNewFile();
            try (OutputStream out = new FileOutputStream(configFile)) {
                properties.store(out, "Writing current properties to file for next load");
            }
        } catch (Exception e) {
            this.getLogger().info("Error saving properties file, exception: " + e.getMessage(), e);
        }
    }

    protected String getUrlWithoutParameters(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    null, // Ignore the query part of the input url
                    uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            return url; //just give back the original input on error
        }
    }

    protected Transaction setTransactionName(Transaction transaction, String newName) {
        IReflector setCurrentTransactionName = getNewReflectionBuilder().invokeInstanceMethod("setCurrentTransactionName", true, new String[]{"com.appdynamics.agent.api.Transaction", "java.lang.String"}).build();
        try {
            Field transactionDelegateField = AppdynamicsAgent.class.getDeclaredField("transactionDelegate");
            transactionDelegateField.setAccessible(true);
            Object transactionDelegate = transactionDelegateField.get(null);
            Transaction returned = (Transaction) getReflectiveObject(transactionDelegate, setCurrentTransactionName, transaction, newName);
            if (returned != null) return returned;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().warn("Error doing something we probably shouldn't in setTransactionName, exception: " + e, e);
        }
        return transaction;
    }

    protected IReflector makeAccessFieldValueReflector(String field) {
        return getNewReflectionBuilder().accessFieldValue(field, true).build();
    }

    protected IReflector makeInvokeInstanceMethodReflector(String method, String... args) {
        if (args.length > 0) return getNewReflectionBuilder().invokeInstanceMethod(method, true, args).build();
        return getNewReflectionBuilder().invokeInstanceMethod(method, true).build();
    }

    protected IReflector makeInvokeInstanceMethodChainReflector(String... methods) {
        IReflectionBuilder builder = getNewReflectionBuilder();
        for (String method : methods)
            builder = builder.invokeInstanceMethod(method, true);
        return builder.build();
    }

    protected boolean isFakeTransaction(Transaction transaction) {
        if( transaction == null ) return true;
        return "".equals(transaction.getUniqueIdentifier());
    }

    protected boolean isFakeExitCall(ExitCall exitCall) {
        return "".equals(exitCall.getCorrelationHeader());
    }

    protected String getReflectiveString(Object object, IReflector method, String defaultString) {
        String value = defaultString;
        if (object == null || method == null) return defaultString;
        try {
            value = (String) method.execute(object.getClass().getClassLoader(), object);
            if (value == null) return defaultString;
        } catch (ReflectorException e) {
            this.getLogger().info("Error in reflection call, exception: " + e.getMessage(), e);
        }
        return value;
    }

    protected Integer getReflectiveInteger(Object object, IReflector method, Integer defaultInteger) {
        Integer value = defaultInteger;
        if (object == null || method == null) return defaultInteger;
        try {
            value = (Integer) method.execute(object.getClass().getClassLoader(), object);
            if (value == null) return defaultInteger;
        } catch (ReflectorException e) {
            this.getLogger().info("Error in reflection call, exception: " + e.getMessage(), e);
        }
        return value;
    }

    protected Long getReflectiveLong(Object object, IReflector method) {
        if (object == null || method == null) return null;
        Object rawValue = getReflectiveObject(object, method);
        if (rawValue instanceof Long) return (Long) rawValue;
        if (rawValue instanceof Integer) return ((Integer) rawValue).longValue();
        if (rawValue instanceof Double) return ((Double) rawValue).longValue();
        if (rawValue instanceof Number) return ((Number) rawValue).longValue();
        return null;
    }

    protected Object getReflectiveObject(Object object, IReflector method, Object... args) {
        Object value = null;
        if (object == null || method == null) return value;
        try {
            if (args.length > 0) {
                value = method.execute(object.getClass().getClassLoader(), object, args);
            } else {
                value = method.execute(object.getClass().getClassLoader(), object);
            }
        } catch (ReflectorException e) {
            this.getLogger().info("Error in reflection call, method: " + method.getClass().getCanonicalName() + " object: " + object.getClass().getCanonicalName() + " exception: " + e.getMessage(), e);
        }
        return value;
    }

    protected void collectData(Transaction transaction, String name, String value) {
        collectData(transaction, null, name, value);
    }

    protected void collectData(Transaction transaction, String className, String name, String value) {
        if (transaction == null) return;
        if (!isInitialized()) {
            initialize();
        }
        if (isAnalyticsEnabledForClass(className)) {
            transaction.collectData(name, value, this.dataScopes);
        } else {
            transaction.collectData(name, value, this.snapshotDatascopeOnly);
        }
    }

    protected void collectSnapshotData(Transaction transaction, String name, String value) {
        if (transaction == null) return;
        if (!isInitialized()) {
            initialize();
        }
        transaction.collectData(name, value, this.snapshotDatascopeOnly);
    }

    /*
                { "operation": "publishEvent", "eventSummary": "Summary Text of Event", "severity": "INFO, WARN, ERROR" "eventType": "String Type of Event", "details": [ { "key": "the name of the detail", "value": "the value of the detail" } ] }
                eventType - the type of the event. Values allowed: [ERROR, APPLICATION_ERROR, APPLICATION_INFO, STALL, BT_SLA_VIOLATION, DEADLOCK, MEMORY_LEAK, MEMORY_LEAK_DIAGNOSTICS, LOW_HEAP_MEMORY, ALERT, CUSTOM, APP_SERVER_RESTART, BT_SLOW,
                                                                    SYSTEM_LOG, INFO_INSTRUMENTATION_VISIBILITY, AGENT_EVENT, INFO_BT_SNAPSHOT, AGENT_STATUS, SERIES_SLOW, SERIES_ERROR, ACTIVITY_TRACE, OBJECT_CONTENT_SUMMARY, DIAGNOSTIC_SESSION,
                                                                    HIGH_END_TO_END_LATENCY, APPLICATION_CONFIG_CHANGE, APPLICATION_DEPLOYMENT, AGENT_DIAGNOSTICS, MEMORY, LICENSE, CONTROLLER_AGENT_VERSION_INCOMPATIBILITY, CONTROLLER_EVENT_UPLOAD_LIMIT_REACHED,
                                                                    CONTROLLER_RSD_UPLOAD_LIMIT_REACHED, CONTROLLER_METRIC_REG_LIMIT_REACHED, CONTROLLER_ERROR_ADD_REG_LIMIT_REACHED, CONTROLLER_ASYNC_ADD_REG_LIMIT_REACHED, AGENT_METRIC_REG_LIMIT_REACHED,
                                                                    AGENT_ADD_BLACKLIST_REG_LIMIT_REACHED, AGENT_ASYNC_ADD_REG_LIMIT_REACHED, AGENT_ERROR_ADD_REG_LIMIT_REACHED, AGENT_METRIC_BLACKLIST_REG_LIMIT_REACHED, DISK_SPACE, INTERNAL_UI_EVENT,
                                                                    APPDYNAMICS_DATA, APPDYNAMICS_INTERNAL_DIAGNOSTICS, APPDYNAMICS_CONFIGURATION_WARNINGS, AZURE_AUTO_SCALING, POLICY_OPEN, POLICY_OPEN_WARNING, POLICY_OPEN_CRITICAL, POLICY_CLOSE,
                                                                    POLICY_UPGRADED, POLICY_DOWNGRADED, RESOURCE_POOL_LIMIT, THREAD_DUMP_ACTION_STARTED, EUM_CLOUD_BROWSER_EVENT, THREAD_DUMP_ACTION_END, THREAD_DUMP_ACTION_FAILED, RUN_LOCAL_SCRIPT_ACTION_STARTED,
                                                                    RUN_LOCAL_SCRIPT_ACTION_END, RUN_LOCAL_SCRIPT_ACTION_FAILED, RUNBOOK_DIAGNOSTIC_SESSION_STARTED, RUNBOOK_DIAGNOSTIC_SESSION_END, RUNBOOK_DIAGNOSTIC_SESSION_FAILED, CUSTOM_ACTION_STARTED,
                                                                    CUSTOM_ACTION_END, CUSTOM_ACTION_FAILED, WORKFLOW_ACTION_STARTED, WORKFLOW_ACTION_END, WORKFLOW_ACTION_FAILED, NORMAL, SLOW, VERY_SLOW, BUSINESS_ERROR, ALREADY_ADJUDICATED,
                                                                    ADJUDICATION_CANCELLED, EMAIL_SENT, SMS_SENT]
     */
    protected void publishEvent(String eventSummary, String severity, String eventType, Map<String, String> details) {
        this.getLogger().debug("Begin publishEvent event summary: " + eventSummary + " severity: " + severity + " event type: " + eventType);
        AppdynamicsAgent.getEventPublisher().publishEvent(eventSummary, severity, eventType, details);
        this.getLogger().debug("Finish publishEvent event summary: " + eventSummary + " severity: " + severity + " event type: " + eventType);
    }

    /*
    { "operation": "reportMetric", "name": "Name|of|Metric|Pipe|Delimited", "value": longValue(NOT_A_STRING!),
            "aggregationType": "Values allowed: [AVERAGE, ADVANCED_AVERAGE, SUM, OBSERVATION, OBSERVATION_FOREVERINCREASING]",
            "timeRollupType": "Values allowed: [AVERAGE, SUM, CURRENT]",
            "clusterRollupType": "Values allowed: [INDIVIDUAL, COLLECTIVE]" }
     */
    protected void reportMetric(String metricName, long metricValue, String aggregationType, String timeRollupType, String clusterRollupType) {
        this.getLogger().debug("Begin reportMetric name: " + metricName + " = " + metricValue + " aggregation type: " + aggregationType + " time rollup type: " + timeRollupType + " cluster rollup type: " + clusterRollupType);
        AppdynamicsAgent.getMetricPublisher().reportMetric(metricName, metricValue, aggregationType, timeRollupType, clusterRollupType);
        this.getLogger().debug("Finish reportMetric name: " + metricName + " = " + metricValue + " aggregation type: " + aggregationType + " time rollup type: " + timeRollupType + " cluster rollup type: " + clusterRollupType);
    }

    private Object getProxyInstance(Object object) throws ClassNotFoundException {
        return getProxyInstance(this.getClass().getClassLoader(), object);
    }

    private Object getProxyInstance(ClassLoader classLoader, Object object) throws ClassNotFoundException {
        ArrayList<Class> externalInterfaces = new ArrayList<>();
        for (Class externalInterface : object.getClass().getInterfaces()) {
            Class loadedInterface = classLoader.loadClass(externalInterface.getCanonicalName());
            externalInterfaces.add(loadedInterface);
            getLogger().info("external interface: " + externalInterface.getCanonicalName());
        }
        externalInterfaces.add(classLoader.loadClass("org.mule.runtime.api.notification.NotificationListener"));
        Class[] externalInterfaceArray = (Class[]) externalInterfaces.toArray(new Class[0]);

        getLogger().info("creating proxy for interface: " + externalInterfaceArray[0].getCanonicalName() + " for object: " + object.getClass().getCanonicalName() + " using classloader: " + classLoader.toString());
        return externalInterfaceArray[0].cast(
                Proxy.newProxyInstance(
                        classLoader,
                        externalInterfaceArray,
                        new ListenerProxy(object))
        );

    }
}