# WebPage Operator

This is a simple example of how a Custom Resource backed by an Operator can serve as
an abstraction layer. This Operator will use a WebPage resource, which mainly contains a
static webpage definition and creates an NGINX Deployment backed by a ConfigMap which holds
the HTML.

This is an example input:
```yaml
apiVersion: "sample.javaoperatorsdk/v1"
kind: WebPage
metadata:
  name: mynginx-hello
spec:
  html: |
    <html>
      <head>
        <title>Webserver Operator</title>
      </head>
      <body>
        Hello World!
      </body>
    </html>
```


### Different Flavors

Sample contains three implementation, that are showcasing the different approaches possible with the framework,
the resulting behavior is almost identical behavior at the end:

- [Low level API](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageDependentsWorkflowReconciler.java)
- [Using managed dependent resources](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java)
- [Using standalone Dependent Resources](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageStandaloneDependentsReconciler.java)

### Try 

The quickest way to try the operator is to run it on your local machine, while it connects to a local or remote
Kubernetes cluster. When you start it, it will use the current kubectl context on your machine to connect to the cluster.

Before you run it you have to install the CRD on your cluster by running
`kubectl apply -f target/classes/META-INF/fabric8/webpages.sample.javaoperatorsdk-v1.yml`.

The CRD is generated automatically from your code by simply adding the `crd-generator-apt`
dependency to your `pom.xml` file.

When the Operator is running you can create some Webserver Custom Resources. You can find a sample custom resource in
`k8s/webpage.yaml`. You can create it by running `kubectl apply -f k8s/webpage.yaml`

After the Operator has picked up the new webserver resource (see the logs) it should create the NGINX server in the 
same namespace where the webserver resource is created. To connect to the server using your browser you can
run `kubectl get service` and view the service created by the Operator. It should have a NodePort configured. If you are
running a single-node cluster (e.g. Docker for Mac or Minikube) you can connect to the VM on this port to access the
page. Otherwise you can change the service to a LoadBalancer (e.g on a public cloud).

You can also try to change the HTML code in `k8s/webpage.yaml` and do another `kubectl apply -f k8s/webpage.yaml`.
This should update the actual NGINX deployment with the new configuration.  

Note that there are multiple reconciler implementations that watch `WebPage` resources differentiated by a label.
When you create a new `WebPage` resource, make sure its label matches the active reconciler's label selector.

If you want the Operator to be running as a deployment in your cluster, follow the below steps.

### Build

In order to point your docker build to minikube docker registry run:

```
eval $(minikube docker-env)
```

You can build the sample using `mvn jib:dockerBuild` this will produce a Docker image you can push to the registry 
of your choice. The JAR file is built using your local Maven and JDK and then copied into the Docker image.

### Deployment

1. Deploy the CRD: `kubectl apply -f target/classes/META-INF/fabric8/webpages.sample.javaoperatorsdk-v1.yml`
2. Deploy the operator: `kubectl apply -f k8s/operator.yaml`

To install observability components - such as Prometheus, Open Telemetry, Grafana use - execute:
[install-observability.sh](../../observability/install-observability.sh)
