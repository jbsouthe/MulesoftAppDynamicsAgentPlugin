package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.TransactionNotification;
import org.mule.runtime.api.notification.TransactionNotificationListener;

public class AppDTransactionNotificationListener extends AppDynamicsNotificationHandler implements TransactionNotificationListener<TransactionNotification> {

    private String _artifactId;

    public AppDTransactionNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDTransactionNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    @Override
    public void onNotification(TransactionNotification notification) {
        String actionIdentifier = notification.getAction().getIdentifier();
        String eventName = notification.getEventName();
        Object source = notification.getSource();
        switch (notification.getAction().getActionId()) {
            case TransactionNotification.TRANSACTION_BEGAN:
                handleTransactionBegan( notification, eventName, actionIdentifier, _artifactId, source );
                break;
            case TransactionNotification.TRANSACTION_ROLLEDBACK:
                handleTransactionRolledBack( notification, eventName, actionIdentifier, _artifactId, source );
                break;
            case TransactionNotification.TRANSACTION_COMMITTED:
                handleTransactionCommitted( notification, eventName, actionIdentifier, _artifactId, source );
                break;
        }
    }

}
