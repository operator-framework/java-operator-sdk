package io.javaoperatorsdk.operator.springboot.starter.test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;
import org.springframework.context.annotation.Import;

@Retention(RetentionPolicy.RUNTIME)
@Import(TestConfiguration.class)
@PropertyMapping("javaoperatorsdk.test")
public @interface EnableMockOperator {

  /**
   * Define a list of files that contain CustomResourceDefinitions for the tested operator. If the
   * file to be loaded is shall be loaded from the classpath prefix it with 'classpath', otherwise
   * provide a path relative to the current working directory.
   *
   * @return List of files
   */
  @PropertyMapping("crd-paths")
  String[] crdPaths() default {};
}
