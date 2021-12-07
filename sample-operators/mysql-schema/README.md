# MySQL Schema Operator

This example shows how an operator can control resources outside of the Kubernetes cluster. In this case it will be
managing MySQL schemas in an existing database server. This is a common scenario in many organizations where developers
need to create schemas for different applications and environments, but the database server itself is managed by a 
different team. Using this operator a dev team can create a CR in their namespace and have a schema provisioned automatically.
Access to the MySQL server is configured in the configuration of the operator, so admin access is restricted. 

This is an example input:
```yaml
apiVersion: "mysql.sample.javaoperatorsdk/v1"
kind: MySQLSchema
metadata:
  name: mydb
spec:
  encoding: utf8
```

Creating this custom resource will prompt the operator to create a schema named `mydb` in the MySQL server and update
the resource status with its URL. Once the resource is deleted, the operator will delete the schema. Obviously don't
use it as is with real databases. 

### Try 

To try how the operator works you will need the following:
* JDK installed (minimum version 11, tested with 11 and 15)
* Maven installed (tested with 3.6.3)
* A working Kubernetes cluster (tested with v1.15.9-gke.24)
* kubectl installed (tested with v1.15.5)
* Docker installed (tested with 19.03.8)
* Container image registry

How to configure all the above depends heavily on where your Kubernetes cluster is hosted. 
If you use [minikube](https://minikube.sigs.k8s.io/docs/) you will need to configure kubectl and docker differently
than if you'd use [GKE](https://cloud.google.com/kubernetes-engine/). You will have to read the documentation of your
Kubernetes provider to figure this out.

Once you have the basics you can build and deploy the operator.

### Build & Deploy

1. We will be building the Docker image from the source code using Maven, so we have to configure the Docker registry
where the image should be pushed. Do this in mysql-schema/pom.xml. In the example below I'm setting it to
the [Container Registry](https://cloud.google.com/container-registry/) in Google Cloud Europe. 
  
```xml
<to>
   <image>eu.gcr.io/my-gcp-project/mysql-operator</image>
</to>
```

1. The following Maven command will build the JAR file, package it as a Docker image and push it to the registry.

   `mvn jib:dockerBuild` 

1. Deploy the test MySQL on your cluster if you want to use it. Note that if you have an already running MySQL server
you want to use, you can skip this step, but you will have to configure the operator to use that server.
   
   `kubectl apply -f k8s/mysql-db.yaml`
1. Deploy the CRD: 

   `kubectl apply -f k8s/crd.yaml`
   
1. Make a copy of `k8s/operator.yaml` and replace ${DOCKER_REGISTRY} and ${OPERATOR_VERSION} to the 
right values. You will want to set `OPERATOR_VERSION` to the one used for building the Docker image. `DOCKER_REGISTRY` should
be the same as you set the docker-registry property in your `pom.xml`.
If you look at the environment variables you will notice this is where the access to the MySQL server is configured.
The default values assume the server is running in another Kubernetes namespace (called `mysql`), uses the `root` user
with a not very secure password. In case you want to use a different MySQL server, this is where you configure it. 

1. Run `kubectl apply -f copy-of-operator.yaml` to deploy the operator. You can wait for the deployment to succeed using
this command: `kubectl rollout status deployment mysql-schema-operator -w`. `-w` will cause kubectl to continuously monitor 
the deployment until you stop it.

1. Now you are ready to create some databases! To create a database schema called `mydb` just apply the `k8s/schema.yaml`
file with kubectl: `kubectl apply -f k8s/schema.yaml`. You can modify the database name in the file to create more schemas.
