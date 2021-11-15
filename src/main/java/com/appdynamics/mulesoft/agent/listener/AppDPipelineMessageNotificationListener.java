package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.api.notification.PipelineMessageNotificationListener;

public class AppDPipelineMessageNotificationListener extends AppDynamicsNotificationHandler implements PipelineMessageNotificationListener<PipelineMessageNotification> {

    private String _artifactId;

    public AppDPipelineMessageNotificationListener() {
        _artifactId = "UNKNOWN_ARTIFACT";
    }

    public AppDPipelineMessageNotificationListener(String artifactId) {
        _artifactId = artifactId;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onNotification(PipelineMessageNotification notification) {
        String transactionID = getTransactionId( notification);
        String flowName = getFlowName( notification );
        // Event listener
        // TODO: refactor to remove the deprecation warning.
        switch (notification.getAction().getActionId()) {
            case PipelineMessageNotification.PROCESS_START:
                handleFlowStartEvent(notification, transactionID, _artifactId, flowName);
                break;

            // On exception this event doesn't fire, only on successful flow completion.
            case PipelineMessageNotification.PROCESS_END:
                break;

            case PipelineMessageNotification.PROCESS_COMPLETE:
                handleFlowEndEvent(notification, transactionID, _artifactId, flowName);
                break;
        }
    }
}
