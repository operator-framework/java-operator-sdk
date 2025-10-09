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

import org.junit.jupiter.api.Test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

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
  }
}
