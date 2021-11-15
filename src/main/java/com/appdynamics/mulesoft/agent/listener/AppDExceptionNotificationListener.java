package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.ExceptionNotification;
import org.mule.runtime.api.notification.ExceptionNotificationListener;

public class AppDExceptionNotificationListener extends AppDynamicsNotificationHandler implements ExceptionNotificationListener {

    private String _artifactId;

    public AppDExceptionNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDExceptionNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    //@Override
    public void onNotification(ExceptionNotification notification) {
        String transactionID = getTransactionId( notification);
        String flowName = getFlowName( notification );
        if( flowName == null ) {
            try {
                flowName = notification.getComponent().getLocation().getLocation().split("/")[0];
            } catch (NullPointerException npe) { //well, we tried
            }
        }
        /*
        if( flowName != null ){
            Thread.currentThread().setName(flowName);
        }
         */
        handleExceptionEvent(notification, transactionID, _artifactId, flowName);
    }
}
