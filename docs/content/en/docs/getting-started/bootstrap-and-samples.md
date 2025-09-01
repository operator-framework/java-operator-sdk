---
title: Bootstrapping and samples
weight: 20
---

## Creating a New Operator Project

### Using the Maven Plugin

The simplest way to start a new operator project is using the provided Maven plugin, which generates a complete project skeleton:

```shell
mvn io.javaoperatorsdk:bootstrapper:[version]:create \
  -DprojectGroupId=org.acme \
  -DprojectArtifactId=getting-started
```

This command creates a new Maven project with:
- A basic operator implementation
- Maven configuration with required dependencies  
- Generated [CustomResourceDefinition](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions) (CRD)

### Building Your Project

Build the generated project with Maven:
```shell
mvn clean install
```

The build process automatically generates the CustomResourceDefinition YAML file that you'll need to apply to your Kubernetes cluster.

## Exploring Sample Operators

The [sample-operators](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators) directory contains real-world examples demonstrating different JOSDK features and patterns:

### Available Samples

**[webpage](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators/webpage)**
- **Purpose**: Creates NGINX webservers from Custom Resources containing HTML code
- **Key Features**: Multiple implementation approaches using both low-level APIs and higher-level abstractions
- **Good for**: Understanding basic operator concepts and API usage patterns

**[mysql-schema](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators/mysql-schema)**  
- **Purpose**: Manages database schemas in MySQL instances
- **Key Features**: Demonstrates managing non-Kubernetes resources (external systems)
- **Good for**: Learning how to integrate with external services and manage state outside Kubernetes

**[tomcat](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators/tomcat)**
- **Purpose**: Manages Tomcat instances and web applications
- **Key Features**: Multiple controllers managing related custom resources
- **Good for**: Understanding complex operators with multiple resource types and relationships

## Running the Samples

### Prerequisites

The easiest way to try samples is using a local Kubernetes cluster:
- [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) 
- [kind](https://kind.sigs.k8s.io/)
- [Docker Desktop with Kubernetes](https://docs.docker.com/desktop/kubernetes/)

### Step-by-Step Instructions

1. **Apply the CustomResourceDefinition**:
   ```shell
   kubectl apply -f target/classes/META-INF/fabric8/[resource-name]-v1.yml
   ```

2. **Run the operator**:
   ```shell
   mvn exec:java -Dexec.mainClass="your.main.ClassName"
   ```
   Or run your main class directly from your IDE.

3. **Create custom resources**:
   The operator will automatically detect and reconcile custom resources when you create them:
   ```shell
   kubectl apply -f examples/sample-resource.yaml
   ```

### Detailed Examples

For comprehensive setup instructions and examples, see:
- [MySQL Schema sample README](https://github.com/operator-framework/java-operator-sdk/blob/main/sample-operators/mysql-schema/README.md)
- Individual sample directories for specific setup requirements

## Next Steps

After exploring the samples:
1. Review the [patterns and best practices](../patterns-best-practices) guide
2. Learn about [implementing reconcilers](../../documentation/reconciler)
3. Explore [dependent resources and workflows](../../documentation/dependent-resource-and-workflows) for advanced use cases



