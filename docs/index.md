---
title: java-operator-sdk
description: Build Kubernetes Operators in Java without hassle
layout: homepage
---

Whether you want to build applications that operate themselves or provision infrastructure from Java code, Kubernetes
Operators are the way to go. This SDK will make it easy for Java developers to embrace this new way of automation.
The java-operator-sdk is based on the [fabric8 Kubernetes client](https://github.com/fabric8io/kubernetes-client).

# Latest News

### 18.1.2021 - Version 1.7.0 released!

This version marks another important step in bringing the Kubernetes Operator paradigm to the land of Java and the JDK. 
1.7.0 brings big improvements in 3 areas:

***Better Custom Resource classes*** - Thanks to upgrading to the latest and greatest version of the fabric8 client we 
get much reduced boilerplate code. Metadata annotations move from the Controller to Custom Resource class leading to 
more intuitive configuration.

***Generalized event system*** - a Controller can be invoked because of events happening not only to the Custom Resource 
it manages, but also to dependent resources. These dependent resources might be Kubernetes objects or anything else 
(e.g. an RDS database created by the Operator). You can implement your own EventSource for any events needed and it 
will all be handled by the system seamlessly while avoiding concurrency issues.

***Full integration with the Quarkus framework*** - For those who like building cloud native Java apps with Quarkus, 
we now provide full support in the form of a Quarkus extension. This means effortless Docker image building, fast 
startup time and native executable compilation our of the box.

# Features
* Framework for handling Kubernetes API events
* Mapping Custom Resources to Java classes
* Retry action on failure
* Smart event scheduling (only handle latest event for the same resource)
* Avoid concurrency issues - related events are serialized, unrelated executed in parallel
* Smooth integration with Quarkus and Spring Boot
* Handling of events from non-Kubernetes resources

# Why build your own Operator?
* Infrastructure automation using the power and flexibility of Java. See [blog post](https://blog.container-solutions.com/cloud-native-java-infrastructure-automation-with-kubernetes-operators).
* Provisioning of complex applications - avoiding Helm chart hell
* Integration with Cloud services - e.g. Secret stores
* Safer deployment of applications - only expose cluster to users by Custom Resources

# Roadmap
* Comprehensive documentation
* Integrate with operator-sdk to generate project skeleton 
* Testing of the framework and all samples while running on a real cluster.
* Generate Java classes from CRD definion (and/or the other way around)


# Contributing
We are a friendly team of Java and Kubernetes enthusiasts and welcome everyone to contribute in any way to the framework!
Get in touch either on GitHub or our [Discord server](https://discord.gg/DacEhAy), we are always happy to chat and help
you find the right issue to get started. Feel free to stop by for questions, comments or just saying hi.
We have a [code of conduct](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/CODE_OF_CONDUCT.md)
which we strictly enforce, as well as [issues marked for new joiners](https://github.com/java-operator-sdk/java-operator-sdk/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).

We are also supporting [#HacktoberFest](https://hacktoberfest.digitalocean.com/) and have several issues marked as [good
candidates](https://github.com/java-operator-sdk/java-operator-sdk/issues?q=is%3Aissue+is%3Aopen+label%3A%22hacktoberfest%22+) to pick up during the event. 

[Maven](https://mvnrepository.com/artifact/io.javaoperatorsdk/java-operator-sdk){:.button-text}
[GitHub](https://github.com/java-operator-sdk/java-operator-sdk){:.button-text}
[Discord](https://discord.gg/DacEhAy){:.button-text}
