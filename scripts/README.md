# Maintenance Scripts

This directory contains scripts for maintaining the Java Operator SDK repository.

## update-docs.py

Updates the AGENTS.md file with the current project structure.

**Usage:**
```bash
# From the repository root
python3 scripts/update-docs.py
```

**What it does:**
- Extracts module list from `pom.xml`
- Lists all GitHub workflows
- Updates AGENTS.md with current information
- Updates the "Last updated" timestamp

**When to run:**
- After adding or removing Maven modules
- After adding or removing GitHub workflows
- Before committing changes to project structure
- When AGENTS.md becomes outdated

**Note:** CLAUDE.md contains specific guidelines and should be updated manually when coding practices or patterns change.
