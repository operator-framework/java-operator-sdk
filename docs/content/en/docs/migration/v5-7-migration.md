---
title: Migrating from v5.6 to v5.7
description: Migrating from v5.6 to v5.7
---

## Fabric8 Kubernetes Client upgraded to v8.0.0

v5.7 uses version 8.0.0 of the
[Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client). Please consult the
client's own release notes and migration guide for the complete list of changes. The ones that are
most likely to affect operators built with JOSDK are described below.

### Jackson 3

The client moved from Jackson 2 to Jackson 3. The packages changed from `com.fasterxml.jackson` to
`tools.jackson` (the annotations from `jackson-annotations` are the exception and remain in
`com.fasterxml.jackson.annotation`). If your operator uses Jackson types directly, for example
`JsonNode` obtained from the client's `KubernetesSerialization`, update the imports and the
dependencies accordingly:

```java
// before
import com.fasterxml.jackson.databind.JsonNode;

// after
import tools.jackson.databind.JsonNode;
```

Other notable Jackson 3 differences:

- `JsonProcessingException` was replaced by `JacksonException`, which is unchecked.
- `JsonNode.fields()` was replaced by `properties()`, `fieldNames()` by `propertyNames()` and
  `asText()` by `asString()`.
- `ObjectMapper` instances are created using builders, for example `YAMLMapper.builder().build()`.

### Ambiguous `withShardSelector(null)`

`Filterable` now has an additional `withShardSelector(ShardSelector)` overload next to
`withShardSelector(String)`. Calls passing a bare `null` no longer compile, cast the argument:

```java
client.pods().withShardSelector((String) null);
```
