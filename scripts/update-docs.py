#!/usr/bin/env python3
"""
Update AGENTS.md and CLAUDE.md with current project structure.

This script extracts information from the project and updates the documentation
files to reflect the current state of the repository.
"""

import xml.etree.ElementTree as ET
import os
import sys
from datetime import datetime


def extract_modules_from_pom():
    """Extract module list from root pom.xml"""
    try:
        tree = ET.parse('pom.xml')
        root = tree.getroot()
        
        # Handle XML namespace
        ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
        
        # Get version
        version_elem = root.find('mvn:version', ns)
        if version_elem is None:
            version_elem = root.find('version')
        version = version_elem.text if version_elem is not None else "unknown"
        
        # Get modules
        modules_elem = root.find('mvn:modules', ns)
        if modules_elem is None:
            modules_elem = root.find('modules')
        
        if modules_elem is None:
            return version, []
        
        modules = []
        for module in modules_elem:
            module_name = module.text.strip() if module.text else ""
            if module_name:
                modules.append(module_name)
        
        return version, sorted(modules)
    except Exception as e:
        print(f"Error parsing pom.xml: {e}", file=sys.stderr)
        return "unknown", []


def get_workflows():
    """Get list of GitHub workflows"""
    workflow_dir = '.github/workflows'
    if not os.path.exists(workflow_dir):
        return []
    
    workflows = []
    for file in sorted(os.listdir(workflow_dir)):
        if file.endswith(('.yml', '.yaml')):
            workflows.append(file)
    
    return workflows


def update_agents_md(version, modules, workflows):
    """Update AGENTS.md with current project structure"""
    
    # Module descriptions (update as needed)
    module_descriptions = {
        'operator-framework-core': 'Core framework implementation',
        'operator-framework': 'Main SDK package with high-level APIs',
        'operator-framework-bom': 'Bill of Materials for dependency management',
        'operator-framework-junit5': 'JUnit 5 testing support for operators',
        'micrometer-support': 'Micrometer metrics integration',
        'caffeine-bounded-cache-support': 'Caffeine cache support with bounded eviction',
        'bootstrapper-maven-plugin': 'Maven plugin for scaffolding new operators',
        'test-index-processor': 'Test utilities for annotation processing',
        'sample-operators': 'Example operator implementations',
    }
    
    today = datetime.now().strftime('%Y-%m-%d')
    
    content = f"""# AI Agent Context for Java Operator SDK

This file provides context for AI agents working on the Java Operator SDK project.

## Project Overview

**Java Operator SDK** is a CNCF project for building Kubernetes Operators in Java.
- **Repository**: https://github.com/operator-framework/java-operator-sdk
- **Website**: https://javaoperatorsdk.io/
- **Version**: {version}
- **License**: Apache 2.0
- **Java Version**: 17

## Project Structure

This is a Maven multi-module project with the following modules:

"""
    
    # Categorize modules
    core_modules = ['operator-framework-core', 'operator-framework', 'operator-framework-bom', 'operator-framework-junit5']
    extension_modules = ['micrometer-support', 'caffeine-bounded-cache-support']
    tooling_modules = ['bootstrapper-maven-plugin', 'test-index-processor']
    example_modules = ['sample-operators']
    
    def add_module_section(title, module_list):
        section = f"### {title}\n"
        for module in module_list:
            if module in modules:
                desc = module_descriptions.get(module, 'Module description')
                section += f"- **{module}** - {desc}\n"
        return section + "\n"
    
    content += add_module_section("Core Modules", core_modules)
    content += add_module_section("Extension Modules", extension_modules)
    content += add_module_section("Tooling", tooling_modules)
    content += add_module_section("Examples", example_modules)
    
    # Add any modules not in predefined categories
    all_categorized = core_modules + extension_modules + tooling_modules + example_modules
    uncategorized = [m for m in modules if m not in all_categorized]
    if uncategorized:
        content += "### Other Modules\n"
        for module in uncategorized:
            content += f"- **{module}**\n"
        content += "\n"
    
    content += """## Directory Structure

```
java-operator-sdk/
├── .github/                    # GitHub workflows and templates
│   ├── workflows/             # CI/CD workflows
│   └── ISSUE_TEMPLATE/        # Issue templates
├── adr/                       # Architecture Decision Records
├── docs/                      # Documentation (Hugo-based)
"""
    
    for module in modules:
        content += f"├── {module + '/':<30} # {module_descriptions.get(module, 'Module')}\n"
    
    content += """├── pom.xml                    # Root POM
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

"""
    
    for workflow in workflows:
        # Remove .yml/.yaml extension for display
        workflow_name = workflow.replace('.yml', '').replace('.yaml', '')
        content += f"- **{workflow}** - {workflow_name.replace('-', ' ').title()}\n"
    
    content += """
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

Last updated: """ + today + "\n"
    
    return content


def main():
    """Main function"""
    if not os.path.exists('pom.xml'):
        print("Error: This script must be run from the repository root", file=sys.stderr)
        sys.exit(1)
    
    print("Extracting project information...")
    version, modules = extract_modules_from_pom()
    workflows = get_workflows()
    
    print(f"Found {len(modules)} modules")
    print(f"Found {len(workflows)} workflows")
    
    print("\nUpdating AGENTS.md...")
    agents_content = update_agents_md(version, modules, workflows)
    
    with open('AGENTS.md', 'w') as f:
        f.write(agents_content)
    
    print("✅ AGENTS.md updated successfully")
    print("\nNote: CLAUDE.md should be updated manually as it contains specific guidelines")
    print("      that require human review.")


if __name__ == '__main__':
    main()
