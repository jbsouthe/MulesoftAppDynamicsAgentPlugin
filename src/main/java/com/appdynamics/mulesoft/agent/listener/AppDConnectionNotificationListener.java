package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.ConnectionNotification;
import org.mule.runtime.api.notification.ConnectionNotificationListener;

public class AppDConnectionNotificationListener extends AppDynamicsNotificationHandler implements ConnectionNotificationListener<ConnectionNotification> {

    private String _artifactId;

    public AppDConnectionNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDConnectionNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    @Override
    public void onNotification(ConnectionNotification notification) {
        String actionIdentifier = notification.getAction().getIdentifier();
        String eventName = notification.getEventName();
        Object source = notification.getSource();
        switch (notification.getAction().getActionId()) {
            case ConnectionNotification.CONNECTION_CONNECTED:
                handleConnectionConnected( notification, eventName, actionIdentifier, _artifactId, source );
                break;
            case ConnectionNotification.CONNECTION_DISCONNECTED:
                handleConnectionDisconnected( notification, eventName, actionIdentifier, _artifactId, source );
                break;
        }
    }

}
