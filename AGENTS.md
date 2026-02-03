# AI Agent Context for Java Operator SDK

This file provides context for AI agents working on the Java Operator SDK project.

## Project Overview

**Java Operator SDK** is a CNCF project for building Kubernetes Operators in Java.
- **Repository**: https://github.com/operator-framework/java-operator-sdk
- **Website**: https://javaoperatorsdk.io/
- **Version**: 5.2.3-SNAPSHOT
- **License**: Apache 2.0
- **Java Version**: 17

## Project Structure

This is a Maven multi-module project with the following modules:

### Core Modules
- **operator-framework-core** - Core framework implementation
- **operator-framework** - Main SDK package with high-level APIs
- **operator-framework-bom** - Bill of Materials for dependency management
- **operator-framework-junit5** - JUnit 5 testing support for operators

### Extension Modules
- **micrometer-support** - Micrometer metrics integration
- **caffeine-bounded-cache-support** - Caffeine cache support with bounded eviction

### Tooling
- **bootstrapper-maven-plugin** - Maven plugin for scaffolding new operators
- **test-index-processor** - Test utilities for annotation processing

### Examples
- **sample-operators** - Example operator implementations

## Directory Structure

```
java-operator-sdk/
├── .github/                    # GitHub workflows and templates
│   ├── workflows/             # CI/CD workflows
│   └── ISSUE_TEMPLATE/        # Issue templates
├── adr/                       # Architecture Decision Records
├── docs/                      # Documentation (Hugo-based)
├── bootstrapper-maven-plugin/     # Maven plugin for scaffolding new operators
├── caffeine-bounded-cache-support/ # Caffeine cache support with bounded eviction
├── micrometer-support/            # Micrometer metrics integration
├── operator-framework/            # Main SDK package with high-level APIs
├── operator-framework-bom/        # Bill of Materials for dependency management
├── operator-framework-core/       # Core framework implementation
├── operator-framework-junit5/     # JUnit 5 testing support for operators
├── sample-operators/              # Example operator implementations
├── test-index-processor/          # Test utilities for annotation processing
├── pom.xml                    # Root POM
├── CONTRIBUTING.md            # Contribution guidelines
├── CODE_OF_CONDUCT.md         # Code of conduct
└── README.md                  # Project README

```

## Key Technologies

- **Build Tool**: Maven
- **Java Version**: 17
- **Kubernetes Client**: Fabric8 Kubernetes Client
- **Testing**: JUnit, Mockito, AssertJ
- **Code Style**: Google Java Format
- **Documentation**: Hugo static site generator

## Development Workflow

### Code Style
- Follows Google Java Code Style
- Automatically formatted on `mvn compile`
- Use IDE plugins for real-time formatting

### Building
```bash
mvn clean install
```

### Testing
```bash
mvn test
```

### Integration Tests
See `.github/workflows/integration-tests.yml` and `e2e-test.yml`

### Pull Request Process
1. Follow [Conventional Commits](https://www.conventionalcommits.org/) format
2. All PRs must pass CI checks
3. Code must be reviewed and approved
4. New code requires new tests

## GitHub Workflows

- **build.yml** - Build
- **e2e-test.yml** - E2E Test
- **hugo.yaml** - Hugo
- **integration-tests.yml** - Integration Tests
- **maintain-docs.yml** - Maintain Docs
- **pr.yml** - Pr
- **release-project-in-dir.yml** - Release Project In Dir
- **release.yml** - Release
- **snapshot-releases.yml** - Snapshot Releases
- **sonar.yml** - Sonar

## Important Files to Monitor

When project structure changes, update:
- This file (AGENTS.md) - AI agent context
- CLAUDE.md - Claude-specific guidelines
- Root pom.xml - Module list and dependencies
- CONTRIBUTING.md - Development guidelines
- docs/ - Documentation site

## Maintenance Notes

This file should be updated when:
- New Maven modules are added or removed
- Directory structure changes significantly
- Core dependencies are upgraded
- Build or test infrastructure changes
- CI/CD workflows are modified

Last updated: 2026-02-03
