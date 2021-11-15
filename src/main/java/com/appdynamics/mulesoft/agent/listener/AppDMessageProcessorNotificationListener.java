package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.MessageProcessorNotificationListener;

public class AppDMessageProcessorNotificationListener extends AppDynamicsNotificationHandler implements MessageProcessorNotificationListener<MessageProcessorNotification>  {
    private String _artifactId;

    public AppDMessageProcessorNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDMessageProcessorNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onNotification(MessageProcessorNotification messageProcessorNotification) {
        String transactionID = getTransactionId( messageProcessorNotification);
        String flowName = getFlowName( messageProcessorNotification );
        //Thread.currentThread().setName(flowName);
        String stepName = getStepName( messageProcessorNotification );
        String spanType = getSpanType( messageProcessorNotification );
        String spanId = getComponentLocation( messageProcessorNotification );
        switch (messageProcessorNotification.getAction().getActionId()) {
            case MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE:
                handleProcessorStartEvent(messageProcessorNotification, transactionID, _artifactId, flowName, stepName, spanType, spanId );
                break;

            case MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE:
                handleProcessorEndEvent(messageProcessorNotification, transactionID, _artifactId, flowName, stepName, spanType, spanId);
                break;
        }
    }

}
