package de.dlr.shepard.v2.shapes.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/shapes/render}.
 *
 * <p>The endpoint is stateless — all state is stored in the
 * {@link de.dlr.shepard.template.entities.ShepardTemplate} referenced by
 * {@code templateAppId}. The {@code focusShepardId} names the DataObject
 * (or other individual) to project through the template's channel bindings.
 *
 * <p>{@code mediaType} is informational — v1 always returns
 * {@code application/json}. Future revisions may honour
 * {@code text/turtle} or {@code application/ld+json} for an RDF-native
 * projection.
 */
@Schema(description = "Request body for POST /v2/shapes/render.")
public record ShapesRenderRequestIO(
  @Schema(
    description = "appId (UUID v7) of the VIEW_RECIPE :ShepardTemplate to render. 404 when not found; 422 when found but templateKind != VIEW_RECIPE.",
    required = true
  )
  String templateAppId,

  @Schema(
    description = "appId (UUID v7) of the focus DataObject whose timeseries channels are projected through the recipe's channel bindings.",
    required = true
  )
  String focusShepardId,

  @Schema(
    description = "Desired response media type. Currently ignored — always returns application/json. Reserved for future Turtle/JSON-LD output.",
    nullable = true,
    defaultValue = "application/json"
  )
  String mediaType
) {}
