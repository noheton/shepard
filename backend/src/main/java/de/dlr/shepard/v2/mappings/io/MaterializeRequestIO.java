package de.dlr.shepard.v2.mappings.io;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-B3 — request body for
 * {@code POST /v2/mappings/{templateAppId}/materialize}.
 *
 * <p>The {@code templateAppId} is a path parameter (not in the body). The body
 * carries the <em>input reference appId bindings</em>: a map from a binding role
 * (declared by the MAPPING_RECIPE shape, e.g. {@code "srcFileAppId"},
 * {@code "urdfFileAppId"}) to the reference {@code appId} (UUID v7) the user
 * picked. Per the CLAUDE.md "UI never asks for paths/URLs" rule, callers pass
 * reference appIds only — the backend resolves them server-side.
 *
 * @param inputReferenceAppIds binding-role → reference appId; never paths/URLs.
 *                             May be empty for a recipe with no required inputs.
 */
@Schema(description = "Request body for POST /v2/mappings/{templateAppId}/materialize.")
public record MaterializeRequestIO(
  @Schema(
    description = "Binding-role → input reference appId (UUID v7). Keys are the roles the " +
    "MAPPING_RECIPE shape declares (e.g. srcFileAppId, urdfFileAppId). Reference appIds only — " +
    "never paths or URLs; the backend resolves them server-side.",
    nullable = true
  )
  Map<String, String> inputReferenceAppIds
) {}
