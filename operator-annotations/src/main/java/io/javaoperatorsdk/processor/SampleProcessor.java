package io.javaoperatorsdk.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.*;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Annotation processor that generates a markdown file listing all classes annotated with @Sample.
 */
@SupportedAnnotationTypes("io.javaoperatorsdk.annotation.Sample")
public class SampleProcessor extends AbstractProcessor {

    private record SampleInfo(String tldr, String description) {}
    private final List<SampleInfo> samples = new ArrayList<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Types types = processingEnv.getTypeUtils();
        for (TypeElement annotation: annotations) {
            // element has details about the class being annotated, but not the values
            // ex: String tldr = ..., it knows it has a field called tldr but not what's assigned
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                // a mirror gives access to the values assigned to the fields of the annotation
                // element.getAnnotation does not work since the retention is SOURCE
                AnnotationMirror annotationMirror = element.getAnnotationMirrors().stream()
                        .filter(am -> types.isSameType(am.getAnnotationType(), annotation.asType()))
                        .findFirst()
                        .orElse(null);

                if (annotationMirror != null) {
                    String tldr = getString(annotationMirror.getElementValues(), "tldr");
                    String description = getString(annotationMirror.getElementValues(), "description");

                    samples.add(new SampleInfo(tldr, description) );
                }
            }
        }

        if (roundEnv.processingOver()) {
            // sort to keep the order stable
            samples.sort(Comparator.comparing(SampleInfo::tldr, String.CASE_INSENSITIVE_ORDER));
            writeSampleMDFile(samples);
        }
        return false;
    }

    /**
     *
     */
    private void writeSampleMDFile(List<SampleInfo> samples) {
        try {
            FileObject fileObject = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", "samples.md");

            try(Writer writer = fileObject.openWriter();) {
                writer.write("# Integration Test Samples \n");

                for (SampleInfo sample : samples) {
                    writer.write("## " + sample.tldr() + "\n");
                    writer.write(sample.description() + "\n\n");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts a string value from the annotation values map.
     * @param vals the map of annotation values
     * @param name the name of the field to extract
     * @return the string value, or empty string if not found
     */
    private String getString(
            Map<? extends ExecutableElement, ? extends AnnotationValue> vals, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> ev : vals.entrySet()) {
            if (ev.getKey().getSimpleName().contentEquals(name)) {
                Object value = ev.getValue().getValue();
                return value == null ? "" : value.toString();
            }
        }
        // should not happen since tldr and description are mandatory
        return "";
    }
}
