---
title: java-operator-sdk
description: Build Kubernetes Operators in Java without hassle
layout: homepage
---

#### Features
* Framework for handling Kubernetes API events
* Registering Custom Resource watches
* Retry action on failure
* Smart event scheduling (only handle latest event for the same resource)



#### Why build your own Operator?
* Infrastructure automation using the power and flexibility of Java. See [blog post](https://blog.container-solutions.com/cloud-native-java-infrastructure-automation-with-kubernetes-operators).
* Provisioning of complex applications - avoiding Helm chart hell
* Integration with Cloud services - e.g. Secret stores
* Safer deployment of applications - only expose cluster to users by Custom Resources



#### Roadmap
* Testing of the framework and all samples while running on a real cluster.
* Generate a project skeleton
* Generate Java classes from CRD definion (and/or the other way around)
* Integrate with Quarkus (including native image build)
* Integrate with OLM (Operator Lifecycle Manager)

[Maven](https://mvnrepository.com/artifact/com.github.containersolutions/java-operator-sdk){:.button-text}
[GitHub](https://github.com/ContainerSolutions/java-operator-sdk){:.button-text}
