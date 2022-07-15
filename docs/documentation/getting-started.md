---
title: java-operator-sdk
description: Build Kubernetes Operators in Java without hassle
layout: docs
permalink: /docs/getting-started
---

# Java Operator SDK - Documentation

## Introduction & Resources on Operators

Operators manage both cluster and non-cluster resources on behalf of Kubernetes. This Java
Operator SDK (JOSDK) aims at making it as easy as possible to write Kubernetes operators in Java
using an API that should feel natural to Java developers and without having to worry about many
low-level details that the SDK handles automatically.

For an introduction on operators, please see this
[blog post](https://blog.container-solutions.com/kubernetes-operators-explained).

You can read about the common problems JOSDK is solving for you
[here](https://blog.container-solutions.com/a-deep-dive-into-the-java-operator-sdk).

You can also refer to the
[Writing Kubernetes operators using JOSDK blog series](https://developers.redhat.com/articles/2022/02/15/write-kubernetes-java-java-operator-sdk)
.

## Getting Started

The easiest way to get started with SDK is to start
[minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) and
execute one of our [examples](https://github.com/java-operator-sdk/samples/tree/main/mysql-schema).
There is a dedicated page to describe how to [use the samples](/docs/use-samples).

Here are the main steps to develop the code and deploy the operator to a Kubernetes cluster.
A more detailed and specific version can be found under `samples/mysql-schema/README.md`.

1. Setup `kubectl` to work with your Kubernetes cluster of choice.
1. Apply Custom Resource Definition
1. Compile the whole project (framework + samples) using `mvn install` in the root directory
1. Run the main class of the sample you picked and check out the sample's README to see what it
   does. When run locally the framework will use your Kubernetes client configuration (in `~/.
   kube/config`) to establish a connection to the cluster. This is why it was important to set
   up `kubectl` up front.
1. You can work in this local development mode to play with the code.
1. Build the Docker image and push it to the registry
1. Apply RBAC configuration
1. Apply deployment configuration
1. Verify if the operator is up and running. Don't run it locally anymore to avoid conflicts in
   processing events from the cluster's API server.



