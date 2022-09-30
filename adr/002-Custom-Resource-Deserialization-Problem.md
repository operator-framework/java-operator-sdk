# Multi Version Custom Resources Deserialization Problem

## Status

accepted

## Context

In case there are multiple versions of a custom resource in can happen that a controller/informer tracking
such a resource might run into deserialization problem as shown
in [this integration test](https://github.com/java-operator-sdk/java-operator-sdk/blob/07aab1a9914d865364d7236e496ef9ba5b50699e/operator-framework/src/test/java/io/javaoperatorsdk/operator/MultiVersionCRDIT.java#L55-L55)
. In the mentioned case two versions of a custom resource are not compatible with each other. The informer receives
both, but naturally not able to deserialize one of them.

How should the framework or the underlying informer behave?

Alternatives:

1. The informer should skip the resource and should continue to process the resources with the correct version.
2. Informer stops and makes a notification callback.

## Decision

From the JOSDK perspective is fine if the informer stops, and the users decides if the whole operator should stop
(usually the preferred way). The reason, that this is an obvious issue on platform level (not on operator/controller
level). Thus, the controller should not receive such custom resources in the first place, so the problem should be
addressed on platform level. Possibly introducing conversion hooks, or labeling for the target resource.

## Consequences

If an Informer stops on such deserialization error, even explicitly restarting it won't solve the problem, since
would fail again on the same error.

## Notes

The informer implementation if fabric8 client changed in this regard, before it was not stopping on deserialization
error, but as describe this change in behavior is completely acceptable.