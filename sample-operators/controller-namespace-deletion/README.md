This sample demonstrates the workaround for problem when a namespace
is being deleted with a running controller, that watches resources
in its own namespace. If the pod or other underlying resources (role,
role binding, service account) are deleted before the cleanup of
the custom resource the namespace deletion is stuck.

see also: https://github.com/operator-framework/java-operator-sdk/pull/2528

