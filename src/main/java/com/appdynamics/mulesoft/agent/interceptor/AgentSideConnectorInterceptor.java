package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.mulesoft.agent.interceptor.MyBaseInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
Agent side integration class for https://github.com/jbsouthe/AppDynamics-Mulesoft4-Extension
which is being managed separatly because the pom.xml is so much different than the existing project build, we may at some point bring these together
 */
public class AgentSideConnectorInterceptor extends MyBaseInterceptor {
    private static final String METRIC_NAME_PREFIX="Custom Metrics|Mulesoft|";

    public AgentSideConnectorInterceptor() {
        super();
    }

    @Override
    public Object onMethodBegin(Object appDynamicsNotificationHandler, String className, String methodName, Object[] params) {
        getLogger().debug("AgentSideConnectorInterceptor.onMethodBegin() for " + className + "." + methodName + "()");

        switch(methodName) {
            case "interceptedPublishEvent": {
                if( params.length < 4 ) {
                    getLogger().warn("AgentSideConnectorInterceptor "+ className+"."+methodName +" Expecting 4 parameters to call but got: "+ params.length);
                    return null;
                }

                // get and cast the params
                String eventSummary = (String) params[0];
                String severity = (String) params[1];
                String eventType = (String) params[2];
                Map<String, String> details = (Map<String, String>) params[3];

                // publish the event
                AppdynamicsAgent.getEventPublisher().publishEvent(eventSummary, severity, eventType, details);

                break;
            }
            case "interceptedReportAverageMetric": {
                if( params.length < 2 ) {
                    getLogger().warn("AgentSideConnectorInterceptor "+ className+"."+methodName +" Expecting 2 parameters to call but got: "+ params.length);
                    return null;
                }

                // get and cast the params
                String metricName = (String) params[0];
                long metricValue = (long) params[1];

                if( ! metricName.startsWith(METRIC_NAME_PREFIX) ) metricName = METRIC_NAME_PREFIX + metricName;

                // publish the metric
                AppdynamicsAgent.getMetricPublisher().reportAverageMetric(metricName, metricValue);

                break;
            }
            case "interceptedReportMetricWithAddedValues": {
                if( params.length < 8 ) {
                    getLogger().warn("AgentSideConnectorInterceptor "+ className+"."+methodName +" Expecting 8 parameters to call but got: "+ params.length);
                    return null;
                }

                // get and cast the params
                String metricName = (String) params[0];
                long metricValue = (Long) params[1];
                long count = (Long) params[2];
                long minValue = (Long) params[3];
                long maxValue = (Long) params[4];
                String aggregatorType = (String) params[5];
                String timeRollup = (String) params[6];
                String clusterRollup = (String) params[7];

                if( ! metricName.startsWith(METRIC_NAME_PREFIX) ) metricName = METRIC_NAME_PREFIX + metricName;

                // publish the metric
                AppdynamicsAgent.getMetricPublisher().reportMetric( metricName, metricValue, count, minValue, maxValue,
                        aggregatorType, timeRollup, clusterRollup);

                break;
            }
            case "interceptedReportMetric": {
                if( params.length < 5 ) {
                    getLogger().warn("AgentSideConnectorInterceptor "+ className+"."+methodName +" Expecting 5 parameters to call but got: "+ params.length);
                    return null;
                }

                // get and cast the params
                String metricName = (String) params[0];
                long metricValue = (Long) params[1];
                String aggregationType = (String) params[2];
                String timeRollup = (String) params[3];
                String clusterRollup = (String) params[4];

                if( ! metricName.startsWith(METRIC_NAME_PREFIX) ) metricName = METRIC_NAME_PREFIX + metricName;

                // publish the metric
                AppdynamicsAgent.getMetricPublisher().reportMetric(metricName, metricValue, aggregationType, timeRollup, clusterRollup);

                break;
            }
            case "interceptedReportObservedMetric": {
                if( params.length < 2 ) {
                    getLogger().warn("AgentSideConnectorInterceptor "+ className+"."+methodName +" Expecting 2 parameters to call but got: "+ params.length);
                    return null;
                }

                // get and cast the params
                String metricName = (String) params[0];
                long metricValue = (Long) params[1];

                if( ! metricName.startsWith(METRIC_NAME_PREFIX) ) metricName = METRIC_NAME_PREFIX + metricName;

                // publish the metric
                AppdynamicsAgent.getMetricPublisher().reportObservedMetric(metricName, metricValue);

                break;
            }
            case "interceptedReportSumMetric": {
                if( params.length < 2 ) {
                    getLogger().warn("AgentSideConnectorInterceptor "+ className+"."+methodName +" Expecting 2 parameters to call but got: "+ params.length);
                    return null;
                }

                // get and cast the params
                String metricName = (String) params[0];
                long metricValue = (Long) params[1];

                if( ! metricName.startsWith(METRIC_NAME_PREFIX) ) metricName = METRIC_NAME_PREFIX + metricName;

                // publish the metric
                AppdynamicsAgent.getMetricPublisher().reportSumMetric(metricName, metricValue);

                break;
            }
            default: {
                getLogger().info("A method was intercepted that this plugin does not support. Please ensure the versions are compatible. Intercepted Method: " + className + "." + methodName + "()");
                break;
            }
        }
        return null;

    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        //no op
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<>();

        rules.add(new Rule.Builder(
                "org.mule.extension.appdmuleextension.internal.AppDAgentInterceptedMethods")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("intercepted").methodStringMatchType(SDKStringMatchType.STARTSWITH)
                .build()
        );
        return rules;
    }
}
