---
title: Contributing
weight: 110
---

Thank you for considering contributing to the Java Operator SDK project! We're building a vibrant community and need help from people like you to make it happen.

## Code of Conduct

We're committed to making this a welcoming, inclusive project. We do not tolerate discrimination, aggressive or insulting behavior.

This project and all participants are bound by our [Code of Conduct]({{baseurl}}/coc). By participating, you're expected to uphold this code. Please report unacceptable behavior to any project admin.

## Reporting Bugs

Found a bug? Please [open an issue](https://github.com/java-operator-sdk/java-operator-sdk/issues)! Include all details needed to recreate the problem:

- Operator SDK version being used
- Exact platform and version you're running on
- Steps to reproduce the bug
- Reproducer code (very helpful for quick diagnosis and fixes)

## Contributing Features and Documentation

Looking for something to work on? Check the issue tracker, especially items labeled [good first issue](https://github.com/java-operator-sdk/java-operator-sdk/labels/good%20first%20issue). Please comment on the issue when you start work to avoid duplicated effort.

### Feature Ideas

Have a feature idea? Open an issue labeled "enhancement" even if you can't work on it immediately. We'll discuss it as a community and see what's possible. 

**Important**: Some features may not align with project goals. Please discuss new features before starting work to avoid wasted effort. We commit to listening to all proposals and working something out when possible.

### Development Process

Once you have approval to work on a feature:
1. Communicate progress via issue updates or our [Discord channel](https://discord.gg/DacEhAy)
2. Ask for feedback and pointers as needed
3. Open a Pull Request when ready

## Pull Request Process

### Commit Messages
Format commit messages following [conventional commit](https://www.conventionalcommits.org/en/v1.0.0/) format.

### Testing and Review
- GitHub Actions will run the test suite on your PR
- All code must pass tests
- New code must include new tests
- All PRs require review and sign-off from another developer
- Expect requests for changes - this is normal and part of the process
- PRs must comply with Java Google code style

### Licensing
All Operator SDK code is released under the [Apache 2.0 licence](LICENSE).

## Development Environment Setup

### Code Style

SDK modules and samples follow Java Google code style. Code gets formatted automatically on every `compile`, but to avoid PR rejections due to style issues, set up your IDE:

**IntelliJ IDEA**: Install the [google-java-format](https://plugins.jetbrains.com/plugin/8527-google-java-format) plugin

**Eclipse**: Follow [these instructions](https://github.com/google/google-java-format?tab=readme-ov-file#eclipse)

## Acknowledgments

These guidelines were inspired by [Atom](https://github.com/atom/atom/blob/master/CONTRIBUTING.md), [PurpleBooth's advice](https://gist.github.com/PurpleBooth/b24679402957c63ec426), and the [Contributor Covenant](https://www.contributor-covenant.org/).
