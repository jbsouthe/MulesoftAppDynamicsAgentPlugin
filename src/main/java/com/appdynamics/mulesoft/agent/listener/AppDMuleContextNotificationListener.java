//package com.appdynamics.mulesoft.agent.listener;
//
//import org.mule.runtime.core.api.context.notification.MuleContextNotification;
//import org.mule.runtime.core.api.context.notification.MuleContextNotificationListener;
//
//public class AppDMuleContextNotificationListener extends AppDynamicsNotificationHandler implements MuleContextNotificationListener<MuleContextNotification> {
//    @Override
//    public void onNotification(MuleContextNotification notification) {
//        String actionIdentifier = notification.getAction().getIdentifier();
//        String eventName = notification.getEventName();
//        Object source = notification.getSource();
//        handleMuleContextNotification( notification, eventName, actionIdentifier, notification.getAction().toString());
//        /*
//        switch (notification.getAction().getActionId()) {
//            case MuleContextNotification.:
//                handleMessageRequestBegin( notification, eventName, actionIdentifier, source );
//                break;
//            case ConnectorMessageNotification.MESSAGE_REQUEST_END:
//                handleMessageRequestEnd( notification, eventName, actionIdentifier, source );
//                break;
//            case ConnectorMessageNotification.MESSAGE_RECEIVED:
//                handleMessageRequestReceived( notification, eventName, actionIdentifier, flowName, transactionID );
//                break;
//            case ConnectorMessageNotification.MESSAGE_RESPONSE:
//                handleMessageRequestResponse( notification, eventName, actionIdentifier, flowName, transactionID );
//                break;
//            default:
//                handleMessageRequest( notification, eventName, actionIdentifier, source );
//                break;
//        }
//
//         */
//    }
//
//}
