package de.dlr.shepard.plugins.v1compat.filters;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — regression test for the live-validation defect 1
 * (see {@code aidocs/agent-findings/v1-compat-live-validation.md}).
 *
 * <p>When the plugin JAR is loaded by the backend via
 * {@code quarkus.index-dependency.shepard-plugin-v1-compat.*}, Quarkus's
 * build-time bean-removal pass drops any {@code @Provider} class that
 * isn't also CDI-scoped (because the Arc container can't see a
 * reference path to it from the rest of the application's beans).
 * The symptom is silent: the filter class is present in the JAR,
 * {@code @Path} resources from the same JAR are scanned, but the
 * filter is never registered and request/response interception never
 * fires — so the deprecation headers and the 410 gate become dead code
 * on a live deploy.
 *
 * <p>This regression test pins the fix: both filters must carry
 * <b>both</b> {@link Provider} and {@link ApplicationScoped} as a
 * class-level annotation pair. If either is removed, the live deploy
 * silently regresses and this test fails fast in {@code mvn verify}.
 *
 * <p>Why this test exists (not just integration coverage): the
 * existing filter unit tests directly instantiate the filter class
 * with a test-seam constructor and call {@code filter()} — they
 * bypass CDI entirely and pass cleanly even when the JAR-load
 * registration is broken. Only an annotation-level pin catches the
 * regression at unit-test scope.
 */
class LegacyV1FilterRegistrationTest {

  @Test
  void deprecationFilter_carriesProviderAndApplicationScoped() {
    Class<?> cls = LegacyV1DeprecationFilter.class;
    assertThat(cls.isAnnotationPresent(Provider.class))
      .as("LegacyV1DeprecationFilter must be @Provider so JAX-RS picks it up")
      .isTrue();
    assertThat(cls.isAnnotationPresent(ApplicationScoped.class))
      .as(
        "LegacyV1DeprecationFilter must be @ApplicationScoped so Arc keeps " +
        "the bean when the plugin JAR is loaded via index-dependency. " +
        "Removing this annotation silently breaks the deprecation headers " +
        "on live deploys (see v1-compat-live-validation.md, Verification 1)."
      )
      .isTrue();
  }

  @Test
  void gateFilter_carriesProviderAndApplicationScoped() {
    Class<?> cls = LegacyV1GateFilter.class;
    assertThat(cls.isAnnotationPresent(Provider.class))
      .as("LegacyV1GateFilter must be @Provider so JAX-RS picks it up")
      .isTrue();
    assertThat(cls.isAnnotationPresent(ApplicationScoped.class))
      .as(
        "LegacyV1GateFilter must be @ApplicationScoped so Arc keeps the bean " +
        "when the plugin JAR is loaded via index-dependency. Removing this " +
        "annotation silently breaks the 410 gate on live deploys (see " +
        "v1-compat-live-validation.md, Verification 3)."
      )
      .isTrue();
  }
}
