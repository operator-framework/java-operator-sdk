---
title: java-operator-sdk
description: Build Kubernetes Operators in Java without hassle
layout: homepage
---

Whether you want to build applications that operate themselves or provision infrastructure from Java code, Kubernetes
Operators are the way to go. This SDK will make it easy for Java developers to embrace this new way of automation.

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

#### Contributing

We are a friendly team of Java and Kubernetes enthusiasts and welcome everyone to contribute in any way to the framework!
Get in touch either on GitHub or our [Discord server](https://discord.gg/DacEhAy), we are always happy to chat and help
you find the right issue to get started. We have a [code of conduct](https://github.com/ContainerSolutions/java-operator-sdk/blob/master/CODE_OF_CONDUCT.md) 
which we strictly enforce, as well as [issues marked for new joiners](https://github.com/ContainerSolutions/java-operator-sdk/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)

We are also supporting [#HacktoberFest](https://hacktoberfest.digitalocean.com/) and have several issues marked as [good
candidates](https://github.com/ContainerSolutions/java-operator-sdk/issues?q=is%3Aissue+is%3Aopen+label%3A%22hacktoberfest%22+) to pick up during the event. 

[Maven](https://mvnrepository.com/artifact/com.github.containersolutions/java-operator-sdk){:.button-text}
[GitHub](https://github.com/ContainerSolutions/java-operator-sdk){:.button-text}