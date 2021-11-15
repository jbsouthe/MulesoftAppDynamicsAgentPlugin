package com.appdynamics.mulesoft.agent;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.Transaction;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionDictionary {
    public String appdTransactionUUID = null;
    private Transaction appdTransaction = null;
    public String muleCorrelationId = null;
    private Long lastTouchTime = null;
    private Long counter = null;
    private ArrayList<FlowSegment> segments = null;
    public String lastFlowName;
    private String sourceType = null;
    private boolean finished = false;
    private boolean started = false;
    private ConcurrentHashMap<String, List<AbstractMap.SimpleImmutableEntry<String, String>>> bufferMap;

    public synchronized FlowSegment addSegment(String flowName, Transaction segment) {
        FlowSegment flowSegment = new FlowSegment();
        flowSegment.number = inc();
        flowSegment.flowName = flowName;
        flowSegment.transactionSegment = segment;
        flowSegment.startTimer();
        segments.add(flowSegment);
        this.lastFlowName = flowName;
        update();
        return flowSegment;
    }

    public FlowSegment getSegment(String flowname) {
        update();
        if (segments == null) return null;
        for (FlowSegment segment : this.segments) {
            if (segment.flowName.equals(flowname))
                return segment;
        }
        return null;
    }

    public boolean isExistingSegment(String flowname) {
        update();
        if (segments == null) return false;
        for (FlowSegment segment : this.segments) {
            if (segment.flowName.equals(flowname))
                return true;
        }
        return false;
    }

    public ArrayList<FlowSegment> getSegments() {
        return segments;
    }

    public class FlowSegment {
        public Long number = null;
        public String flowName = null;
        public Transaction transactionSegment = null;
        private Long timer = null;

        public void startTimer() {
            update();
            timer = now();
        }

        public Long getTimerDuration() {
            update();
            return now() - timer;
        }

        public String toString() {
            return "Flow." + number.toString() + "." + flowName + "." + transactionSegment.getUniqueIdentifier();
        }
    }

    public String toString() {
        return "Transaction." + muleCorrelationId + "." + appdTransactionUUID + ".flows_" + counter.toString();
    }

    private static Long now() {
        return new Date().getTime();
    }

    public TransactionDictionary(Transaction appTransaction, String muleCorrelationId) {
        this(appTransaction.getUniqueIdentifier(), muleCorrelationId);
        this.appdTransaction = appTransaction;
        this.started = true;
    }

    public TransactionDictionary(String appTransactionUUID, String muleCorrelationId) {
        this.appdTransactionUUID = appTransactionUUID;
        this.muleCorrelationId = muleCorrelationId;
        this.lastTouchTime = now();
        this.counter = -1L;
        this.segments = new ArrayList<>();
        this.started = true;
    }

    public TransactionDictionary(String muleCorrelationId) {
        this.muleCorrelationId = muleCorrelationId;
        this.lastTouchTime = now();
        this.counter = -1L;
        this.segments = new ArrayList<>();
    }

    public void update() {
        lastTouchTime = now();
    }

    public long inc() {
        this.counter++;
        this.update();
        return counter;
    }

    public Long getCounter() {
        this.update();
        return counter;
    }

    public boolean isStarted() { return this.started; }

    public void start() {
        this.started = true;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public void finish() {
        this.started = true;
        this.finished = true;
    }

    public Transaction getTransaction() {
        this.update();
        if ( appdTransaction != null ) return appdTransaction;
        return AppdynamicsAgent.getTransaction(this.appdTransactionUUID);
    }

    public void setTransaction(Transaction transaction) {
        this.update();
        this.appdTransaction = transaction;
    }

    public void setTransaction(String transactionUUID) {
        this.update();
        this.appdTransactionUUID = transactionUUID;
    }

    public String getSourceType() {
        this.update();
        return this.sourceType;
    }

    public void setSourceType(String sourceType) {
        this.update();
        this.sourceType = sourceType;
    }

    public Long getLastTouchTime() {
        return this.lastTouchTime;
    }

    private List<AbstractMap.SimpleImmutableEntry<String,String>> getListForFlow( String flowName ) {
        List<AbstractMap.SimpleImmutableEntry<String,String>> list = null;
        if( bufferMap == null ) bufferMap = new ConcurrentHashMap<>();
        list = bufferMap.get(flowName);
        if( list == null ) {
            list = new ArrayList<>();
            bufferMap.put(flowName,list);
        }
        return list;
    }

    public void addCustomDataToQueue( String flowName, String key, String value ) {
        this.update();
        AbstractMap.SimpleImmutableEntry<String, String> data = new AbstractMap.SimpleImmutableEntry<>( key, value);
        getListForFlow(flowName).add(data);
    }

    public List<AbstractMap.SimpleImmutableEntry<String,String>> drainQueueForFlow(String flowName) {
        this.update();
        if( bufferMap == null || !bufferMap.contains(flowName) ) return null;
        return bufferMap.remove(flowName);
    }
}
