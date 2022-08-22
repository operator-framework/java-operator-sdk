# ![java-operator-sdk](docs/assets/images/logo.png)

![Java CI with Maven](https://github.com/java-operator-sdk/java-operator-sdk/workflows/Java%20CI%20with%20Maven/badge.svg)
[![Discord](https://img.shields.io/discord/723455000604573736.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.com/channels/723455000604573736)

# Build Kubernetes Operators in Java Without Hassle 

## Documentation

Documentation can be found on the  **[JOSDK WebSite](https://javaoperatorsdk.io/)**.

It's getting better every day! :)

## Contact us

Join us on [Discord](https://discord.gg/DacEhAy) or feel free to ask any question on 
[Kubernetes Slack Operator Channel](https://kubernetes.slack.com/archives/CAW0GV7A5)

**Meet us** every Thursday (17:00 CET) at our **community meeting** on [Zoom](https://zoom.us/j/8415370125)
(Password in the Discord channel, or just ask for it there!)

## How to Contribute

See the [contribution](https://javaoperatorsdk.io/docs/contributing) guide on the website.

## What is Java Operator SDK

Java Operator SDK is a higher level framework and related tooling to support writing Kubernetes Operators in Java.
It makes it easy to implement best practices and patters for an Operator. Features include: 

* Optimal handling Kubernetes API events
* Handling dependent resources, related events, caching.
* Automatic Retries
* Smart event scheduling
* Handling Observed Generations automatically
* Easy to use Error Handling
* ... and everything that a batteries included framework needs 

For all features and their usage see the [related section on the website](https://javaoperatorsdk.io/docs/features). 

## Related Projects

Operator SDK plugin: https://github.com/operator-framework/java-operator-plugins

Quarkus Extension: https://github.com/quarkiverse/quarkus-operator-sdk

Spring Boot Starter: https://github.com/java-operator-sdk/operator-framework-spring-boot-starter

## Projects using JOSDK

While we know of multiple projects using JOSDK in production, we don't want to presume these 
projects want to advertise that fact here. For this reason, we ask that if you'd like your project 
to be featured in this section, please open a PR, adding a link to and short description of your 
project, as shown below:

- [ExposedApp operator](https://github.com/halkyonio/exposedapp-rhdblog): a sample operator 
  written to illustrate JOSDK concepts and its Quarkus extension in the ["Write Kubernetes 
  Operators in Java with the Java Operator SDK" blog series](https://developers.redhat.com/articles/2022/02/15/write-kubernetes-java-java-operator-sdk#).
- [Keycloak operator](https://github.com/keycloak/keycloak/tree/main/operator): the official
  Keycloak operator, built with Quarkus and JOSDK.
