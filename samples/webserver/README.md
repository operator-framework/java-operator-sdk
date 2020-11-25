# WebServer Operator

This is a more complex example of how a Custom Resource backed by an Operator can serve as
an abstraction layer. This Operator will use an webserver resource, which mainly contains a
static webpage definition and creates a nginx Deployment backed by a ConfigMap which holds
the html.

This is an example input:
```yaml
apiVersion: "sample.javaoperatorsdk.io/v1"
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
        Hello World!!
      </body>
    </html>
```

### Try 

The quickest way to try the operator is to run it on your local machine, while it connects to a local or remote
Kubernetes cluster. When you start it it will use the current kubectl context on your machine to connect to the cluster.

Before you run it you have to install the CRD on your cluster by running `kubectl apply -f crd/crd.yaml`

When the Operator is running you can create some Webserver Custom Resources. You can find a sample custom resource in
`crd/webserver.yaml`. You can create it by running `kubectl apply -f webserver.yaml`

After the Operator has picked up the new webserver resource (see the logs) it should create the nginx server in the 
same namespace where the webserver resource is created. To connect to the server using your browser you can
run `kubectl get service` and view the service created by the Operator. It should have a NodePort configured. If you are
running a single-node cluster (e.g. Docker for Mac or Minikube) you can connect to the VM on this port to access the
page.

You can also try to change the html code in `crd/webserver.yaml` and do another `kubectl apply -f crd/webserver.yaml`.
This should update the actual nginx deployment with new configuration.  

### Build

You can build the sample using `mvn dockerfile:build` this will produce a Docker image you can push to the registry 
of your choice. The jar file is built using your local Maven and JDK and then copied into the Docker image.

### Deployment

1. Deploy the CRD: kubectl apply -f crd/crd.yaml
2. Deploy the operator: kubectl apply -f k8s/deployment.yaml
