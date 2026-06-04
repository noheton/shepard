package de.dlr.shepard.common.openapi;

import de.dlr.shepard.plugin.RestNamespaceRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;

/**
 * V2CONV-A5 — strips, from a served OpenAPI document, every path that sits under a
 * currently-<em>disabled</em> owned namespace ({@code /v2/aas/*} when the AAS plugin is
 * off, {@code /v2/jupyter/*} when Jupyter is off, …).
 *
 * <p>This is a plain {@link OASFilter} — like {@link V1OpenApiFilter} / {@link V2OpenApiFilter}
 * it carries no {@code @OpenApiFilter} annotation, so it is <strong>not</strong> picked up
 * by the build-time SmallRye pipeline. That is deliberate: plugin/feature enabled-state is
 * <em>runtime-mutable</em> (a {@code PATCH /v2/admin/plugins/{id}} flip), whereas the
 * build-time combined spec is computed once. The strip therefore happens at request time,
 * composed by {@link OpenApiPerShelfRest} over the disabled-prefix list the
 * {@link RestNamespaceRegistry} reports <em>on each call</em>. Re-enabling the plugin
 * restores its paths on the next fetch with no restart.
 *
 * <p>The disabled-prefix set is injected (not read from a static), so the filter is unit
 * testable in isolation against a fixed prefix list without booting CDI.
 *
 * @see OpenApiPerShelfRest
 * @see de.dlr.shepard.common.filters.DisabledNamespaceRequestFilter
 */
public class DisabledNamespaceOasFilter implements OASFilter {

  private final List<String> disabledPrefixes;

  /**
   * @param disabledPrefixes application-relative {@code /v2/...} prefixes whose owning
   *     namespace is currently disabled. An empty list makes this filter a no-op.
   */
  public DisabledNamespaceOasFilter(Collection<String> disabledPrefixes) {
    this.disabledPrefixes = disabledPrefixes == null ? List.of() : List.copyOf(disabledPrefixes);
  }

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI == null || openAPI.getPaths() == null || disabledPrefixes.isEmpty()) {
      return;
    }

    Paths newPaths = OASFactory.createPaths();
    Map<String, PathItem> kept = new LinkedHashMap<>();
    for (Map.Entry<String, PathItem> entry : openAPI.getPaths().getPathItems().entrySet()) {
      if (!isUnderDisabledPrefix(entry.getKey())) {
        kept.put(entry.getKey(), entry.getValue());
      }
    }
    newPaths.setPathItems(kept);
    openAPI.setPaths(newPaths);
  }

  /**
   * Whether {@code path} sits under any disabled prefix. Reuses the registry's structural
   * prefix matcher so the request-filter and OpenAPI-strip semantics are identical.
   */
  boolean isUnderDisabledPrefix(String path) {
    for (String prefix : disabledPrefixes) {
      if (RestNamespaceRegistry.matchesPrefix(path, prefix)) {
        return true;
      }
    }
    return false;
  }
}
