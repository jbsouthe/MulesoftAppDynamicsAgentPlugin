package com.appdynamics.mulesoft.agent.metric;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.MetricPublisher;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.mulesoft.agent.interceptor.MyBaseInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MetricInterceptor extends MyBaseInterceptor {
    private static final String THREAD_NAME = "AppDynamics Mulesoft Metric Collector";
    private static final String METRIC_BASE_NAME = "Custom Metrics|Mulesoft|";
    private static final String DEFAULT_AGGREGATOR_TYPE = "AVERAGE";
    private static final String FORCE_STATISTICS_COLLECTION_PROPERTY = "AppDynamicsForceMulesoftStatisticsCollection";
    private static final String FLATTEN_METRICNAME_COLLECTION_PROPERTY = "AppDynamicsFlattenNameMulesoftStatisticsCollection";
    private static final String FLOWNAME_WHITELIST_PROPERTY = "com.appdynamics.mulesoft.agent.metric.whiteListRegex";
    private static final String FLOWNAME_BLACKLIST_PROPERTY = "com.appdynamics.mulesoft.agent.metric.blackListRegex";


    private Scheduler scheduler = null;
    private ConcurrentHashMap<String, Collector> metricMap = null;
    private boolean enableForceStatisticsCollection = false;
    private boolean enableFlattenMetricNames = false;
    private Pattern whiteListPattern, blackListPattern;
    private boolean enableWhiteList = false, enableBlackList = false;

    IReflector isEnabled, setEnabled; // org.mule.runtime.core.api.management.stats.Statistics
    IReflector typeAttribute, getCaughtMessages, getNotRouted, getTotalReceived, getTotalRouted, getRouted; //org.mule.runtime.core.api.management.stats.RouterStatistics
    IReflector getName, getAverageProcessingTime, getProcessedEvents, getMaxProcessingTime, getMinProcessingTime,
            getTotalProcessingTime, getExecutionErrors, getFatalErrors, getTotalEventsReceived; //org.mule.runtime.core.api.management.stats.FlowConstructStatistics

    static { PLUGIN_PROPERTIES_FILE_NAME = "MuleMetricCollection.properties"; }

    public MetricInterceptor() {
        super();

        if( System.getProperty(FORCE_STATISTICS_COLLECTION_PROPERTY,"false").equalsIgnoreCase("true") ) {
            this.enableForceStatisticsCollection=true;
            this.getLogger().info("Enabling Forced Mulesoft Statistics Collections, to disable add JVM property -D"+ FORCE_STATISTICS_COLLECTION_PROPERTY +"=false");
        }
        if( System.getProperty(FLATTEN_METRICNAME_COLLECTION_PROPERTY,"false").equalsIgnoreCase("true") ) {
            this.enableFlattenMetricNames=true;
            this.getLogger().info("Enabling Custom Metric Name Flattening for Mulesoft Statistics Collections, to disable add JVM property -D"+ FLATTEN_METRICNAME_COLLECTION_PROPERTY +"=false");
        }

        metricMap = new ConcurrentHashMap<>();
        scheduler = new Scheduler( 30000l , metricMap );
        scheduler.start();

        // org.mule.runtime.core.api.management.stats.Statistics
        isEnabled = makeInvokeInstanceMethodReflector( "isEnabled" ); //boolean
        setEnabled = makeInvokeInstanceMethodReflector( "setEnabled", boolean.class.getCanonicalName() ); //not in interface, but seen in implementations

        //org.mule.runtime.core.api.management.stats.RouterStatistics
        typeAttribute = makeAccessFieldValueReflector("type"); //int
        getCaughtMessages = makeInvokeInstanceMethodReflector( "getCaughtMessages" ); // long
        getNotRouted = makeInvokeInstanceMethodReflector( "getNotRouted" ); // long
        getTotalReceived = makeInvokeInstanceMethodReflector( "getTotalReceived" ); //long
        getTotalRouted = makeInvokeInstanceMethodReflector( "getTotalRouted" ); //long
        getRouted = makeInvokeInstanceMethodReflector( "getRouted" ); //Map<String,Long>

        //org.mule.runtime.core.api.management.stats.FlowConstructStatistics
        getName = makeInvokeInstanceMethodReflector("getName"); //String
        getAverageProcessingTime = makeInvokeInstanceMethodReflector( "getAverageProcessingTime" ); //long
        getProcessedEvents = makeInvokeInstanceMethodReflector( "getProcessedEvents" ); //long
        getMaxProcessingTime = makeInvokeInstanceMethodReflector( "getMaxProcessingTime" ); //long
        getMinProcessingTime = makeInvokeInstanceMethodReflector( "getMinProcessingTime" ); //long
        getTotalProcessingTime = makeInvokeInstanceMethodReflector( "getTotalProcessingTime" ); //long
        getExecutionErrors = makeInvokeInstanceMethodReflector( "getExecutionErrors" ); //long
        getFatalErrors = makeInvokeInstanceMethodReflector( "getFatalErrors" ); //long
        getTotalEventsReceived = makeInvokeInstanceMethodReflector( "getTotalEventsReceived" ); //long

        String whiteList = getProperty(FLOWNAME_WHITELIST_PROPERTY);
        if( whiteList == null || "".equals(whiteList) ) {
            setProperty(FLOWNAME_WHITELIST_PROPERTY,"");
        } else {
            try {
                whiteListPattern = Pattern.compile(whiteList);
                enableWhiteList = true;
                getLogger().info("Enabling White List Regex on Flow Name: "+ whiteList);
            }catch (Exception ex) {
                getLogger().warn("Error in whitelist pattern compilation \""+ whiteList +"\" continuing without whitelist! Exception: "+ ex);
            }
        }
        String blackList = getProperty(FLOWNAME_BLACKLIST_PROPERTY);
        if( blackList == null || "".equals(blackList) ) {
            setProperty(FLOWNAME_BLACKLIST_PROPERTY,"");
        } else {
            try {
                blackListPattern = Pattern.compile(blackList);
                enableBlackList = true;
                getLogger().info("Enabling Black List Regex on Flow Name: "+ blackList);
            }catch (Exception ex) {
                getLogger().warn("Error in blacklist pattern compilation \""+ blackList +"\" continuing without blacklist! Exception: "+ ex);
            }
        }
        saveProperties();
    }

    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add( new Rule.Builder(
                "org.mule.runtime.core.api.management.stats.AllStatistics")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("add")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        rules.add( new Rule.Builder(
                "org.mule.runtime.core.api.management.stats.AllStatistics")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("remove")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        rules.add( new Rule.Builder(
                "org.mule.runtime.core.api.management.stats.RouterStatistics")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("<init>")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

        return rules;
    }

    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        return null;
    }

    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("MetricInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
        if( className.contains("AllStatistics") ) {
            Object flowConstructStatistics = params[0];
            String flowName = getReflectiveString(flowConstructStatistics, getName, "UNKNOWN-FLOW");
            if (methodName.equals("remove")) {
                removeMetric(flowName);
                return;
            }
            if (methodName.equals("add") && checkWhiteBlackList(flowName) ) {
                addMetricToScheduler(flowName, flowConstructStatistics, "FlowConstructStatistics");
            }
        }
        if( className.contains("RouterStatistics") ) {
            addMetricToScheduler("RouterStatistics", object, "RouterStatistics");
        }
        this.getLogger().debug("MetricInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }

    private boolean checkWhiteBlackList(String flowName) {
        if( enableBlackList && blackListPattern.matcher(flowName).find() ) return false;
        if( enableWhiteList ) return whiteListPattern.matcher(flowName).find();
        return true;
    }


    private synchronized void addMetricToScheduler(String name, Object metric, String type) {
        metricMap.put(name, new Collector( name, metric, type));
    }

    private synchronized void removeMetric( String name ) {
        getLogger().debug("MetricInterceptor.removeMetric() Cancelling collector for "+ name);
        metricMap.remove(name);
    }

    public class Collector {
        String name, type;
        Object metric;
        MetricPublisher publisher;

        public Collector( String name, Object metric, String type) {
            this.name = name;
            if( enableFlattenMetricNames )
                this.name = name.replace(":", "-");
            this.metric = metric;
            this.type = type;
            this.publisher = AppdynamicsAgent.getMetricPublisher();
            getLogger().debug("MetricInterceptor.Collector<init> Scheduling collector for "+ name +" of type: "+ type);
        }

        public void run() {
            Boolean enabled = (Boolean) getReflectiveObject(metric, isEnabled);
            getLogger().debug("MetricInterceptor.Collector.run() collector for "+ name +" of type: "+ type +" enabled? "+ enabled);

            if( enabled == null || !enabled.booleanValue() ) { //don't collect if disabled
                //we will return as no op in the future, but for now let's enable everything for development
                if( enableForceStatisticsCollection ) {
                    getLogger().debug("MetricInterceptor.Collector.run() collector for " + name + " of type: " + type + " Enabling Manually!");
                    getReflectiveObject(metric, setEnabled, new Boolean(true));
                }
            }
            Long val = null;
            switch(type) {
                case "FlowConstructStatistics": {
                    String metricBaseName = METRIC_BASE_NAME + "Flows|" + name;
                    val = getReflectiveLong( metric, getAverageProcessingTime);
                    this.publisher.reportMetric( metricBaseName +"|Average Processing Time(ms)", val, "AVERAGE", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getProcessedEvents);
                    this.publisher.reportMetric( metricBaseName +"|Processed Events", val, "SUM", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getMaxProcessingTime);
                    this.publisher.reportMetric( metricBaseName +"|Max Processing Time(ms)", val, "AVERAGE", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getMinProcessingTime);
                    this.publisher.reportMetric( metricBaseName +"|Min Processing Time(ms)", val, "AVERAGE", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getTotalProcessingTime);
                    this.publisher.reportMetric( metricBaseName +"|Total Processing Time(ms)", val, "AVERAGE", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getExecutionErrors);
                    this.publisher.reportMetric( metricBaseName +"|Execution Errors", val, "SUM", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getFatalErrors);
                    this.publisher.reportMetric( metricBaseName +"|Fatal Errros", val, "SUM", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getTotalEventsReceived);
                    this.publisher.reportMetric( metricBaseName +"|Total Events Received", val, "SUM", "CURRENT", "COLLECTIVE");
                    break;
                }
                case "RouterStatistics": {
                    String metricBaseName = METRIC_BASE_NAME;
                    int type = getReflectiveInteger(metric, typeAttribute, 0);
                    switch (type) {
                        case 0: metricBaseName += "Unknown Router"; break;
                        case 1: metricBaseName += "Inbound Router"; break;
                        case 2: metricBaseName += "Outbound Router"; break;
                        case 3: metricBaseName += "Response Router"; break;
                        case 4: metricBaseName += "Binding Router"; break;
                    }
                    val = getReflectiveLong( metric, getCaughtMessages);
                    this.publisher.reportMetric( metricBaseName +"|Caught Messages", val, "SUM", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getNotRouted);
                    this.publisher.reportMetric( metricBaseName +"|Not Routed Messages", val, "SUM", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getTotalReceived);
                    this.publisher.reportMetric( metricBaseName +"|Total Received Messages", val, "SUM", "CURRENT", "COLLECTIVE");
                    val = getReflectiveLong( metric, getTotalRouted);
                    this.publisher.reportMetric( metricBaseName +"|Total Routed Messages", val, "SUM", "CURRENT", "COLLECTIVE");
                    Map<String,Long> endpointMap = (Map<String, Long>) getReflectiveObject(metric,getRouted);
                    if( endpointMap != null ) {
                        for( String endpointName : endpointMap.keySet() ) {
                            this.publisher.reportMetric( metricBaseName +"|Routed To "+ endpointName, endpointMap.get(endpointName), "SUM", "CURRENT", "COLLECTIVE");
                        }
                    }
                    break;
                }
                default: return;
            }
        }
    }

    class Scheduler extends Thread {
        ConcurrentHashMap<String, Collector> map = null;
        long sleepTime = 15000;
        public Scheduler( long sleepTimeInput, ConcurrentHashMap<String, Collector> collectorConcurrentHashMap ) {
            map = collectorConcurrentHashMap;
            if( sleepTimeInput > sleepTime ) sleepTime = sleepTimeInput; //safety check, we aren't going faster than this
            setDaemon(true);
            try {
                setPriority( (int)getPriority()/2 );
            } catch (Exception e) {
                //we tried, no op
            }
            setName(THREAD_NAME);
        }

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            while(true) {
                for (Collector collector : map.values()) {
                    collector.run();
                }
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    //no op
                }
            }
        }
    }
}
