# Kotlin Operator Sample

This module is a minimalist end-to-end test verifying that JOSDK works properly when both the
custom resource and the reconciler are implemented in Kotlin rather than Java.

The `ConfigMapCopyReconciler` reads `spec.message` from a `ConfigMapCopy` custom resource and
copies it into a `ConfigMap`. This exercises:

- deserialization of the custom resource from the Kubernetes API via the fabric8 client,
- a Kotlin-implemented `Reconciler`, and
- the rest of the reconciliation runtime (event sources, status updates, owner references).

See [`KotlinCheckedExceptionDependentResourceTest`](../../operator-framework-core/src/test/kotlin/io/javaoperatorsdk/operator/processing/dependent/workflow/KotlinCheckedExceptionDependentResourceTest.kt)
for a narrower unit-level test covering checked-exception handling specifically (see
[#2967](https://github.com/operator-framework/java-operator-sdk/issues/2967)).
