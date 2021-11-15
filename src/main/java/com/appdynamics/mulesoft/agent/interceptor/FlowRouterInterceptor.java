package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.mulesoft.agent.interceptor.MyBaseInterceptor;

import java.util.ArrayList;
import java.util.List;

public class FlowRouterInterceptor extends MyBaseInterceptor {
    IReflector getLocation;

    public FlowRouterInterceptor() {
        super();

        getLocation = getNewReflectionBuilder()
                .invokeInstanceMethod("getContext", true)
                .invokeInstanceMethod("getOriginatingLocation", true)
                .invokeInstanceMethod("getLocation", true)
                .build();
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        Object coreEvent = params[0];
        String flowName = getReflectiveString( coreEvent, getLocation, "UNKNOWN-FLOW/");
        getLogger().debug("Flow Name: "+ flowName);
        Thread.currentThread().setName( flowName.split("/")[0] );
        return null;
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {

    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        /* disable for now, naming doesn't work with high volume
        rules.add(new Rule.Builder(
                "org.mule.module.apikit.api.spi.AbstractRouter")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("processEvent")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

         */
        return rules;
    }
}
