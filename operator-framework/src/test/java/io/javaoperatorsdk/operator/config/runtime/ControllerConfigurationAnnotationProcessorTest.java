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
package io.javaoperatorsdk.operator.config.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.tools.StandardLocation;

import org.junit.jupiter.api.Test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import static io.javaoperatorsdk.operator.config.runtime.RuntimeControllerMetadata.RECONCILERS_RESOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

class ControllerConfigurationAnnotationProcessorTest {

  @Test
  public void generateCorrectDoneableClassIfInterfaceIsSecond() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerConfigurationAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource(
                    "compile-fixtures/ReconcilerImplemented2Interfaces.java"));
    CompilationSubject.assertThat(compilation).succeeded();
    assertMapping(
        compilation,
        "io.ReconcilerImplemented2Interfaces,io.ReconcilerImplemented2Interfaces.MyCustomResource");
  }

  @Test
  public void generateCorrectDoneableClassIfThereIsAbstractBaseController() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerConfigurationAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource("compile-fixtures/AbstractReconciler.java"),
                JavaFileObjects.forResource(
                    "compile-fixtures/ReconcilerImplementedIntermediateAbstractClass.java"));
    CompilationSubject.assertThat(compilation).succeeded();
    assertMapping(
        compilation,
        "io.ReconcilerImplementedIntermediateAbstractClass,io.AbstractReconciler.MyCustomResource");
  }

  @Test
  public void generateDoneableClassWithMultilevelHierarchy() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerConfigurationAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource("compile-fixtures/AdditionalReconcilerInterface.java"),
                JavaFileObjects.forResource("compile-fixtures/MultilevelAbstractReconciler.java"),
                JavaFileObjects.forResource("compile-fixtures/MultilevelReconciler.java"));
    CompilationSubject.assertThat(compilation).succeeded();
    assertMapping(compilation, "io.MultilevelReconciler,io.MultilevelReconciler.MyCustomResource");
  }

  /**
   * When the reconciled resource is itself generic, the resolved type is a parameterized {@code
   * DeclaredType}. Only its erasure may be written to the mapping resource: {@link
   * ClassMappingProvider} loads the recorded name with {@code ClassUtils.getClass(String)}, which
   * cannot parse type arguments.
   */
  @Test
  public void writesErasureOfGenericResourceType() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerConfigurationAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource("compile-fixtures/GenericResourceReconciler.java"));
    CompilationSubject.assertThat(compilation).succeeded();
    assertMapping(
        compilation,
        "io.GenericResourceReconciler,io.GenericResourceReconciler.MyGenericCustomResource");
    assertLoadableMapping(compilation);
  }

  /**
   * Checks that the generated mapping resource contains the expected {@code
   * reconciler,resource-class} line, using the same fully qualified, dot separated names that
   * {@link ClassMappingProvider} expects to be able to load at runtime.
   */
  private static void assertMapping(Compilation compilation, String expectedMapping) {
    CompilationSubject.assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, RECONCILERS_RESOURCE_PATH)
        .contentsAsUtf8String()
        .contains(expectedMapping);
  }

  /**
   * Checks that every recorded name in the generated mapping resource is a plain binary-ish class
   * name, i.e. one that {@code ClassUtils.getClass(String)} can actually resolve, rather than a
   * generic type signature such as {@code io.Foo<java.lang.String>}.
   */
  private static void assertLoadableMapping(Compilation compilation) {
    final var contents =
        compilation
            .generatedFile(StandardLocation.CLASS_OUTPUT, RECONCILERS_RESOURCE_PATH)
            .map(
                file -> {
                  try {
                    return file.getCharContent(true).toString();
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                })
            .orElseThrow(() -> new AssertionError("no mapping resource was generated"));
    contents
        .lines()
        .filter(line -> !line.isBlank())
        .forEach(
            line -> {
              final var names = line.split(",");
              assertThat(names).as("mapping line '%s'", line).hasSize(2);
              for (String name : names) {
                assertThat(name)
                    .as("recorded class name '%s' must be loadable at runtime", name)
                    .doesNotContain("<")
                    .doesNotContain(">")
                    .doesNotContain(" ")
                    .matches("[\\p{L}_$][\\p{L}\\p{N}_$]*(\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*");
              }
            });
  }
}
