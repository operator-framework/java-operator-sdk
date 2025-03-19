---
title: Bootstrapping and samples
weight: 30
---

## Generating Project Skeleton

Project includes a maven plugin to generate a skeleton project:

```shell
mvn io.javaoperatorsdk:bootstrapper:[version]:create -DprojectGroupId=org.acme -DprojectArtifactId=getting-started
```

## Getting started with samples

The easiest way to get started with SDK is to start
[minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) and
execute one of our [examples](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/sample-operators).
There is a dedicated page to describe how to [use the samples](/docs/using-samples).

Here are the main steps to develop the code and deploy the operator to a Kubernetes cluster.
A more detailed and specific version can be found under `samples/mysql-schema/README.md`.


- Setup `kubectl` to work with your Kubernetes cluster of choice. 
- Apply Custom Resource Definition
- Compile the whole project (framework + samples) using `mvn install` in the root directory
- Run the main class of the sample you picked and check out the sample's README to see what it
   does. When run locally the framework will use your Kubernetes client configuration (in `~/.
   kube/config`) to establish a connection to the cluster. This is why it was important to set
   up `kubectl` up front.
- You can work in this local development mode to play with the code.
- Build the Docker image and push it to the registry
- Apply RBAC configuration
- Apply deployment configuration
- Verify if the operator is up and running. Don't run it locally anymore to avoid conflicts in
   processing events from the cluster's API server.



