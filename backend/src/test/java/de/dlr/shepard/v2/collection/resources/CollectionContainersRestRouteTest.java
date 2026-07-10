package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

/**
 * C2 / task #241 — route-reachability regression test for
 * {@code GET /v2/collections/{collectionAppId}/referenced-containers}.
 *
 * <p><b>Why this test exists.</b> Task #241 reported a 404 from the
 * frontend composable {@code useFetchCollectionContainers.ts} (which
 * calls the generated client's {@code listReferencedContainers}). On
 * 2026-05-30 a deployed-runtime probe confirmed the endpoint IS reachable
 * (returns 401 unauthenticated, not 404), so the report's root cause was
 * a stale deployed image / cached client at the reporter's site, not a
 * missing route. This test prevents a future rename or path drift from
 * silently breaking the frontend without surfacing in CI.
 *
 * <p>The path is exercised here against three sources of truth:
 * <ol>
 *   <li>the backend annotation on {@link CollectionContainersRest},</li>
 *   <li>the generated TypeScript client at
 *       {@code backend-client/src/apis/CollectionContainersApi.ts}
 *       (literal string — copy-checked when regenerating clients), and</li>
 *   <li>the frontend composable at
 *       {@code frontend/composables/context/useFetchCollectionContainers.ts}
 *       (which calls {@code listReferencedContainers({collectionAppId})}).</li>
 * </ol>
 *
 * <p>If any one of the three drifts, the others need to move with it; this
 * test catches the backend half.
 */
class CollectionContainersRestRouteTest {

  /** The canonical path the frontend, generated client, and backend must agree on. */
  static final String EXPECTED_PATH = "/v2/collections/{appId}/referenced-containers";

  @Test
  void routePathIsStable() {
    Path path = CollectionContainersRest.class.getAnnotation(Path.class);
    assertThat(path)
      .as("CollectionContainersRest must carry an @Path annotation")
      .isNotNull();
    assertThat(path.value())
      .as("@Path must equal the canonical CC2 path the frontend calls; if you rename this, "
        + "you MUST regenerate the TypeScript client AND update "
        + "useFetchCollectionContainers.ts.")
      .isEqualTo(EXPECTED_PATH);
  }
}
