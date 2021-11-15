package com.appdynamics.mulesoft.agent;

import javax.inject.Singleton;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class Scheduler extends Thread {
    private static final String THREAD_NAME = "AppDynamics Mule Session Cleaner Thread";
    ConcurrentHashMap<String, TransactionDictionary> map = null;
    long sleepTime = 30000;
    long ageToDiscard = 120000;
    private static Scheduler instance = null;

    public synchronized static Scheduler getInstance(long sleepTimeMS, long ageToDiscardMS, ConcurrentHashMap<String, TransactionDictionary> concurrentHashMap ) {
        if( instance == null )
            instance = new Scheduler();
        if( sleepTimeMS > 30000 ) instance.sleepTime = sleepTimeMS; //safety check, we aren't going faster than this
        instance.ageToDiscard = ageToDiscardMS;
        instance.map = concurrentHashMap;
        return instance;
    }

    private Scheduler() {
        setDaemon(true);
        try {
            setPriority( (int)getPriority()/2 );
        } catch (Exception e) {
            //we tried, no op
        }
        setName(THREAD_NAME);
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        while(true) {
            long now = new Date().getTime();
            long numTransactions = map.size();
            long numRemoved = 0;
            for (TransactionDictionary transactionDictionary : map.values()) {
                if( transactionDictionary.isFinished() || now > (transactionDictionary.getLastTouchTime() + ageToDiscard) ) {
                    numRemoved++;
                    map.remove( transactionDictionary.muleCorrelationId);
                }
            }
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                //no op
            }
        }
    }
}