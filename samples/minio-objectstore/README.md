# ObjectStore Operator

This is a more complex example of how a Custom Resource backed by an Operator can serve as
an abstraction layer for some other custom resource. 
This Operator will use an ObjectStore resource, which mainly contains minio operator's custom resource 
definitions and creates a minio deployment.
Refer: https://github.com/minio/minio-operator for more details on the custom resource we would be deploying, 
which intrun would deploy minio.

This is an example input of our object store:
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

After minio operator installs and configures **minio**, our operator will configure buckets on that **minio** instance.
It will use the configration under `stores` to create these buckets, by using minio java client.

_This operator can further be extended to create custom objects, inside these stores._

### Try 

The quickest way to try the operator is to run it on your local machine, while it connects to a local or remote
Kubernetes cluster. When you start it it will use the current kubectl context on your machine to connect to the cluster.

Before you run it you have to install the custom CRDs on your cluster by running:
- `kubectl apply -f k8s/deployment.yaml`
- `kubectl apply -f k8s/minio-operator.yaml`

When the Operator is running you can create some ObjectStore Custom Resources. You can find a sample custom resource in
`crd/objectstore.yaml`. You can create it by running `kubectl apply -f crd/objectstore.yaml`.

After the Operator has picked up the new ObjectStore resource 
(see the logs via `kubectl logs <pod name>` in either the java operator's namespace or minio operator's namespace), 
it should create the minio instance resource via minio operator in the **provided namespace**. 

You need to switch to the running instance's kubectl context in order to view the deployment/pod/service of your minio instance in the **provided namespace**.

Here are the steps you can use (*verified for local deployments*).
- Add a context to kubectl ex: `kubectl config set-context <name> --namespace=<provided namespace> --cluster=minikube --user=minikube`

  *note cluter name and user name can be different based on your installation*
- switch to context using: `kubectl config use-context <name>`
- To connect to the minio server using your browser you can run `kubectl get service` and view the service(s) created by the Operator. 

  *note the default port on which the service would be running would be 9000*
  
  *note to expose minio outside of cluster you may need to configure a service of type NodePort or LoadBalancer*
  
  *refer: https://github.com/sjmittal/java-operator-sdk/blob/sample-minio-operator/samples/minio-objectstore/src/main/resources/com/github/containersolutions/operator/sample/service.yaml*
- To view the pod on which minio is running: `kubectl get pods`.
- If you are running a single-node cluster (e.g. Docker for Mac or Minikube) you can connect to the VM's port using port forward from local host.

  *`kubectl port-forward pods/<pod name> <local port>:<minio port on pod>`*
- finally access the instance via browser http://localhost:9000 (minio/minio123) default credentials as configured in:
  
  *refer: https://github.com/sjmittal/java-operator-sdk/blob/sample-minio-operator/samples/minio-objectstore/src/main/resources/com/github/containersolutions/operator/sample/secret.yaml*
  
You can also try to change the code in `crd/objectstore.yaml` and do another `kubectl apply -f crd/objectstore.yaml`.
This should update the actual minio deployment with new configuration.  

### Build

You can build the sample using `mvn dockerfile:build` this will produce a Docker image you can push to the registry 
of your choice. The jar file is built using your local Maven and JDK and then copied into the Docker image.
In windows if you are using minikube with docker vm, make sure to switch to docker deamon of kubernetes so images are directly
copied on to the vm.
Refer:
https://kubernetes.io/docs/setup/learning-environment/minikube/#use-local-images-by-re-using-the-docker-daemon
So basically run:
- `@FOR /f "tokens=*" %i IN ('minikube -p minikube docker-env') DO @%i` before instally and building docker image.
- To install the jar run: `mvn install`
- To build the image run: `mvn dockerfile:build`


### Deployment

1. Deploy the CRD and the operator: `kubectl apply -f k8s/deployment.yaml`
2. Deploy minio operator CRD and operator: `kubectl apply -f k8s/minio-operator.yaml`
3. Deploy your instance of custom java operator: `kubectl apply -f crd/objectstore.yaml`
