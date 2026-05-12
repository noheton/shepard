package de.dlr.shepard.common.openapi;

import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;

/**
 * P4c — `/shepard/doc/openapi/v1.json` shelf filter.
 *
 * <p>Keeps only the upstream-compatible {@code /shepard/api/...} paths.
 * The shelf split is wire-public: {@code /shepard/doc/openapi.json}
 * remains the combined view (unchanged for upstream clients);
 * {@code /shepard/doc/openapi/v1.json} is the upstream-frozen surface
 * in isolation, {@code /shepard/doc/openapi/v2.json} the fork's
 * development surface.
 *
 * <p><b>Path shape.</b> The filter accepts both raw paths
 * ({@code /shepard/api/foo}) and the already-stripped form
 * ({@code /foo}) that {@link de.dlr.shepard.common.filters.ApiPathFilter}
 * produces at build time. Concretely, a v1 path is any path that is
 * <em>not</em> a {@code /v2/} path and <em>not</em> a platform path
 * ({@code /healthz}, {@code /openapi}, {@code /swagger-ui},
 * {@code /metrics}). This dual-mode shape keeps the unit fixture
 * (which uses raw {@code /shepard/api/...} entries) honest while also
 * matching the runtime model that {@link ApiPathFilter} has already
 * stripped.
 *
 * <p>Plain {@link OASFilter} — no {@code @OpenApiFilter} annotation so
 * the global build-time pipeline (which produces the combined
 * {@code /openapi.json}) is unaffected. Wired explicitly by
 * {@link OpenApiPerShelfRest}.
 */
public class V1OpenApiFilter implements OASFilter {

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI == null || openAPI.getPaths() == null) return;

    Paths newPaths = OASFactory.createPaths();
    Map<String, PathItem> kept = new LinkedHashMap<>();
    for (Map.Entry<String, PathItem> entry : openAPI.getPaths().getPathItems().entrySet()) {
      if (OpenApiShelfMembership.isV1Path(entry.getKey())) {
        kept.put(entry.getKey(), entry.getValue());
      }
    }
    newPaths.setPathItems(kept);
    openAPI.setPaths(newPaths);
  }
}
