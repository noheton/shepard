package de.dlr.shepard.common.openapi;

import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.api.util.FilterUtil;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.OpenApiSerializer;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * P4c — exposes the v1 / v2 OpenAPI shelves as derived endpoints over
 * the combined {@code /shepard/doc/openapi.json} document.
 *
 * <p>The combined view served by the Quarkus smallrye-openapi
 * extension at {@code /shepard/doc/openapi.json} remains the canonical
 * doc; upstream-built clients pointing at it keep working. The two
 * endpoints here are <em>additive</em>:
 *
 * <ul>
 *   <li>{@code GET /shepard/doc/openapi/v1.json} — only the upstream-
 *       compatible {@code /shepard/api/...} paths
 *       (post-{@link de.dlr.shepard.common.filters.ApiPathFilter} the
 *       paths render without the {@code /shepard/api} prefix, mirroring
 *       upstream's wire shape).</li>
 *   <li>{@code GET /shepard/doc/openapi/v2.json} — only the fork's
 *       {@code /v2/...} development surface.</li>
 * </ul>
 *
 * <p>Both endpoints honour {@code ?format=yaml} for YAML output
 * ({@code application/yaml}); default is JSON ({@code application/json}).
 * Both are public ({@link PermitAll} on the class) and listed in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry} so the
 * JWT filter lets them through pre-auth — OpenAPI specs are public,
 * mirroring the combined {@code /openapi.json}.
 *
 * <p>The reuse strategy: take the live {@link OpenApiDocument#get()}
 * (the document the smallrye-openapi build pipeline produced, including
 * the {@link de.dlr.shepard.common.filters.ApiPathFilter} prefix-strip),
 * clone it shallowly via {@link io.smallrye.openapi.api.util.MergeUtil},
 * then push through the shelf-appropriate {@link OASFilter}. The clone
 * step is the safety belt: {@link FilterUtil#applyFilter(OASFilter, OpenAPI)}
 * mutates its argument, and the global doc is shared.
 *
 * <p>Lands per <code>aidocs/16</code> P4c; rationale and contract are
 * documented there.
 */
@Path("/shepard/doc/openapi")
@PermitAll
@ApplicationScoped
public class OpenApiPerShelfRest {

  static final String V1_PATH = "/shepard/doc/openapi/v1.json";
  static final String V2_PATH = "/shepard/doc/openapi/v2.json";

  @GET
  @Path("/v1.json")
  @Tag(name = "openapi-per-shelf")
  @Operation(description = "OpenAPI document for the upstream-compatible /shepard/api/... shelf only.")
  public Response getV1Shelf(@QueryParam("format") String format) {
    return serialise(new V1OpenApiFilter(), format);
  }

  @GET
  @Path("/v2.json")
  @Tag(name = "openapi-per-shelf")
  @Operation(description = "OpenAPI document for the /v2/... development shelf only.")
  public Response getV2Shelf(@QueryParam("format") String format) {
    return serialise(new V2OpenApiFilter(), format);
  }

  /**
   * Clone the live OpenAPI document, push through {@code filter},
   * serialise as JSON (default) or YAML (when {@code format=yaml}),
   * return the resulting bytes with the matching media type.
   */
  private Response serialise(OASFilter filter, String format) {
    // smallrye-openapi throws IllegalStateException from get() when the
    // model hasn't been initialised yet; guard via isSet() to surface a
    // proper 500 instead. Defensive — should never happen post-startup.
    if (!OpenApiDocument.INSTANCE.isSet()) {
      throw new InternalServerErrorException("OpenAPI document is not yet initialised.");
    }
    OpenAPI source = OpenApiDocument.INSTANCE.get();
    if (source == null) {
      throw new InternalServerErrorException("OpenAPI document is not yet initialised.");
    }
    OpenAPI clone = io.smallrye.openapi.api.util.MergeUtil.merge(OASFactory.createOpenAPI(), source);
    FilterUtil.applyFilter(filter, clone);
    Format wireFormat = "yaml".equalsIgnoreCase(format) ? Format.YAML : Format.JSON;
    String body;
    try {
      body = OpenApiSerializer.serialize(clone, wireFormat);
    } catch (IOException ex) {
      throw new InternalServerErrorException("Failed to serialise OpenAPI document: " + ex.getMessage(), ex);
    }
    return Response.ok(body).type(mediaTypeFor(wireFormat)).build();
  }

  private static String mediaTypeFor(Format format) {
    return format == Format.YAML ? "application/yaml" : MediaType.APPLICATION_JSON;
  }
}
