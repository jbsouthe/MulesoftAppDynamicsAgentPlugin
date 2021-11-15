package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.ManagementNotification;
import org.mule.runtime.api.notification.ManagementNotificationListener;

public class AppDManagementNotificationListener extends AppDynamicsNotificationHandler implements ManagementNotificationListener<ManagementNotification> {

    private String _artifactId;

    public AppDManagementNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDManagementNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    @Override
    public boolean isBlocking() {
        return ManagementNotificationListener.super.isBlocking();
    }

    @Override
    public void onNotification(ManagementNotification notification) {
        String actionIdentifier = notification.getAction().getIdentifier();
        String eventName = notification.getEventName();
        Object source = notification.getSource();
        switch (notification.getAction().getActionId()) {
            case ManagementNotification.MANAGEMENT_COMPONENT_QUEUE_EXHAUSTED:
                handleManagementComponentQueueExhausted( notification, eventName, actionIdentifier, _artifactId, source );
                break;
        }
    }

}
