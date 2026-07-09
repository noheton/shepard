package de.dlr.shepard.v2.provenance.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Response from {@code GET /v2/data-objects/{appId}/activities/count}. */
@Schema(description = "Count of provenance activity records for an entity.")
public record ActivityCountIO(long count) {}
