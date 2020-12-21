package io.javaoperatorsdk.operator.springboot.starter.test;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.context.annotation.Import;

@Retention(RetentionPolicy.RUNTIME)
@Import(TestConfiguration.class)
public @interface EnableMockOperator {

}
