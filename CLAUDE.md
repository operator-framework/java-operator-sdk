# Claude AI Assistant Guidelines for Java Operator SDK

This file provides specific guidelines for Claude AI when working on the Java Operator SDK project.

## Project Context

You are working on **Java Operator SDK**, a framework for building Kubernetes Operators in Java. This is:
- A CNCF (Cloud Native Computing Foundation) project
- A multi-module Maven project
- Production code used by many organizations
- Open source under Apache 2.0 license

## Code Quality Standards

### Java Code Style
- **MUST** follow Google Java Format
- Code is auto-formatted on compilation
- Do not add unnecessary comments unless required for clarity
- Follow existing patterns in the codebase

### Testing Requirements
- All new code **MUST** have tests
- Use JUnit 5 for testing
- Follow existing test patterns in each module
- Integration tests use Fabric8 Kubernetes Client

### Commit Messages
- **MUST** follow [Conventional Commits](https://www.conventionalcommits.org/) format
- Examples:
  - `feat: add new operator lifecycle hook`
  - `fix: resolve NPE in reconciliation loop`
  - `docs: update getting started guide`
  - `chore: bump dependency versions`
  - `test: add test for error handling`

## Working with the Codebase

### Before Making Changes
1. Read relevant Architecture Decision Records (ADRs) in `/adr`
2. Check existing tests to understand expected behavior
3. Review CONTRIBUTING.md for contribution guidelines
4. Run tests to ensure baseline passes

### Module Structure
Each module typically has:
```
module-name/
├── src/
│   ├── main/
│   │   ├── java/           # Source code
│   │   └── resources/      # Resources
│   └── test/
│       ├── java/           # Test code
│       └── resources/      # Test resources
└── pom.xml                 # Module POM
```

### Common Commands
```bash
# Format code
mvn fmt:format

# Build entire project
mvn clean install

# Build specific module
mvn clean install -pl operator-framework-core -am

# Run tests
mvn test

# Skip tests (use sparingly)
mvn clean install -DskipTests
```

## Special Considerations

### Kubernetes Integration
- Code interacts with Kubernetes API via Fabric8 client
- Be careful with resource management (close clients, handle errors)
- Consider multi-threading implications (reconciliation is concurrent)
- Understand Kubernetes concepts: reconciliation, watches, finalizers

### Backward Compatibility
- This is a library used by many projects
- Breaking changes require careful consideration
- Deprecate before removing APIs
- Follow semantic versioning principles

### Performance
- Operators run continuously in production
- Avoid unnecessary API calls to Kubernetes
- Consider caching strategies
- Be mindful of memory usage (long-running processes)

## Common Patterns

### Reconciliation Pattern
```java
@Override
public UpdateControl<CustomResource> reconcile(CustomResource resource, Context<CustomResource> context) {
    // 1. Get current state from Kubernetes
    // 2. Determine desired state
    // 3. Take action to move current -> desired
    // 4. Update status
    // 5. Return UpdateControl
}
```

### Error Handling
```java
try {
    // operation
} catch (Exception e) {
    log.error("Failed to reconcile resource", e);
    return UpdateControl.updateStatus(resource)
        .rescheduleAfter(Duration.ofMinutes(1));
}
```

## File Maintenance

When you make changes that affect project structure:

### Update AGENTS.md if:
- Maven modules are added/removed
- Directory structure changes
- Key dependencies are upgraded
- CI/CD workflows are modified

### Update CLAUDE.md if:
- Coding guidelines change
- New patterns are established
- Testing requirements evolve
- Build process changes

### Update Other Files:
- **pom.xml** - Module lists, dependencies
- **CONTRIBUTING.md** - Development processes
- **docs/** - User-facing documentation
- **README.md** - Project overview

## Testing Guidelines

### Unit Tests
- Test single components in isolation
- Use mocking for dependencies (Mockito)
- Fast execution (no I/O, no network)

### Integration Tests
- Test interaction between components
- May use test Kubernetes cluster
- Found in separate test classes (often *IT.java)

### End-to-End Tests
- Test complete operator workflows
- Use actual Kubernetes cluster (kind, k3s)
- Defined in `.github/workflows/e2e-test.yml`

## Common Issues to Avoid

1. **Don't** break backward compatibility without discussion
2. **Don't** add dependencies without justification
3. **Don't** skip tests or remove existing tests
4. **Don't** ignore code style (will fail CI)
5. **Don't** commit secrets or credentials
6. **Don't** make large refactorings without incremental steps
7. **Don't** modify generated code (regenerate instead)

## When Stuck

1. Check ADRs in `/adr` for architectural decisions
2. Look for similar patterns in existing code
3. Check documentation at https://javaoperatorsdk.io/
4. Review related GitHub issues
5. Ask for clarification rather than guessing

## Security Considerations

- Validate all inputs from Kubernetes resources
- Be careful with RBAC permissions
- Don't log sensitive information
- Handle secrets appropriately
- Consider security implications of operator actions

## Documentation

- Update JavaDoc for public APIs
- Update user documentation in `/docs` if behavior changes
- Update README.md if setup changes
- Consider adding ADR for significant architectural changes

---

**Remember**: You're working on critical infrastructure code. Quality, reliability, and backward compatibility are paramount.

Last updated: 2026-02-03
