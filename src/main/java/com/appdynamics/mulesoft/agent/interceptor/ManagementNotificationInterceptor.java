package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class ManagementNotificationInterceptor extends MyBaseInterceptor {
    // getters on the mule artifact that has been deployed
    IReflector getArtifactName;
    IReflector getArtifactId;
    IReflector getMuleVersion;
    IReflector getArtifactDescriptorName;
    IReflector getStatus;
    IReflector get;

    public ManagementNotificationInterceptor() {
        super();
        getArtifactName = makeInvokeInstanceMethodReflector("getArtifactName"); //artifact.getArtifactName())
        getArtifactId = makeInvokeInstanceMethodReflector("getArtifactId"); //artifact.getArtifactId())
        getMuleVersion = makeInvokeInstanceMethodChainReflector("getDescriptor", "getMinMuleVersion", "toCompleteNumericVersion"); //artifact.getDescriptor().getMinMuleVersion().toCompleteNumericVersion())
        getArtifactDescriptorName = makeInvokeInstanceMethodChainReflector("getDescriptor", "getName"); //artifact.getDescriptor().getName())
        getStatus = makeInvokeInstanceMethodReflector("getStatus"); //enum
        get = makeInvokeInstanceMethodReflector("get", String.class.getCanonicalName());
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        getLogger().debug("ManagementNotificationInterceptor.onMethodBegin() for " + className + "." + methodName + "()");
        Object notification = params[0];
        String eventName = (String) params[1];
        String actionIdentifier = (String) params[2];
        String artifactId = (String) params[3];
        Object source = params[4];
        getLogger().debug(String.format("%s: %s action identifier: %s notification: %s artifactId: %s source: %s",
                methodName, eventName, actionIdentifier, notification, artifactId, source));
        Map<String, String> details = getDetails(objectIntercepted);
        details.put("Event", eventName);
        details.put("Action", actionIdentifier);
        details.put("Notification", String.valueOf(notification) );
        details.put("ArtifactID", artifactId);

        switch (methodName) {
            case "handleManagementComponentQueueExhausted": {
                publishEvent("Component Queue Exhausted", "WARN", "RESOURCE_POOL_LIMIT", details);
                break;
            }
            default: {
                publishEvent(eventName, "ERROR", "ERROR", details);
            }
        }
        return null;
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {

    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<>();

        rules.add(new Rule.Builder("com.appdynamics.mulesoft.agent.listener.AppDynamicsNotificationHandler")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS).methodMatchString("handleManagement")
                .methodStringMatchType(SDKStringMatchType.STARTSWITH).build());
        return rules;
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
}
