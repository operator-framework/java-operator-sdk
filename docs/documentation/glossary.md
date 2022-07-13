---
title: Glossary
description: Glossary
layout: docs
permalink: /docs/glossary
---

# Glossary

- **Primary Resource** - the resource that represents the desired state that the controller is
  working
  to achieve. While this is often a Custom Resource, it can be also be a Kubernetes native
  resource (Deployment,
  ConfigMap,...).
- **Secondary Resource** - any resource that the controller needs to manage the reach the desired state
  represented by the primary resource. These resources can be created, updated, deleted or simply
  read depending on the use case. For example, the `Deployment` controller manages `ReplicatSet`
  instances when trying to realize the state represented by the `Deployment`. In this scenario,
  the `Deployment` is the primary resource while `ReplicaSet` is one of the secondary resources
  managed by the `Deployment` controller.
- **Dependent Resource** - a feature of JOSDK, to make it easier to manage secondary resources. A
  dependent resource represents a secondary resource with related reconciliation logic.