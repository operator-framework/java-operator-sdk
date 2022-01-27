## Description

This sample shows how to use CRD with multiple versions.

## How-to

For getting the resource with target version use:

`k get MultiVersionCRDTestCustomResource.v1.sample.javaoperatorsdk mvcv1  -o yaml`

`k get MultiVersionCRDTestCustomResource.v2.sample.javaoperatorsdk mvcv1  -o yaml`