package de.dlr.shepard.common.openapi;

import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;

/**
 * P4c — `/shepard/doc/openapi/v2.json` shelf filter.
 *
 * <p>Keeps only the {@code /v2/...} paths — this fork's development
 * surface (P-series, R-series additive endpoints, U-series profile,
 * J1 lab journal, G1 git credentials, T1 templates, etc.). Companion
 * to {@link V1OpenApiFilter}; see that class for the rationale on the
 * shelf split.
 *
 * <p>Platform paths ({@code /healthz}, {@code /openapi}, ...) are
 * excluded — they are not part of any API shelf and live on neither
 * v1 nor v2 client.
 *
 * <p>Plain {@link OASFilter} — no {@code @OpenApiFilter} annotation so
 * the global build-time pipeline (which produces the combined
 * {@code /openapi.json}) is unaffected. Wired explicitly by
 * {@link OpenApiPerShelfRest}.
 */
public class V2OpenApiFilter implements OASFilter {

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI == null || openAPI.getPaths() == null) return;

    Paths newPaths = OASFactory.createPaths();
    Map<String, PathItem> kept = new LinkedHashMap<>();
    for (Map.Entry<String, PathItem> entry : openAPI.getPaths().getPathItems().entrySet()) {
      if (OpenApiShelfMembership.isV2Path(entry.getKey())) {
        kept.put(entry.getKey(), entry.getValue());
      }
    }
    newPaths.setPathItems(kept);
    openAPI.setPaths(newPaths);
  }
}
