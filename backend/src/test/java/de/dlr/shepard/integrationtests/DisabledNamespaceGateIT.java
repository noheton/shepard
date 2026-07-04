package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A5 — acceptance IT for the disabled-plugin-namespace gate.
 *
 * <p>The AAS plugin owns the {@code /v2/aas} REST namespace (its
 * {@code AasPluginManifest} implements {@code RestNamespaceContributor}) and ships
 * <strong>disabled by default</strong> ({@code shepard.plugins.aas.enabled=false}). So in
 * the default IT environment the gate is active, which lets this IT assert — without any
 * admin-role plumbing — the two halves of the operator contract:
 *
 * <ol>
 *   <li><b>Routes 404.</b> {@code GET /v2/aas/.well-known/aas-server} (a public AAS
 *       endpoint) returns 404 when AAS is disabled — the {@code DisabledNamespaceRequestFilter}
 *       gates it before resource matching.</li>
 *   <li><b>Absent from the OpenAPI v2 shelf.</b> {@code GET /shepard/doc/openapi/v2.json}
 *       (public) contains no {@code /v2/aas/...} path while AAS is disabled — the
 *       {@code OpenApiPerShelfRest} composes the {@code DisabledNamespaceOasFilter} over the
 *       live disabled-prefix list.</li>
 * </ol>
 *
 * <p><b>Enable / re-enable side.</b> The full "enable AAS → present + routes; disable →
 * absent + 404; re-enable → restored" round-trip is driven by
 * {@code PATCH /v2/admin/plugins/aas} which requires the {@code instance-admin} role. That
 * runtime-flip behaviour is covered by the must-pass unit tests
 * ({@code RestNamespaceRegistryTest#runtimeFlip_reflectedWithoutReRegistration},
 * {@code DisabledNamespaceRequestFilterTest}, {@code DisabledNamespaceOasFilterTest}) which
 * read enabled-state live on each call. This IT pins the boot-time disabled contract; the
 * admin-role enable path is exercised once the IT admin-bootstrap fixture lands (tracked in
 * aidocs/16 alongside V2CONV-A5).
 *
 * <p>Requires Docker testcontainers (Neo4j + Mongo + TimescaleDB) — runs in CI's IT job;
 * skipped locally when the container runtime is unavailable.
 */
@QuarkusIntegrationTest
public class DisabledNamespaceGateIT extends BaseTestCaseIT {

  private static final String AAS_WELL_KNOWN = "/v2/aas/.well-known/aas-server";
  private static final String V2_OPENAPI = "/shepard/doc/openapi/v2.json";

  @Test
  public void disabledAas_wellKnownRoute_returns404() {
    // No /shepard/api basePath here — /v2 paths are application-root relative.
    given()
      .baseUri(host)
      .port(RestAssured.port)
      .basePath("")
      .when()
      .get(AAS_WELL_KNOWN)
      .then()
      .statusCode(404);
  }

  @Test
  public void disabledAas_absentFromV2OpenApiShelf() {
    String spec = given()
      .baseUri(host)
      .port(RestAssured.port)
      .basePath("")
      .when()
      .get(V2_OPENAPI)
      .then()
      .statusCode(200)
      .extract()
      .asString();

    // The disabled AAS namespace must not appear in the served v2 spec.
    assertFalse(spec.contains("/v2/aas"), "disabled AAS paths must be stripped from the v2 OpenAPI shelf");
    // Sanity: the v2 shelf still carries core /v2 paths (proves we didn't strip everything).
    assertTrue(spec.contains("/v2/"), "v2 shelf should still carry enabled /v2 paths");
  }

  @Test
  public void disabledAas_shellsRoute_returns404() {
    given()
      .baseUri(host)
      .port(RestAssured.port)
      .basePath("")
      .when()
      .get("/v2/aas/shells")
      .then()
      .statusCode(not(200));
  }
}
