package de.dlr.shepard.common.openapi;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.servers.Server;

/**
 * OAS1 — rewrite the OpenAPI {@code servers} list with the actual
 * publicly-reachable backend URL when one has been configured.
 *
 * <p>The bundled static {@code META-INF/openapi.yaml} hard-codes a
 * relative {@code servers: [{url: /shepard/api}]} entry. Works in the
 * browser-served Swagger UI (resolves against the current origin) but
 * generates broken absolute URLs in any OpenAPI-Generator-produced
 * client that downloads the spec from a non-browser context.
 *
 * <p>This filter runs at the {@code RUN} stage so it can read the
 * runtime config property {@code shepard.public.base-url} (typically
 * set via the {@code SHEPARD_PUBLIC_BASE_URL} env var). When present
 * (e.g. {@code https://shepard-api.example.dlr.de}), it PREPENDS an
 * absolute-URL server entry pointing at {@code <base-url>/shepard/api}
 * and keeps the original relative entry as a fallback. When the
 * property is empty or absent, the spec is unchanged.
 *
 * <p>The value should be the **scheme + host (+ port)** of the public
 * deployment, with no path suffix — the filter appends
 * {@code /shepard/api} itself. The path prefix is intentionally
 * hard-coded because {@link de.dlr.shepard.common.filters.ApiPathFilter}
 * strips it from the path items in the same spec; clients need the
 * prefix on the server URL to reassemble valid request URLs.
 */
@OpenApiFilter(OpenApiFilter.RunStage.RUN)
public class PublicServerUrlFilter implements OASFilter {

  static final String PUBLIC_BASE_URL_KEY = "shepard.public.base-url";
  static final String SERVER_PATH_PREFIX = "/shepard/api";

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI == null) return;
    String baseUrl = readBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) return;

    String trimmed = baseUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }

    String publicUrl = trimmed + SERVER_PATH_PREFIX;
    List<Server> newServers = new ArrayList<>();
    newServers.add(
      OASFactory.createServer()
        .url(publicUrl)
        .description("shepard backend (public deployment)")
    );

    // Preserve any existing entries — they're useful for in-browser
    // Swagger UI behind a reverse proxy on a different hostname.
    List<Server> existing = openAPI.getServers();
    if (existing != null) {
      for (Server s : existing) {
        if (s.getUrl() != null && !s.getUrl().equals(publicUrl)) {
          newServers.add(s);
        }
      }
    }
    openAPI.setServers(newServers);
  }

  private String readBaseUrl() {
    try {
      return ConfigProvider.getConfig()
        .getOptionalValue(PUBLIC_BASE_URL_KEY, String.class)
        .orElse(null);
    } catch (Exception e) {
      return null;
    }
  }
}
