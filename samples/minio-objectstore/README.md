# ObjectStore Operator

This is a more complex example of how a Custom Resource backed by an Operator can serve as
an abstraction layer. This Operator will use an ObjectStore resource, which mainly contains minio 
definitions and creates a minio deployment with buckets

This is an example input:
```yaml
apiVersion: "sample.javaoperatorsdk/v1"
kind: ObjectStore
metadata:
  name: my-objectstore
spec:
  name: objectstore
  deployNamespace: objectstore-runtime
  instances:
    zone:
      name: zone-0
      count: 1
      mem: 123
      cpu: 123
  volumes:
    count: 1
    size: 10M
  stores: 
    configstore: 
      - cfg-test
    filestore:
      - file-test
```

### Try 

The quickest way to try the operator is to run it on your local machine, while it connects to a local or remote
Kubernetes cluster. When you start it it will use the current kubectl context on your machine to connect to the cluster.

Before you run it you have to install the CRD on your cluster by running `kubectl apply -f crd/crd.yaml`

When the Operator is running you can create some ObjectStore Custom Resources. You can find a sample custom resource in
`crd/objectstore.yaml`. You can create it by running `kubectl apply -f objectstore.yaml`

After the Operator has picked up the new ObjectStore resource (see the logs) it should create the minio server in the 
provided namespace. To connect to the server using your browser you can run `kubectl get service` and view the service 
created by the Operator. It should have a NodePort configured. If you are running a single-node cluster 
(e.g. Docker for Mac or Minikube) you can connect to the VM on this port to access the page.

You can also try to change the code in `crd/objectstore.yaml` and do another `kubectl apply -f crd/objectstore.yaml`.
This should update the actual minio deployment with new configuration.  

### Build

You can build the sample using `mvn dockerfile:build` this will produce a Docker image you can push to the registry 
of your choice. The jar file is built using your local Maven and JDK and then copied into the Docker image.

### Deployment

1. Deploy the CRD: kubectl apply -f crd/crd.yaml
2. Deploy the operator: kubectl apply -f k8s/deployment.yaml
