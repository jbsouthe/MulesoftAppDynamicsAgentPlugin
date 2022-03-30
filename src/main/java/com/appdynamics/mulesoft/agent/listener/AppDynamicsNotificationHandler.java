package com.appdynamics.mulesoft.agent.listener;

import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ConfigurationProperties;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.*;
import org.mule.runtime.core.api.context.notification.FlowStackElement;
import org.mule.runtime.core.api.context.notification.MuleContextNotification;
import org.mule.runtime.core.api.event.CoreEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.lang.reflect.Field;

/*
Many thanks go out to https://github.com/michaelhyatt/elastic-apm-mule4-agent
Tip of the hat, this was not intuitive....
 */
public class AppDynamicsNotificationHandler {
    protected ConfigurationProperties configurationProperties;
    protected static final String DOC_NAME = "name";
    protected static final String DOC_NAMESPACE = "http://www.mulesoft.org/schema/mule/documentation";
    protected static final String UNNAMED = "...";
    protected static final String UNTYPED = "zzz_type";

    protected static Logger logger = LoggerFactory.getLogger(AppDynamicsNotificationHandler.class);


    public void handleProcessorStartEvent(MessageProcessorNotification messageProcessorNotification,  String transactionID, String artifactId, String flowName, String stepName, String spanType, String spanId ) {
        logger.debug("handle Flow Start Step Event Step Name: {}.{} transaction id: {}", flowName, stepName, transactionID);
    }

    public void handleProcessorEndEvent(MessageProcessorNotification messageProcessorNotification,  String transactionID, String artifactId, String flowName, String stepName, String spanType, String spanId ) {
        logger.debug("handle Flow End Step Event Step Name: {}.{} transaction id: {}", flowName, stepName, transactionID);
    }

    public void handleExceptionEvent(ExceptionNotification exceptionNotification,  String transactionID, String artifactId, String flowName) {
        logger.debug("handle Flow Exception/End Event Flow Name: {} transaction id: {}", flowName, transactionID);
    }

    public void handleFlowStartEvent(PipelineMessageNotification pipelineMessageNotification, String transactionID, String artifactId, String flowName ) {
        logger.debug("handle Flow Start Event Flow Name: {} transaction id: {}", flowName, transactionID);
    }

    public void handleFlowEndEvent(PipelineMessageNotification pipelineMessageNotification, String transactionID, String artifactId, String flowName) {
        logger.debug("handle Flow End Event Flow Name: {} transaction id: {}", flowName, transactionID);
    }

    protected void handleManagementComponentQueueExhausted(ManagementNotification notification, String eventName, String actionIdentifier,  String artifactId, Object source) {
        logger.debug("handle Management Component Queue Exhausted Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleManagementDefaultEvent(ManagementNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Management Default Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleConnectionDisconnected(ConnectionNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Connection Disconnected Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleConnectionConnected(ConnectionNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Connection Connected Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleTransactionCommitted(TransactionNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Transaction Committed Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleTransactionRolledBack(TransactionNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Transaction Rolled Back Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleTransactionBegan(TransactionNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Transaction Began Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source);
    }

    protected void handleMessageRequestBegin(ConnectorMessageNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Message Request Begin Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source.toString());
    }

    protected void handleMessageRequestEnd(ConnectorMessageNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Message Request End Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source.toString());
    }

    protected void handleMessageRequest(ConnectorMessageNotification notification, String eventName, String actionIdentifier, String artifactId, Object source) {
        logger.debug("handle Message Request Other Event, Name: {} action identifier: {} source: {}", eventName, actionIdentifier, source.toString());
    }

    protected void handleMessageRequestReceived(ConnectorMessageNotification notification, String eventName, String actionIdentifier, String artifactId, String flowName, String location, String transactionId) {
        logger.debug("handle Message Request Received Event, Name: {} action identifier: {} flow: {} location: {}", eventName, actionIdentifier, flowName, location);
    }

    protected void handleMessageRequestResponse(ConnectorMessageNotification notification, String eventName, String actionIdentifier, String artifactId, String flowName, String location, String transactionId) {
        logger.debug("handle Message Request Response Event, Name: {} action identifier: {} flow: {} location: {}", eventName, actionIdentifier, flowName, location);
    }

    protected void handleMuleContextNotification(MuleContextNotification notification, String eventName, String actionIdentifier, String action) {
        logger.debug("handle Mule Context Notification Event, Name: {} action identifier: {} action: {}", eventName, actionIdentifier, action);
    }

    public String getConfigurationProperty( String propertyName ) {
        if( propertyName == null ) return "UNKNOWN-PROPERTY";
        if( configurationProperties == null ) return "NO-CONFIG";
        return configurationProperties.resolveStringProperty(propertyName).orElse("UNKNOWN-"+propertyName);
    }

    public static String getTransactionId( EnrichedServerNotification notification ) {
        Event event = notification.getEvent();
        if( event != null ) return event.getCorrelationId();
        // If the event == null, this must be the case of an exception and the original
        // event is attached under processedEvent in the exception.
        Exception e = notification.getInfo().getException();
        if( e == null ) return null;

        CoreEvent coreEvent = getEventFromException( e );
        if (coreEvent != null)
            return coreEvent.getCorrelationId();
        return null;
    }

    private static CoreEvent getEventFromException( Exception exception ) {
        // This is a really ugly hack to get around the fact that
        // org.mule.runtime.core.internal.exception.MessagingException class is not
        // visible in the current classloader and there is no documentation to explain
        // how to access objects of this class and why the hell it is internal and is
        // not part of the API.
        // TODO: raise why org.mule.runtime.core.internal.exception.MessagingException
        // is not part of the API with Mule support.
        Field f = null;
        CoreEvent iWantThis = null;
        try {
            f = exception.getClass().getDeclaredField("processedEvent");
        } catch (NoSuchFieldException | SecurityException ignored) {

        } // NoSuchFieldException
        if ( f == null ) return null;
        f.setAccessible(true);
        try {
            iWantThis = (CoreEvent) f.get(exception);
        } catch (IllegalArgumentException | IllegalAccessException e1) {
            return null;
        } // IllegalAccessException

        return iWantThis;
    }

    public static String getFlowName(EnrichedServerNotification notification) {
        String flowName = notification.getResourceIdentifier();
        if (flowName == null )
            try {
                flowName = notification.getInfo().getComponent().getLocation().getLocation();
            } catch ( NullPointerException ignored) {  } //we tried
        Exception e = notification.getInfo().getException();
        if( e != null && flowName == null ) {
            CoreEvent coreEvent = getEventFromException( e );
            if (coreEvent != null)
                for( FlowStackElement flow : coreEvent.getFlowCallStack().getElements())
                    flowName = flow.getFlowName(); //get last flow name, and assume that is this flow
                try {
                    if( flowName == null ) flowName = coreEvent.getContext().getOriginatingLocation().getLocation();
                } catch( NullPointerException ignored) {} //ignored, give up already
        }
        if( flowName == null ) return "UNKNOWN-FLOW";
        return flowName.split("/")[0];
    }


    public static String getStepName(EnrichedServerNotification notification) {
        try {
            Component component = notification.getComponent();
            if (component == null)
                return UNNAMED;

            Object nameParam = component.getAnnotation(new QName(DOC_NAMESPACE, DOC_NAME));

            if (nameParam == null)
                return UNNAMED;

            return nameParam.toString();
        }catch (Exception ex ) {
            logger.info("Error getting Step Name: "+ ex,ex);
        }
        return UNNAMED;
    }

    public static String getSpanType(EnrichedServerNotification notification) {
        try {
            Component component = notification.getComponent();
            if (component == null) return UNTYPED;
            String nameParam = component.getLocation().getComponentIdentifier().getIdentifier().getName();
            if (nameParam == null)
                return UNTYPED;

            return nameParam;
        } catch (Exception ex ) {
            logger.info("Error getting Span Type: "+ ex,ex);
        }
        return UNTYPED;
    }

    public static String getComponentLocation(EnrichedServerNotification notification) {
        return notification.getInfo().getComponent().getLocation().getLocation();
    }
}
