package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.ConnectorMessageNotification;
import org.mule.runtime.api.notification.ConnectorMessageNotificationListener;

public class AppDConnectorMessageNotificationListener extends AppDynamicsNotificationHandler implements ConnectorMessageNotificationListener<ConnectorMessageNotification> {

    private String _artifactId;

    public AppDConnectorMessageNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDConnectorMessageNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    @Override
    public void onNotification(ConnectorMessageNotification notification) {
        String actionIdentifier = notification.getAction().getIdentifier();
        String eventName = notification.getEventName();
        Object source = notification.getSource();
        String transactionID = getTransactionId( notification);
        String flowName = getFlowName( notification );
        String location = getComponentLocation( notification );
        //TODO JBS 060821: get step name here, somehow!
        switch (notification.getAction().getActionId()) {
            case ConnectorMessageNotification.MESSAGE_REQUEST_BEGIN:
                handleMessageRequestBegin( notification, eventName, actionIdentifier, _artifactId, source );
                break;
            case ConnectorMessageNotification.MESSAGE_REQUEST_END:
                handleMessageRequestEnd( notification, eventName, actionIdentifier, _artifactId, source );
                break;
            case ConnectorMessageNotification.MESSAGE_RECEIVED:
                handleMessageRequestReceived( notification, eventName, actionIdentifier, _artifactId, flowName, location, transactionID );
                break;
            case ConnectorMessageNotification.MESSAGE_RESPONSE:
                handleMessageRequestResponse( notification, eventName, actionIdentifier, _artifactId, flowName, location, transactionID );
                break;
            default:
                handleMessageRequest( notification, eventName, actionIdentifier, _artifactId, source );
                break;
        }
    }

}
