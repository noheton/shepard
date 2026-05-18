package de.dlr.shepard.common.openapi;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;

/**
 * OAS2 — annotate every operation in the COMBINED OpenAPI spec with the
 * API shelf it belongs to, so a reader of the unified Swagger UI can
 * tell at a glance whether a given endpoint is on the upstream-frozen
 * {@code /shepard/api} surface or this fork's {@code /v2/} development
 * shelf.
 *
 * <p>Two writes per operation:
 * <ul>
 *   <li>The {@code summary} is prefixed with {@code [v1]} or {@code [v2]}
 *       — small, inline, visible without expanding the operation.</li>
 *   <li>A vendor extension {@code x-shepard-shelf: v1 | v2 | platform}
 *       is added to the {@link PathItem} so machine readers (the Kiota
 *       generator, custom doc renderers) can branch on it without
 *       string-parsing the summary.</li>
 * </ul>
 *
 * <p>This is the COMBINED-spec analog of the per-shelf split that
 * {@link V1OpenApiFilter} and {@link V2OpenApiFilter} apply when
 * producing the shelf-only documents. The combined spec is what
 * /shepard/doc/openapi.json (and the in-app Swagger UI) serves; users
 * browsing that need a tag of their own. Paths classified as platform
 * ({@code /healthz}, {@code /openapi}, …) get the {@code [platform]}
 * prefix to make their lifecycle promises clear too.
 *
 * <p>Ordering: this filter runs at the BUILD stage AFTER
 * {@link de.dlr.shepard.common.filters.ApiPathFilter} has stripped the
 * {@code /shepard/api} prefix, so the shelf membership check operates
 * on the post-strip path shape. Smallrye runs build-stage filters in
 * order of registration; the explicit
 * {@link OpenApiFilter.RunStage#BUILD} placement keeps the spec stable
 * across rebuilds.
 */
@OpenApiFilter(OpenApiFilter.RunStage.BUILD)
public class ShelfTagFilter implements OASFilter {

  static final String EXT_SHELF = "x-shepard-shelf";

  static final String V1_PREFIX = "[v1] ";
  static final String V2_PREFIX = "[v2] ";
  static final String PLATFORM_PREFIX = "[platform] ";

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI == null || openAPI.getPaths() == null) return;

    for (Map.Entry<String, PathItem> entry : openAPI.getPaths().getPathItems().entrySet()) {
      String path = entry.getKey();
      PathItem item = entry.getValue();
      if (item == null) continue;

      String shelf = classify(path);
      item.addExtension(EXT_SHELF, shelf);

      String prefix = switch (shelf) {
        case "v1" -> V1_PREFIX;
        case "v2" -> V2_PREFIX;
        default -> PLATFORM_PREFIX;
      };

      tagOperation(item.getGET(), prefix);
      tagOperation(item.getPOST(), prefix);
      tagOperation(item.getPUT(), prefix);
      tagOperation(item.getPATCH(), prefix);
      tagOperation(item.getDELETE(), prefix);
      tagOperation(item.getHEAD(), prefix);
      tagOperation(item.getOPTIONS(), prefix);
      tagOperation(item.getTRACE(), prefix);
    }
  }

  private static String classify(String path) {
    if (OpenApiShelfMembership.isV2Path(path)) return "v2";
    if (OpenApiShelfMembership.isV1Path(path)) return "v1";
    return "platform";
  }

  private static void tagOperation(Operation op, String prefix) {
    if (op == null) return;
    String current = op.getSummary();
    if (current == null || current.isBlank()) {
      // Build a fallback summary from the operationId if available, so
      // every operation surfaces SOMETHING in the prefix slot. An
      // operation with no human-readable summary is hard to triage, and
      // [v2] alone reads weirdly.
      String oid = op.getOperationId();
      if (oid != null && !oid.isBlank()) {
        op.setSummary(prefix + oid);
      } else {
        op.setSummary(prefix.trim());
      }
      return;
    }
    if (current.startsWith(V1_PREFIX) || current.startsWith(V2_PREFIX) || current.startsWith(PLATFORM_PREFIX)) {
      // Idempotent: leave already-tagged summaries alone (defensive in
      // case the filter runs twice across hot-reload cycles).
      return;
    }
    op.setSummary(prefix + current);
  }
}
