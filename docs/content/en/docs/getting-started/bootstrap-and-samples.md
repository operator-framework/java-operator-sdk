---
title: Bootstrapping and samples
weight: 20
---

## Generating Project Skeleton

Project includes a maven plugin to generate a skeleton project:

```shell
mvn io.javaoperatorsdk:bootstrapper:[version]:create -DprojectGroupId=org.acme -DprojectArtifactId=getting-started
```

You can build this project with maven,
the build will generate also the [CustomResourceDefinition](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions) 
for you.

## Getting started with samples

You can find examples under [sample-operators](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators)
directory which are intended to demonstrate the usage of different components in different scenarios, but mainly are more real world
examples:

* *webpage*: Simple example creating an NGINX webserver from a Custom Resource containing HTML code. We provide more 
  flavors of implementation, both with the low level APIs and higher level abstractions.
* *mysql-schema*: Operator managing schemas in a MySQL database. Shows how to manage non Kubernetes resources.
* *tomcat*: Operator with two controllers, managing Tomcat instances and Webapps running in Tomcat. The intention
  with this example to show how to manage multiple related custom resources and/or more controllers.

The easiest way to run / try out is to run one of the samples on
[minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) or [kind](https://kind.sigs.k8s.io/).
After applying the generated CRD, you can simply run your main class. The controller will automatically
start communicate with you local Kubernetes cluster and reconcile custom resource after you create one.

See also detailed instructions under [`samples/mysql-schema/README.md`](https://github.com/operator-framework/java-operator-sdk/blob/main/sample-operators/mysql-schema/README.md).



