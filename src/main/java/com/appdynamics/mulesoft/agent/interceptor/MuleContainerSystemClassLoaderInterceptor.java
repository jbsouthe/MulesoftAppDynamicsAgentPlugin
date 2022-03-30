package com.appdynamics.mulesoft.agent.interceptor;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MuleContainerSystemClassLoaderInterceptor extends MyBaseInterceptor{
    private static boolean initialized=false;
    private IReflector addURL, findClass;

    public MuleContainerSystemClassLoaderInterceptor() {
        super();

        addURL = makeInvokeInstanceMethodReflector("addURL", URL.class.getCanonicalName() );
        findClass = makeInvokeInstanceMethodReflector("findClass", String.class.getCanonicalName() );
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        return null;
    }

    @Override
    public void onMethodEnd(Object state, Object objectIntercepted, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( initialized ) return; //already injected our jar file
        Object clazz = null;
        try {
            clazz = findClass.execute( objectIntercepted.getClass().getClassLoader(), objectIntercepted, new Object[]{"com.appdynamics.mulesoft.agent.interceptor.MuleContainerSystemClassLoaderInterceptor"});
        } catch ( Exception ignore ) {} //ignore it because if the class isn't found we get a runtime exception, and we don't care
        if( clazz == null ) { //if we were able to load the class, then this jar file is already in the classpath, and this needs to not be executed
            File pluginDir = new File( this.getAgentPluginDirectory() );
            File [] files = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));
            for( File file : files ) {
                getLogger().debug("Attempting to load jar file into the mulesoft system class loader " + file.toString());
                try {
                    addURL.execute(objectIntercepted.getClass().getClassLoader(), objectIntercepted, new Object[] {file.toURI().toURL()} );
                    getLogger().info("Added jar file into the mulesoft system class loader " + file.toString());
                } catch (MalformedURLException e) {
                    getLogger().warn("Error in file name url conversion for "+ file.toURI() );
                } catch (ReflectorException e) {
                    getLogger().warn("Error attempting to add jar to class loader for file "+ file.toURI() +" exception: "+ e.toString());
                }
            }
        } else {
            getLogger().info("No need to load the plugin jar into the Mulesoft classloader, it is already in it somewhere");
        }
        initialized=true;
        return;
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        //this probably isn't polite, but some customers are having problems adding our jar file to their <mule_home>/lib/user directory on kubernetes
        rules.add(new Rule.Builder(
                "org.mule.runtime.module.reboot.internal.MuleContainerSystemClassLoader")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("<init>")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

        return rules;
    }
}
