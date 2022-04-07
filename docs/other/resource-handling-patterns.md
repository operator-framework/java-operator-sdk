# Resource Management Atomic Resource Relations

The goal of this document is to enumerate and explore of resource management patters in a reconciler. This should
server as an input to argumentation in issues, architectural and design decisions.
Pattern will be a type of (usually two) resources a resource and a relation between the resources. 

## Notes

- `Kubernetes Resource` in this context means that any well known Kubernetes resource (pod, deployment, config map,...)
  or any custom resource. 
- `External Resource` is any **non Kubernetes resource**, in other words any resource that is managed by an API call 
  outside of Kubernetes (samples: GitHub repository, S3 bucket, Jenkins Pipeline, Database Schema) by a controller.
- `A depends on B` means that the resource needs to be created or updated first it's output values used as an input for
  other resource. This is typically values from a status of a resource, like any status from 
  custom resource or ip from a service:  
  
  ```yaml
    apiVersion: v1
    kind: Service
    metadata:
        name: my-service
    spec:
        selector:
            app: MyApp
        ports:
        - protocol: TCP
          port: 80
          targetPort: 9376
        clusterIP: 10.0.171.239
        type: LoadBalancer
    status:
        loadBalancer:
            ingress:
            - ip: 192.0.2.127
  ```
  

## Patterns

1. Independent Resources (both External and Kubernetes)

3. Non Independent Kubernetes Resources
4. External Resource depending on a Kubernetes Resource
5. Kubernetes Resource depends on External Resource 
6. External Resource depends on other External Resource
