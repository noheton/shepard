package de.dlr.shepard.spi.ai;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated plugin class requires a specific
 * {@link AiCapability} slot to be configured.
 *
 * <p>When {@code hardDep = true} (the default), the plugin's
 * {@code PluginManifest.onRegister()} should refuse to activate if the
 * capability is not available — log a clear error and throw.
 *
 * <p>When {@code hardDep = false}, the feature that uses the capability
 * is silently disabled at runtime; the plugin still activates.
 *
 * <p>Usage example:
 * <pre>{@code
 * @RequiresAiCapability(capability = AiCapability.TEXT, hardDep = true)
 * @RequiresAiCapability(capability = AiCapability.IMAGE_GEN, hardDep = false)
 * public class WikiWriterPluginManifest implements PluginManifest { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(RequiresAiCapability.List.class)
public @interface RequiresAiCapability {

  AiCapability capability();

  /** {@code true} → plugin fails to start if capability unavailable. */
  boolean hardDep() default true;

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface List {
    RequiresAiCapability[] value();
  }
}
