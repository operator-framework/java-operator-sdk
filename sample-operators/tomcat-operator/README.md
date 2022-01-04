# Tomcat Operator

Creates a Tomcat deployment from a Custom Resource, while keeping the WAR separated with another Custom Resource.

This sample demonstrates the following capabilities of the Java Operator SDK:
* Multiple Controllers in a single Operator. The Tomcat resource is managed by the TomcatController while the Webapp
resource is managed by the WebappController.
* Reacting to events about resources created by the controller. The TomcatController will receive events about the
Deployment resources it created. See EventSource section below for more detail. 

## Example input for creating a Tomcat instance
```
apiVersion: "tomcatoperator.io/v1"
kind: Tomcat
metadata:
  name: test-tomcat1
spec:
  version: 9.0
  replicas: 2
```

## Example input for the Webapp
```
apiVersion: "tomcatoperator.io/v1"
kind: Webapp
metadata:
  name: sample-webapp1
spec:
  tomcat: test-tomcat1
  url: http://tomcat.apache.org/tomcat-7.0-doc/appdev/sample/sample.war
  contextPath: mysample
```

## Getting started / Testing

The quickest way to try the operator is to run it on your local machine, while it connects to a
local or remote Kubernetes cluster. When you start it, it will use the current kubectl context on
your machine to connect to the cluster.

Before you run it you have to install the CRDs on your cluster by running:
- `kubectl apply -f target/classes/META-INF/fabric8/tomcats.tomcatoperator.io-v1.yml`
- `kubectl apply -f target/classes/META-INF/fabric8/webapps.tomcatoperator.io-v1.yml`

The CRDs are generated automatically from your code by simply adding the `crd-generator-apt`
dependency to your `pom.xml` file.

When the Operator is running you can create some Tomcat Custom Resources. You can find a sample
custom resources in the k8s folder.

If you want the Operator to be running as a deployment in your cluster, follow the below steps.

## Build

You can build the sample using `mvn install jib:dockerBuild` this will produce a Docker image you
can push to the registry of your choice. The JAR file is built using your local Maven and JDK and
then copied into the Docker image.

## Install Operator into cluster

Install the CRDs as shown above if you haven't already, then
run `kubectl apply -f k8s/operator.yaml`. Now you can create Tomcat instances with CRs (see examples
above).

## EventSources
The TomcatController is listening to events about Deployments created by the TomcatOperator by
registering a InformerEventSource with the EventSourceManager. The InformerEventSource will in turn
register a watch on all Deployments managed by the Controller (identified by
the `app.kubernetes.io/managed-by` label). When an event from a Deployment is received we have to
identify which Tomcat object does the Deployment belong to. This is done when the
InformerEventSource creates the event.

The TomcatController has to take care of setting the `app.kubernetes.io/managed-by` label on the
Deployment so the InformerEventSource can watch the right Deployments. The TomcatController also has
to set `ownerReference` on the Deployment so later the InformerEventSource can identify which Tomcat
does the Deployment belong to. This is necessary so the framework can call the Controller
`reconcile` method correctly.

