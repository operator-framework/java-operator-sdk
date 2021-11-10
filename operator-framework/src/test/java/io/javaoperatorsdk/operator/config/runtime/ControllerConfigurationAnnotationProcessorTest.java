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
  public void generateDoneableClasswithMultilevelHierarchy() {
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
