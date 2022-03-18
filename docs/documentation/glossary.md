---
title: Glossary 
description: Glossary 
layout: docs 
permalink: /docs/glossary
---

# Glossary

- Primary Resource - the resource that represents the desired state that the controller is working
  to achieve. While this is often a Custom Resource, this can also be a Kubernetes native resource.
- Secondary Resource - any resource that the controller needs to achieve the desired state
  represented by the primary resource. These resources can be created, updated, deleted or simply
  read depending on the use case. For example, the `Deployment` controller manages `ReplicatSet`
  instances when trying to realize the state represented by the `Deployment`. In this scenario,
  the `Deployment` is the primary resource while `ReplicaSet` is one of the secondary resources
  managed by the `Deployment` controller.
- Dependent Resource - a feature of JOSDK, aimed at making easier to manage secondary resources. A
  dependent resource is therefore a secondary resource implemented using this specific JOSDK
  feature. 