# Leader Election E2E Test

The purpose of this module is to e2e test leader election feature and to demonstrate contract-first CRDs.

The deployment is using directly pods in order to better control some aspects in test. 
In real life this would be a Deployment.

The custom resource definition (CRD) is defined in YAML in the folder `src/main/resources/kubernetes`.
Upon build, the [java-generator-maven-plugin](https://github.com/fabric8io/kubernetes-client/blob/master/doc/java-generation-from-CRD.md)
generates the Java code under `target/generated-sources/java`.
