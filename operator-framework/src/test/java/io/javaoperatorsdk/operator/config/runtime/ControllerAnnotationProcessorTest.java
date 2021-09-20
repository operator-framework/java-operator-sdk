package io.javaoperatorsdk.operator.config.runtime;

import org.junit.jupiter.api.Test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

class ControllerAnnotationProcessorTest {

  @Test
  public void generateCorrectDoneableClassIfInterfaceIsSecond() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource(
                    "compile-fixtures/ControllerImplemented2Interfaces.java"));
    CompilationSubject.assertThat(compilation).succeeded();
  }

  @Test
  public void generateCorrectDoneableClassIfThereIsAbstractBaseController() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource("compile-fixtures/AbstractController.java"),
                JavaFileObjects.forResource(
                    "compile-fixtures/ControllerImplementedIntermediateAbstractClass.java"));
    CompilationSubject.assertThat(compilation).succeeded();
  }

  @Test
  public void generateDoneableClasswithMultilevelHierarchy() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource("compile-fixtures/AdditionalControllerInterface.java"),
                JavaFileObjects.forResource("compile-fixtures/MultilevelAbstractController.java"),
                JavaFileObjects.forResource("compile-fixtures/MultilevelController.java"));
    CompilationSubject.assertThat(compilation).succeeded();
  }
}
