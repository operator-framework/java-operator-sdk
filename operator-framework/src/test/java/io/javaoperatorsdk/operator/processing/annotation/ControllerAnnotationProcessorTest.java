package io.javaoperatorsdk.operator.processing.annotation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjectSubject;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

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

    final JavaFileObject expectedResource =
        JavaFileObjects.forResource(
            "compile-fixtures/ControllerImplemented2InterfacesExpected.java");
    JavaFileObjectSubject.assertThat(compilation.generatedSourceFiles().get(0))
        .hasSourceEquivalentTo(expectedResource);
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

    final JavaFileObject expectedResource =
        JavaFileObjects.forResource(
            "compile-fixtures/ControllerImplementedIntermediateAbstractClassExpected.java");
    JavaFileObjectSubject.assertThat(compilation.generatedSourceFiles().get(0))
        .hasSourceEquivalentTo(expectedResource);
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

    final JavaFileObject expectedResource =
        JavaFileObjects.forResource("compile-fixtures/MultilevelControllerExpected.java");
    JavaFileObjectSubject.assertThat(compilation.generatedSourceFiles().get(0))
        .hasSourceEquivalentTo(expectedResource);
  }
}
