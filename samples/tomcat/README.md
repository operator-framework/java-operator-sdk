# Tomcat Operator

Creates a Tomcat deployment from a Custom Resource, while keeping the WAR separated with another Custom Resource.

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

The quickest way to try the operator is to run it on your local machine, while it connects to a local or remote Kubernetes cluster. When you start it it will use the current kubectl context on your machine to connect to the cluster.

Before you run it you have to install the CRD on your cluster by running `kubectl apply -f k8s/crd.yaml`.

When the Operator is running you can create some Tomcat Custom Resources. You can find a sample custom resources in the k8s folder.

If you want the Operator to be running as a deployment in your cluster, follow the below steps.

## Build
You can build the sample using `mvn install jib:dockerBuild` this will produce a Docker image you can push to the registry of your choice. The jar file is built using your local Maven and JDK and then copied into the Docker image.

## Install Operator into cluster

Run `kubectl apply -f k8s/crd.yaml` if you haven't already, then run `kubectl apply -f k8s/operator.yaml`. Now you can create Tomcat instances with CRs (see examples above).
