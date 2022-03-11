---
title: Glossary
description: Glossary
layout: docs
permalink: /docs/glossary
---

# Glossary

- Primary Resource - usually the custom resource (or a well known Kubernetes Resource like Ingress) the controller manages.
- Secondary Resource - is the resource managed by the operator, thus resources mainly created or updated or just read
  during the reconciliation.
- Dependent Resource(s) - is a feature of JOSDK, to make it more easy to manage secondary resources.
  So speaking of dependent resources refers to a secondary resource, but managed using this feature. 