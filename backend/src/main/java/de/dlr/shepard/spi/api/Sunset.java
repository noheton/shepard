package de.dlr.shepard.spi.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JAX-RS method or resource class as scheduled for removal.
 *
 * <p>Translated to RFC 8594 {@code Sunset} and {@code Deprecation: true}
 * response headers by {@link SunsetFilter}. Applies to either the
 * resource method or its declaring class; the filter walks both.
 *
 * <p>Currently unused — wired up so L2e ({@code aidocs/25 §4 Phase 5})
 * can apply it during the {@code /v1/} long-id-paths deprecation window
 * without first having to ship the annotation. See {@code aidocs/16}
 * row P4 for the rollout plan.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Sunset {
  /**
   * RFC 8594 Sunset date in ISO-8601 / HTTP-date form.
   *
   * <p>The filter passes the value through unchanged into the {@code
   * Sunset} response header — operators are free to use either an
   * ISO-8601 instant ({@code "2027-01-01T00:00:00Z"}) or the IMF-fixdate
   * shape RFC 8594 §3 prefers ({@code "Sat, 31 Dec 2026 23:59:59 GMT"}).
   */
  String date();

  /**
   * Optional URL pointing at a deprecation explainer. When set, emitted
   * as a {@code Link: <url>; rel="sunset"} header alongside {@code
   * Sunset} per RFC 8594 §3.
   */
  String link() default "";
}
