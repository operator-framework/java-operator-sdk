package io.javaoperatorsdk.annotation;

import java.lang.annotation.*;

/**
 * This annotation marks an integration test class as a sample for the documentation.
 * Intended for use on test classes only.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * @Sample(
 *  tldr="Usage of PrimaryToSecondaryMapper",
 *  description="Showcases the usage of PrimaryToSecondaryMapper, in what situation it needs to be used and how to optimize typical uses with Informer indexes."
 * )
 * class PrimaryToSecondaryIT {
 *   // details omitted
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Sample {
    String tldr();
    String description();
}