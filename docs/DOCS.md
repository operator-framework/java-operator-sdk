# Java Operator SDK - Documentation

## Introduction & Resources on Operators

Operators are easy and simple way to manage resource on Kubernetes clusters but
also outside of the cluster. The goal of this SDK is to allow writing operators in Java by
providing a nice API and handling common issues regarding the operators on framework level.
   
For an introduction, what is an operator see this [blog post](https://blog.container-solutions.com/kubernetes-operators-explained).

You can read about the common problems what is this operator framework is solving for you [here](https://blog.container-solutions.com/a-deep-dive-into-the-java-operator-sdk).

## Getting Started

The easiest way to get started with SDK is start [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) and 
execute one of our [examples](https://github.com/ContainerSolutions/java-operator-sdk/tree/master/samples/webserver)

Note that you don't have to deploy the operator to a Kubernetes clusters as a pod (however you have to apply the CRD).
If you run this example from you favorite IDE, it will pick up the kubeconfig (prepared by minikube) and will start
processing events of custom resources immediately. 

<!-- todo add Adam's blogpost here -->

## Controllers

Controllers are where you implement you logic for you operator.

## Automatic Retries

## Event Processing Details
### Handling Finalizers
### Managing Consistency
### Generation Awareness
### Event Scheduling
### Event Dispatching 

## Running The Operator

## Spring Boot Support
