# AI Agents Guide for Java Operator SDK

This document provides guidance for AI coding agents working with the Java Operator SDK codebase.

## Project Overview

Java Operator SDK is a production-ready framework for building Kubernetes Operators in Java. It provides:
- A controller runtime for reconciliation loops
- Support for dependent resources and workflows
- Testing utilities for operator development
- Integration with Fabric8 Kubernetes Client

**Key Technologies:**
- Java 17 (compilation target), validated in CI against Java 17, 21, and 25
- Maven for build management
- Fabric8 Kubernetes Client for K8s API access
- JUnit 6 (via `org.junit:junit-bom`) for testing
- GitHub Actions for CI/CD

## Project Structure

### Core Modules

```
java-operator-sdk/
├── operator-framework-core/        # Core reconciliation engine and API
├── operator-framework/             # Main operator framework implementation
├── operator-framework-junit/      # Testing utilities and extensions
├── operator-framework-bom/         # Bill of Materials for dependency management
├── micrometer-support/             # Metrics integration
├── open-telemetry-support/         # Distributed tracing support
├── caffeine-bounded-cache-support/ # Caching implementation
├── bootstrapper-maven-plugin/      # Maven plugin for bootstrapping
└── test-index-processor/           # Test utilities for annotation processing
```

### Key Packages

- `io.javaoperatorsdk.operator.api.reconciler` - Core reconciler interfaces and annotations
- `io.javaoperatorsdk.operator.processing` - Event processing and workflow engine
- `io.javaoperatorsdk.operator.processing.dependent` - Dependent resource management
- `io.javaoperatorsdk.operator.api.config` - Configuration interfaces
- `io.javaoperatorsdk.operator.junit` - Testing support classes

## Working Effectively

### Build Commands

```bash
# Full build with tests
./mvnw clean install

# Build without tests
./mvnw clean install -DskipTests

# Parallel build (uses 1 thread per CPU core)
./mvnw -T1C clean install

# Check code formatting
./mvnw spotless:check

# Apply code formatting
./mvnw spotless:apply

# Check license headers
./mvnw -N license:check
```

### Test Execution

```bash
# Run unit tests only
./mvnw test

# Run integration tests
./mvnw verify -Pintegration-tests

# Run specific test class
./mvnw test -Dtest=ClassName

# Run specific test method
./mvnw test -Dtest=ClassName#methodName
```

## Code Conventions

### Code Style

- Formatting: The project uses Spotless with Google Java Format
- License Headers: All source files must have Apache 2.0 license headers
- Line Length: 100 characters maximum
- Indentation: 2 spaces (no tabs)
- Prefer `var` to avoid type declarations, except for very short type names like `int`, `long`, `String` etc.
- Always use proper imports for classes instead of fully qualified class references. Import classes at the top of the file and use simple class names throughout the code, only using fully qualified names when absolutely necessary to avoid naming collisions.
- Add unit and/or integration tests for new functionality whenever reasonably possible
- Avoid excessive logging, only add logs to critical parts. Avoid both logging errors and throwing exceptions at the same time. Throwing the error is enough it is logged already somewhere else.
- Do not add comments to the code, except in case of very long or complex logic.

### Naming Conventions

- **Reconcilers:** End with `Reconciler` (e.g., `MyResourceReconciler`)
- **Dependent Resources:** End with `DependentResource` (e.g., `ConfigMapDependentResource`)
- **Test Classes:** End with `Test` for unit tests, `IT` for integration tests, `E2E` for end-to-end testing.
- **Custom Resources:** Typically structured as `{Name}Spec`, `{Name}Status`, `{Name}` (the CR class)

### API Design

- Use builder patterns for complex configurations
- Prefer immutable objects where possible
- Use annotations for declarative configuration (`@ControllerConfiguration`, `@KubernetesDependent`, etc.)
- Follow fluent API design for DSLs

## Testing Guidelines

### Unit Tests

- Use JUnit 6
- Mock Kubernetes API interactions using Fabric8's mock server or Mockito; or service layer directly
- Test reconciliation logic in isolation
- Place in `src/test/java`

### Integration Tests

- Use `LocallyRunOperatorExtension` or `OperatorExtension` from `operator-framework-junit`
- Test against real Kubernetes API (typically via test cluster like minikube or kind)
- Suffix with `IT` (e.g., `MyReconcilerIT`)
- Located in `src/test/java`

### Test Resources

- Kubernetes manifests in `src/test/resources`

## Common Patterns

### Reconciler Implementation

Reconcilers implement the `Reconciler<T>` interface:

```java
@ControllerConfiguration
public class MyReconciler implements Reconciler<MyCustomResource> {

  @Override
  public UpdateControl<MyCustomResource> reconcile(
      MyCustomResource resource, Context<MyCustomResource> context) {
    // Reconciliation logic
    return UpdateControl.noUpdate();
  }
}
```

### Dependent Resources

Dependent resources use the `DependentResource` interface or extend base classes:

```java
@KubernetesDependent
public class ConfigMapDependent extends CRUDKubernetesDependentResource<ConfigMap, Primary> {

  @Override
  protected ConfigMap desired(Primary primary, Context<Primary> context) {
    // Return desired state
  }
}
```

### Error Handling

- Use `UpdateControl` with `rescheduleAfter()` for retriable errors
- Throw `RuntimeException` for non-retriable errors
- Update resource status to reflect error conditions
- Use structured logging (SLF4J)

## Making Changes

### Before Submitting a PR

1. Run `./mvnw spotless:apply` to format code
2. Run `./mvnw clean install` to ensure all tests pass
3. Add tests for new functionality
4. Update documentation if adding/changing APIs
5. Follow existing code patterns and conventions

### PR Guidelines

- Keep changes focused and atomic
- We use conventional commits 
- Reference issues in commit messages when applicable
- Ensure CI checks pass (format, license, tests)

### Documentation Update

When implementing features or making code changes, always evaluate whether the change affects user-facing behavior that should be documented. If so, update the documentation as part of the same change.

#### Documentation Location

Documentation is a Hugo/Docsy site at `docs/content/en/docs/`:

- `documentation/` — core feature docs (reconciler, eventing, error handling, caches, rate limiting, testing, features)
- `documentation/dependent-resource-and-workflows/` — dependent resources and workflows
- `documentation/operations/` — operational concerns (config, metrics, logging, health probes, leader election, helm)
- `getting-started/` — introductory guides and best practices
- `migration/` — version migration guides
- `faq/` — frequently asked questions

#### Behavior

1. After implementing a code change, check if it introduces, modifies, or removes user-facing behavior.
2. Search the docs for pages covering the affected functionality.
3. Update existing pages or add new sections to reflect the change.
4. Skip if not relevant — internal refactors, test-only changes, and build tooling changes typically do not need doc updates.

#### Guidelines

- Match the tone and style of the existing documentation
- Prefer adding code snippets
- Keep explanations concise with code examples where helpful
- If a change introduces a new feature, consider whether it belongs in an existing page or warrants a new one
- For breaking changes, also update the relevant migration guide under `migration/`

## Common Issues and Solutions

### Build Issues

- **Test failures:** Check if Kubernetes context is needed for ITs
- **Formatting failures:** Run `./mvnw spotless:apply` before committing

### Test Issues

- **Integration tests hanging:** Check for resource leaks or improper cleanup
- **Flaky tests:** Ensure proper synchronization using `StatusChecker` or similar patterns
- **Performance test variance:** Results depend on available CPU/memory

## Resources

- **Documentation:** https://javaoperatorsdk.io/
- **GitHub:** https://github.com/operator-framework/java-operator-sdk
- **Slack:** [#java-operator-sdk](https://kubernetes.slack.com/archives/CAW0GV7A5) on Kubernetes Slack
- **Discord:** https://discord.gg/DacEhAy
- **Fabric8 Client:** https://github.com/fabric8io/kubernetes-client

## Additional Notes for AI Agents

- The codebase makes extensive use of Java generics for type safety
- Context objects provide access to client, informers, and event sources
- The framework handles K8s API retries and conflict resolution automatically
- Prefer using `ResourceOperations` from context over direct client calls
- Status updates are handled separately from spec reconciliation
