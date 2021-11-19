# WebServer Operator

This is a simple example of how a Custom Resource backed by an Operator can serve as
an abstraction layer. This Operator will use a webserver resource, which mainly contains a
static webpage definition and creates an NGINX Deployment backed by a ConfigMap which holds
the HTML.

This is an example input:
```yaml
apiVersion: "sample.javaoperatorsdk/v1"
kind: WebServer
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

### Try 

The quickest way to try the operator is to run it on your local machine, while it connects to a local or remote
Kubernetes cluster. When you start it, it will use the current kubectl context on your machine to connect to the cluster.

Before you run it you have to install the CRD on your cluster by running `kubectl apply -f k8s/crd.yaml`

When the Operator is running you can create some Webserver Custom Resources. You can find a sample custom resource in
`k8s/webserver.yaml`. You can create it by running `kubectl apply -f k8s/webserver.yaml`

After the Operator has picked up the new webserver resource (see the logs) it should create the NGINX server in the 
same namespace where the webserver resource is created. To connect to the server using your browser you can
run `kubectl get service` and view the service created by the Operator. It should have a NodePort configured. If you are
running a single-node cluster (e.g. Docker for Mac or Minikube) you can connect to the VM on this port to access the
page. Otherwise you can change the service to a LoadBalancer (e.g on a public cloud).

You can also try to change the HTML code in `k8s/webserver.yaml` and do another `kubectl apply -f k8s/webserver.yaml`.
This should update the actual NGINX deployment with the new configuration.  

### Build

You can build the sample using `mvn jib:dockerBuild` this will produce a Docker image you can push to the registry 
of your choice. The JAR file is built using your local Maven and JDK and then copied into the Docker image.

### Deployment

1. Deploy the CRD: `kubectl apply -f k8s/crd.yaml`
2. Deploy the operator: `kubectl apply -f k8s/operator.yaml`
