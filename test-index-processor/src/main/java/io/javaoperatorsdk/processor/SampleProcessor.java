/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.javaoperatorsdk.annotation.Sample;

/**
 * Annotation processor that generates a samples.md file containing documentation for all
 * integration tests annotated with @Sample.
 */
@SupportedAnnotationTypes("io.javaoperatorsdk.annotation.Sample")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class SampleProcessor extends AbstractProcessor {

  private final List<SampleInfo> samples = new ArrayList<>();
  private boolean processed = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (processed) {
      return true;
    }

    // Collect all @Sample annotated elements
    for (Element element : roundEnv.getElementsAnnotatedWith(Sample.class)) {
      if (element instanceof TypeElement) {
        TypeElement typeElement = (TypeElement) element;
        Sample sample = element.getAnnotation(Sample.class);

        String className = typeElement.getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName().toString();
        String tldr = sample.tldr();
        String description = sample.description();

        samples.add(new SampleInfo(className, simpleName, tldr, description));
      }
    }

    // Generate the markdown file in the last round
    if (roundEnv.processingOver() && !samples.isEmpty()) {
      generateMarkdownFile();
      processed = true;
    }

    return true;
  }

  private void generateMarkdownFile() {
    try {
      // Categorize samples by package
      List<SampleInfo> baseApiSamples = new ArrayList<>();
      List<SampleInfo> dependentSamples = new ArrayList<>();
      List<SampleInfo> workflowSamples = new ArrayList<>();

      for (SampleInfo sample : samples) {
        if (sample.className.contains(".baseapi.")) {
          baseApiSamples.add(sample);
        } else if (sample.className.contains(".dependent.")) {
          dependentSamples.add(sample);
        } else if (sample.className.contains(".workflow.")) {
          workflowSamples.add(sample);
        }
      }

      // Sort each category by class name
      baseApiSamples.sort(Comparator.comparing(s -> s.className));
      dependentSamples.sort(Comparator.comparing(s -> s.className));
      workflowSamples.sort(Comparator.comparing(s -> s.className));

      // Create the samples.md file in the source output location
      FileObject file =
          processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", "samples.md");

      try (BufferedWriter writer = new BufferedWriter(file.openWriter())) {
        writer.write("---\n");
        writer.write("title: Integration Test Index\n");
        writer.write("weight: 85\n");
        writer.write("---\n\n");
        writer.write(
            "This document provides an index of all integration tests annotated with @Sample.\n\n"
                + "These serve also as samples for various use cases. "
                + "You are encouraged to improve both the tests and/or descriptions.\n\n");

        // Generate table of contents
        writer.write("## Contents\n\n");

        if (!baseApiSamples.isEmpty()) {
          writer.write("### Base API\n\n");
          for (SampleInfo sample : baseApiSamples) {
            String anchor = sample.simpleName.toLowerCase();
            writer.write("- [" + sample.tldr + "](#" + anchor + ")\n");
          }
          writer.write("\n");
        }

        if (!dependentSamples.isEmpty()) {
          writer.write("### Dependent Resources\n\n");
          for (SampleInfo sample : dependentSamples) {
            String anchor = sample.simpleName.toLowerCase();
            writer.write("- [" + sample.tldr + "](#" + anchor + ")\n");
          }
          writer.write("\n");
        }

        if (!workflowSamples.isEmpty()) {
          writer.write("### Workflows\n\n");
          for (SampleInfo sample : workflowSamples) {
            String anchor = sample.simpleName.toLowerCase();
            writer.write("- [" + sample.tldr + "](#" + anchor + ")\n");
          }
          writer.write("\n");
        }

        writer.write("---\n\n");

        // Generate Base API section
        if (!baseApiSamples.isEmpty()) {
          writer.write("# Base API\n\n");
          for (SampleInfo sample : baseApiSamples) {
            writeSampleSection(writer, sample);
          }
        }

        // Generate Dependent Resources section
        if (!dependentSamples.isEmpty()) {
          writer.write("# Dependent Resources\n\n");
          for (SampleInfo sample : dependentSamples) {
            writeSampleSection(writer, sample);
          }
        }

        // Generate Workflows section
        if (!workflowSamples.isEmpty()) {
          writer.write("# Workflows\n\n");
          for (SampleInfo sample : workflowSamples) {
            writeSampleSection(writer, sample);
          }
        }
      }

      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.NOTE, "Generated samples.md with " + samples.size() + " samples");
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Failed to generate samples.md: " + e.getMessage());
    }
  }

  private void writeSampleSection(BufferedWriter writer, SampleInfo sample) throws IOException {
    writer.write("## " + sample.simpleName + "\n\n");
    writer.write("**" + sample.tldr + "**\n\n");
    writer.write(sample.description + "\n\n");
    writer.write("**Package:** " + getGitHubPackageLink(sample.className) + "\n\n");
    writer.write("---\n\n");
  }

  private String getGitHubPackageLink(String className) {
    // Extract package name by removing the simple class name
    int lastDot = className.lastIndexOf('.');
    if (lastDot == -1) {
      return "[root package]";
    }

    String packageName = className.substring(0, lastDot);
    String packagePath = packageName.replace('.', '/');

    // GitHub repository base URL
    String baseUrl = "https://github.com/operator-framework/java-operator-sdk/tree/main";
    String sourcePath = "operator-framework/src/test/java";

    String githubUrl = baseUrl + "/" + sourcePath + "/" + packagePath;
    return "[" + packageName + "](" + githubUrl + ")";
  }

  private static class SampleInfo {
    final String className;
    final String simpleName;
    final String tldr;
    final String description;

    SampleInfo(String className, String simpleName, String tldr, String description) {
      this.className = className;
      this.simpleName = simpleName;
      this.tldr = tldr;
      this.description = description;
    }
  }
}
