# Documentation Maintenance Guide

## Overview

This repository includes automated documentation maintenance for AI agents and contributors. The system tracks the project structure and ensures documentation stays up-to-date.

## Files

### AGENTS.md
Contains comprehensive project context for AI agents, including:
- Project overview and structure
- Maven module descriptions
- Directory layout
- Key technologies and dependencies
- Development workflows
- GitHub Actions workflows

**Auto-updated by:** `scripts/update-docs.py`

### CLAUDE.md
Contains specific guidelines for Claude AI assistant, including:
- Code quality standards
- Testing requirements
- Commit message format
- Common patterns and practices
- What to avoid
- Security considerations

**Updated manually** when coding practices change.

## Automation

### GitHub Workflow: maintain-docs.yml

The workflow runs on:
- Pull requests that modify `pom.xml`, workflow files, or documentation files
- Pushes to main branch affecting the same files

**What it does:**
1. Extracts module list from `pom.xml`
2. Lists all GitHub workflows
3. Validates that AGENTS.md mentions all modules
4. Comments on PR if updates are needed

### Update Script: scripts/update-docs.py

Manual script to update AGENTS.md.

**Usage:**
```bash
cd /path/to/java-operator-sdk
python3 scripts/update-docs.py
```

**When to run:**
- After adding/removing Maven modules
- After adding/removing GitHub workflows
- Before committing structural changes
- When AGENTS.md becomes outdated

## Workflow Examples

### Adding a New Maven Module

1. Add the module to `pom.xml`:
   ```xml
   <modules>
     <module>existing-module</module>
     <module>new-module</module>  <!-- New -->
   </modules>
   ```

2. Update AGENTS.md:
   ```bash
   python3 scripts/update-docs.py
   ```

3. Review and commit:
   ```bash
   git add AGENTS.md pom.xml
   git commit -m "feat: add new-module to project"
   ```

4. The GitHub workflow will validate the PR automatically.

### Updating Coding Guidelines

1. Edit CLAUDE.md manually with new guidelines
2. Update the "Last updated" date
3. Commit the changes:
   ```bash
   git add CLAUDE.md
   git commit -m "docs: update coding guidelines in CLAUDE.md"
   ```

### Adding a New GitHub Workflow

1. Create the workflow file in `.github/workflows/`
2. Update AGENTS.md:
   ```bash
   python3 scripts/update-docs.py
   ```
3. Commit both files

## Maintenance Checklist

When making significant project changes, ensure:

- [ ] Root `pom.xml` is updated with module changes
- [ ] `AGENTS.md` is updated (run `scripts/update-docs.py`)
- [ ] `CLAUDE.md` is reviewed for outdated guidelines
- [ ] `README.md` reflects major changes
- [ ] `CONTRIBUTING.md` is updated if development process changes
- [ ] Documentation in `/docs` is updated for user-facing changes

## CI/CD Integration

The `maintain-docs.yml` workflow integrates with your PR process:

1. **Detection**: Automatically runs on relevant file changes
2. **Validation**: Checks documentation consistency
3. **Notification**: Comments on PR if updates needed
4. **Blocking**: Can be configured as a required check (optional)

## Troubleshooting

### Workflow fails: "AGENTS.md needs updates"

Run the update script:
```bash
python3 scripts/update-docs.py
git add AGENTS.md
git commit -m "docs: update AGENTS.md with current structure"
```

### New module not listed in AGENTS.md

The script categorizes modules. If your module doesn't fit existing categories:
1. Run the update script (it will add it to "Other Modules")
2. Manually edit AGENTS.md to categorize it properly
3. Update `scripts/update-docs.py` to recognize the new category

### Script errors on pom.xml parsing

Ensure `pom.xml`:
- Is valid XML
- Has `<modules>` section
- Uses standard Maven structure

## Future Enhancements

Possible improvements:
- Automated CLAUDE.md updates for common patterns
- Integration with PR templates
- Automatic dependency version updates in documentation
- More comprehensive validation checks
- Support for additional documentation formats

## Support

For issues or questions:
- Open a GitHub issue
- Ask on Discord: https://discord.gg/DacEhAy
- Check community meeting schedule in README.md
