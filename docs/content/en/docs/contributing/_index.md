---
title: Contributing To Java Operator SDK
weight: 110
---

First of all, we'd like to thank you for considering contributing to the project! We really
hope to create a vibrant community around this project but this won't happen without help from
people like you!

## Code of Conduct

We are serious about making this a welcoming, happy project. We will not tolerate discrimination,
aggressive or insulting behaviour.

To this end, the project and everyone participating in it is bound by the [Code of
Conduct]({{baseurl}}/coc). By participating, you are expected to uphold this code. Please report
unacceptable behaviour to any of the project admins.

## Bugs

If you find a bug,
please [open an issue](https://github.com/java-operator-sdk/java-operator-sdk/issues)! Do try
to include all the details needed to recreate your problem. This is likely to include:

- The version of the Operator SDK being used
- The exact platform and version of the platform that you're running on
- The steps taken to cause the bug
- Reproducer code is also very welcome to help us diagnose the issue and fix it quickly

## Building Features and Documentation

If you're looking for something to work on, take look at the issue tracker, in particular any items
labelled [good first issue](https://github.com/java-operator-sdk/java-operator-sdk/labels/good%20first%20issue)
.
Please leave a comment on the issue to mention that you have started work, in order to avoid
multiple people working on the same issue.

If you have an idea for a feature - whether or not you have time to work on it - please also open an
issue describing your feature and label it "enhancement". We can then discuss it as a community and
see what can be done. Please be aware that some features may not align with the project goals and
might therefore be closed. In particular, please don't start work on a new feature without
discussing it first to avoid wasting effort. We do commit to listening to all proposals and will do
our best to work something out!

Once you've got the go ahead to work on a feature, you can start work. Feel free to communicate with
team via updates on the issue tracker or the [Discord channel](https://discord.gg/DacEhAy) and ask
for feedback, pointers etc. Once you're happy with your code, go ahead and open a Pull Request.

## Pull Request Process

First, please format your commit messages so that they follow
the [conventional commit](https://www.conventionalcommits.org/en/v1.0.0/) format.

On opening a PR, a GitHub action will execute the test suite against the new code. All code is
required to pass the tests, and new code must be accompanied by new tests.

All PRs have to be reviewed and signed off by another developer before being merged. This review
will likely ask for some changes to the code - please don't be alarmed or upset
at this; it is expected that all PRs will need tweaks and a normal part of the process.

The PRs are checked to be compliant with the Java Google code style.

Be aware that all Operator SDK code is released under the [Apache 2.0 licence](LICENSE).

## Development environment setup

### Code style

The SDK modules and samples are formatted to follow the Java Google code style.
On every `compile` the code gets formatted automatically, however, to make things simpler (i.e.
avoid getting a PR rejected simply because of code style issues), you can import one of the
following code style schemes based on the IDE you use:

- for *Intellij IDEA*
  install [google-java-format](https://plugins.jetbrains.com/plugin/8527-google-java-format) plugin
- for *Eclipse*
  follow [these intructions](https://github.com/google/google-java-format?tab=readme-ov-file#eclipse)

## Thanks

These guidelines were based on several sources, including
[Atom](https://github.com/atom/atom/blob/master/CONTRIBUTING.md), [PurpleBooth's
advice](https://gist.github.com/PurpleBooth/b24679402957c63ec426) and the [Contributor
Covenant](https://www.contributor-covenant.org/).
