---
title: java-operator-sdk
description: Build Kubernetes Operators in Java without hassle
layout: docs
permalink: /docs/getting-started
---

# Java Operator SDK - Documentation

## Introduction & Resources on Operators

Operators are easy and simple way to manage resource on Kubernetes clusters but
also outside the cluster. The goal of this SDK is to allow writing operators in Java by
providing a nice API and handling common issues regarding the operators on framework level.
   
For an introduction, what is an operator see this [blog post](https://blog.container-solutions.com/kubernetes-operators-explained).

You can read about the common problems what is this operator framework is solving for you [here](https://blog.container-solutions.com/a-deep-dive-into-the-java-operator-sdk).

## Getting Started

The easiest way to get started with SDK is start [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) and 
execute one of our [examples](https://github.com/java-operator-sdk/samples/tree/main/mysql-schema)

Here are the main steps to develop the code and deploy the operator to a Kubernetes cluster. A more detailed and specific
version can be found under `samples/mysql-schema/README.md`.

1. Setup kubectl to work with your Kubernetes cluster of choice.
1. Apply Custom Resource Definition
1. Compile the whole project (framework + samples) using `mvn install` in the root directory
1. Run the main class of the sample you picked and check out the sample's README to see what it does.
When run locally the framework will use your Kubernetes client configuration (in ~/.kube/config) to make the connection
to the cluster. This is why it was important to set up kubectl up front.
1. You can work in this local development mode to play with the code.
1. Build the Docker image and push it to the registry
1. Apply RBAC configuration
1. Apply deployment configuration
1. Verify if the operator is up and running. Don't run it locally anymore to avoid conflicts in processing events from 
the cluster's API server.



