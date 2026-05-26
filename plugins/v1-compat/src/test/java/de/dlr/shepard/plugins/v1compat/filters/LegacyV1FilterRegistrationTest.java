package de.dlr.shepard.plugins.v1compat.filters;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.vertx.http.runtime.filters.Filters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — regression test that pins the Vert.x {@code @Observes Filters}
 * registration pattern on both filter classes.
 *
 * <p>Background: the symptom of the original defect is that filter classes
 * from plugin JARs are silently absent at runtime — no error is thrown, the
 * JAR is present, but the filter never fires. Two mechanisms were tried and
 * both fail silently from plugin JARs:
 *
 * <ul>
 *   <li>{@code @Provider ContainerRequestFilter} — processed by Quarkus's
 *       build-time JAX-RS scanner which only covers the main application
 *       JAR.</li>
 *   <li>{@code @ServerRequestFilter} / {@code @ServerResponseFilter} —
 *       also build-time scanned; same gap.</li>
 * </ul>
 *
 * <p>The fix: use Quarkus's Vert.x {@link Filters} CDI event. A method
 * annotated with {@code void registerFilter(@Observes Filters filters)} in
 * any {@code @ApplicationScoped} CDI bean — including beans from plugin JARs
 * indexed via {@code quarkus.index-dependency.*} — is called at runtime when
 * Quarkus fires the {@link Filters} event. CDI event observation is
 * JAR-origin-agnostic.
 *
 * <p>This test pins the fix:
 * <ul>
 *   <li>Both filter classes must be {@link ApplicationScoped} CDI beans.</li>
 *   <li>Both must have a method that accepts a single {@link Filters}
 *       parameter annotated with {@link Observes} (i.e., a CDI observer
 *       method for the Filters event).</li>
 * </ul>
 *
 * <p>If either class loses {@code @ApplicationScoped} or the observer method,
 * the live deploy silently regresses and this test fails fast in
 * {@code mvn verify}.
 */
class LegacyV1FilterRegistrationTest {

  @Test
  void gateFilter_isApplicationScopedCdiBean() {
    assertThat(LegacyV1GateFilter.class.isAnnotationPresent(ApplicationScoped.class))
      .as("LegacyV1GateFilter must be @ApplicationScoped so CDI includes it in bean scanning " +
          "and the @Observes Filters method is invoked at runtime from the plugin JAR.")
      .isTrue();
  }

  @Test
  void gateFilter_hasObservesFiltersMethod() {
    assertThat(hasObservesFiltersMethod(LegacyV1GateFilter.class))
      .as("LegacyV1GateFilter must have a void method with an @Observes Filters parameter. " +
          "This is the ONLY filter registration mechanism that works from plugin JARs. " +
          "Replacing this with @Provider, @ServerRequestFilter, or @Priority annotations " +
          "silently breaks the 410 gate on live deploys (see v1-compat-live-validation.md).")
      .isTrue();
  }

  @Test
  void deprecationFilter_isApplicationScopedCdiBean() {
    assertThat(LegacyV1DeprecationFilter.class.isAnnotationPresent(ApplicationScoped.class))
      .as("LegacyV1DeprecationFilter must be @ApplicationScoped so CDI keeps the bean.")
      .isTrue();
  }

  @Test
  void deprecationFilter_hasObservesFiltersMethod() {
    assertThat(hasObservesFiltersMethod(LegacyV1DeprecationFilter.class))
      .as("LegacyV1DeprecationFilter must have a void method with an @Observes Filters parameter. " +
          "Removing this silently breaks Deprecation/Link/X-Shepard-Legacy headers " +
          "on live deploys (see v1-compat-live-validation.md).")
      .isTrue();
  }

  // ─── helper ──────────────────────────────────────────────────────────────

  /**
   * Returns {@code true} if {@code clazz} declares a method whose first
   * (and only) parameter is of type {@link Filters} and carries the
   * {@link Observes} annotation — i.e., a valid CDI observer for the
   * Quarkus Vert.x Filters event.
   */
  private static boolean hasObservesFiltersMethod(Class<?> clazz) {
    for (Method m : clazz.getDeclaredMethods()) {
      if (m.getParameterCount() != 1) continue;
      if (!m.getParameterTypes()[0].equals(Filters.class)) continue;
      Annotation[] paramAnnotations = m.getParameterAnnotations()[0];
      for (Annotation a : paramAnnotations) {
        if (a.annotationType().equals(Observes.class)) return true;
      }
    }
    return false;
  }
}
