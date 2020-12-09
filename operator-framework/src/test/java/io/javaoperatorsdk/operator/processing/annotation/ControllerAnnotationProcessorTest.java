package io.javaoperatorsdk.operator.processing.annotation;

import com.google.testing.compile.*;
import com.google.testing.compile.Compiler;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class ControllerAnnotationProcessorTest {
  @Test
  public void generateCorrectDoneableClassIfInterfaceIsSecond() {
    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerAnnotationProcessor())
            .compile(JavaFileObjects.forResource("ControllerImplemented2Interfaces.java"));
    CompilationSubject.assertThat(compilation).succeeded();

    final JavaFileObject expectedResource =
        JavaFileObjects.forResource("ControllerImplemented2InterfacesExpected.java");
    JavaFileObjectSubject.assertThat(compilation.generatedSourceFiles().get(0))
        .hasSourceEquivalentTo(expectedResource);
  }

  @Test
  public void generateCorrectDoneableClassIfThereIsAbstractBaseController() {

    Compilation compilation =
        Compiler.javac()
            .withProcessors(new ControllerAnnotationProcessor())
            .compile(
                JavaFileObjects.forResource("AbstractController.java"),
                JavaFileObjects.forResource("ControllerImplementedIntermediateAbstractClass.java"));
    CompilationSubject.assertThat(compilation).succeeded();

    final JavaFileObject expectedResource =
        JavaFileObjects.forResource("ControllerImplementedIntermediateAbstractClassExpected.java");
    JavaFileObjectSubject.assertThat(compilation.generatedSourceFiles().get(0))
        .hasSourceEquivalentTo(expectedResource);
  }
}
