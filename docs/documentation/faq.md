---
title: FAQ
description: Frequently asked questions
layout: docs
permalink: /docs/faq
---

### Q: How can I access the events which triggered the Reconciliation?
In the v1.* version events were exposed to `Reconciler` (in v1 called `ResourceController`). This 
included events (Create, Update) of the custom resource, but also events produced by Event Sources. After
long discussions also with developers of golang version (controller-runtime), we decided to remove access to
these events. We already advocated to not use events in the reconciliation logic, since events can be lost. 
Instead reconcile all the resources on every execution of reconciliation. On first this might sound a little 
opinionated, but there was a sound agreement between the developers that this is the way to go. 