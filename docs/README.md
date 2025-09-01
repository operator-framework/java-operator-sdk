# Java Operator SDK Documentation

This repository contains the documentation website for the Java Operator SDK (JOSDK), built using Hugo and the Docsy theme.

## About Java Operator SDK

Java Operator SDK is a framework that makes it easy to build Kubernetes operators in Java. It provides APIs designed to feel natural to Java developers and handles common operator challenges automatically, allowing you to focus on your business logic.

## Development Setup

This documentation site uses Hugo v0.125.7 with the Docsy theme.

## Prerequisites

- Hugo v0.125.7 or later (extended version required)
- Node.js and npm (for PostCSS processing)
- Git

## Local Development

### Quick Start

1. Clone this repository
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   hugo server
   ```
4. Open your browser to `http://localhost:1313`

### Using Docker

You can also run the documentation site using Docker:

1. Build the container:
   ```bash
   docker-compose build
   ```
2. Run the container:
   ```bash
   docker-compose up
   ```
   > **Note**: You can combine both commands with `docker-compose up --build`

3. Access the site at `http://localhost:1313`

To stop the container, press **Ctrl + C** in your terminal.

To clean up Docker resources:
```bash
docker-compose rm
```

## Contributing

We welcome contributions to improve the documentation! Please see our [contribution guidelines](CONTRIBUTING.md) for details on how to get started.

## Troubleshooting

### Module Compatibility Error
If you see an error about module compatibility, ensure you're using Hugo v0.110.0 or higher:
```console
Error: Error building site: failed to extract shortcode: template for shortcode "blocks/cover" not found
```

### SCSS Processing Error
If you encounter SCSS-related errors, make sure you have the extended version of Hugo installed:
```console
Error: TOCSS: failed to transform "scss/main.scss"
```

### Go Binary Not Found
If you see "binary with name 'go' not found", install the Go programming language from [golang.org](https://golang.org).

## Links

- [Hugo Documentation](https://gohugo.io/documentation/)
- [Docsy Theme Documentation](https://www.docsy.dev/docs/)
- [Java Operator SDK GitHub Repository](https://github.com/operator-framework/java-operator-sdk)
