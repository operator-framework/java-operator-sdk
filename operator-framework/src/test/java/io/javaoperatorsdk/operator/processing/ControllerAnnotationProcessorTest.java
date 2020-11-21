package io.javaoperatorsdk.operator.processing;

import com.google.testing.compile.*;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

class ControllerAnnotationProcessorTest {
    @Test
    public void generateCorrectDoneableClassIfInterfaceIsSecond() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new ControllerAnnotationProcessor())
                .compile(JavaFileObjects.forResource("ControllerImplemented2Interfaces.java"));
        CompilationSubject.assertThat(compilation).succeeded();

        final JavaFileObject expectedResource = JavaFileObjects.forResource("ControllerImplemented2InterfacesExpected.java");
        JavaFileObjectSubject.assertThat(compilation.generatedSourceFiles().get(0)).hasSourceEquivalentTo(expectedResource);
    }
}
