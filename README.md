## Features
- Ensures the AppDynamics agent doesn't lose track of a transaction during complex flow and api calls due to mule's reactor framework
- Collects the flow name, start time, and processors called for each transaction and uploads them to the data collectors
- Detects artifact deployments and submits events for installation and startup of the artifact, and even includes a call stack when an API fails to start.

[![published](https://static.production.devnetcloud.com/codeexchange/assets/images/devnet-published.svg)](https://developer.cisco.com/codeexchange/github/repo/jbsouthe/MulesoftAppDynamicsAgentPlugin)

## Required
- Agent version 21.4+
- Mulesoft version 4.3.0+
- Java 8


## Deployment steps
- Copy `AppD-MuleAgentTracer-2.1.jar` file under `<agent-install-dir>/ver.x.x.x.x/sdk-plugins`
- OPTIONALLY: add the following config options as well to the `wrapper.conf`
```properties
  wrapper.java.additional.92=-DAppDynamicsForceMulesoftStatisticsCollection=false
  wrapper.java.additional.93=-DAppDynamicsFlattenNameMulesoftStatisticsCollection=false
```
- Restart the mule JVM
- Edit `MuleMetricCollection.properties` file in `<agent-install-dir>/ver.x.x.x.x/sdk-plugins`
  to use the flow statistics white list and black list properties. Some organizations have thousands of flows
  and will not want to monitor all of them, this feature may or may not be needed for your deployment
 ```properties
  com.appdynamics.mulesoft.agent.metric.blackListRegex=regex test # to skip collection on match
  com.appdynamics.mulesoft.agent.metric.whiteListRegex=regex test # to collect on match else skip (applied after blacklist)
```

Flow data may come back in ways that are not intuitive to the developers creating flows and subflows. A review of this reference
may help before considering something a "bug" https://docs.mulesoft.com/mule-runtime/4.3/about-flows

If errors in the agent log file resembling these lines:
```
[appdynamics-analytics-autodata-writer1] 11 May 2021 13:20:34,039  WARN AnalyticsAutoDataTransform - Enqueuing transformed CollectionData failed since publish queue is full. This operation has failed [18000] time(s).
[appdynamics-analytics-autodata-writer1] 11 May 2021 13:20:34,040  WARN AnalyticsAutoDataTransform - Enqueuing transformed CollectionData failed since publish queue is full. This operation has failed [18100] time(s).
[appdynamics-analytics-autodata-writer1] 11 May 2021 13:20:54,198  WARN AnalyticsAutoDataTransform - Enqueuing transformed CollectionData failed since publish queue is full. This operation has failed [18200] time(s).
[appdynamics-analytics-autodata-writer1] 11 May 2021 13:25:15,542  WARN AnalyticsAutoDataTransform - Enqueuing transformed CollectionData failed since publish queue is full. This operation has failed [18300] time(s).
[appdynamics-analytics-autodata-writer1] 11 May 2021 13:26:54,198  WARN AnalyticsAutoDataTransform - Enqueuing transformed CollectionData failed since publish queue is full. This operation has failed [18400] time(s).
```
add this node property to mulesoft nodes: `max-analytics-collectors-allowed=5000` (2000 is default, but that isn't enough in large environments)
