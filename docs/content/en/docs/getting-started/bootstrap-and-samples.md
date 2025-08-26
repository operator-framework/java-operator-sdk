---
title: Bootstrapping and samples
weight: 20
---

## Generating Project Skeleton

The project includes a Maven plugin to generate a skeleton project:

```shell
mvn io.javaoperatorsdk:bootstrapper:[version]:create -DprojectGroupId=org.acme -DprojectArtifactId=getting-started
```

You can build this project with Maven. The build will also generate the [CustomResourceDefinition](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions) for you.

## Getting Started with Samples

You can find examples in the [sample-operators](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators) directory. These samples demonstrate different components and scenarios with real-world examples:

* **webpage**: Simple example creating an NGINX webserver from a Custom Resource containing HTML code. Multiple implementation flavors are provided, using both low-level APIs and higher-level abstractions.
* **mysql-schema**: Operator managing schemas in a MySQL database. Shows how to manage non-Kubernetes resources.  
* **tomcat**: Operator with two controllers managing Tomcat instances and Webapps. Demonstrates how to manage multiple related custom resources and controllers.

## Running the Samples

The easiest way to try the samples is to run them on [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) or [kind](https://kind.sigs.k8s.io/).

1. Apply the generated CRD
2. Run your main class
3. The controller will automatically communicate with your local Kubernetes cluster and reconcile custom resources when you create them

See detailed instructions in [`samples/mysql-schema/README.md`](https://github.com/operator-framework/java-operator-sdk/blob/main/sample-operators/mysql-schema/README.md).



