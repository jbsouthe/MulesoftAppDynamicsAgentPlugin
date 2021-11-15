package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.mulesoft.agent.interceptor.MyBaseInterceptor;

import java.util.ArrayList;
import java.util.List;

public class FlowRoutingStrategyInterceptor extends MyBaseInterceptor {
    IReflector getFlowNameString;

    public FlowRoutingStrategyInterceptor() {
        super();

        getFlowNameString = getNewReflectionBuilder()
                .invokeInstanceMethod("getLocation", true)
                .invokeInstanceMethod("getLocation", true)
                .build();
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        Object flow = params[0];
        String locationName = getReflectiveString(flow, getFlowNameString, "UNKNOWN-FLOW/");
        String flowName = locationName.split("/")[0];
        getLogger().debug("FlowRoutingStrategyInterceptor location: "+ locationName +" flow: "+ flowName);

        Thread.currentThread().setName(flowName);
        return null;
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {

    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        /* disable for now, flow naming doesn't work in high volume environment
        rules.add(new Rule.Builder(
                "org.mule.module.apikit.routing.FlowRoutingStrategy")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("route")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

         */
        return rules;
    }
}
