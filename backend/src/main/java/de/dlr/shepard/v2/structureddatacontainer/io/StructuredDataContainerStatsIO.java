package de.dlr.shepard.v2.structureddatacontainer.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * UI21-SIZEBAR-DATA — cardinality stats for a StructuredDataContainer.
 *
 * <p>{@code entryCount} is the number of {@link de.dlr.shepard.data.structureddata.entities.StructuredData}
 * nodes attached to the container. Used by the /containers list sizebar to display a
 * domain-meaningful scale indicator instead of the CC1e referenced-by proxy.
 */
@Schema(name = "StructuredDataContainerStats")
public record StructuredDataContainerStatsIO(
  @Schema(description = "Number of structured-data entries stored in this container.", required = true)
  long entryCount
) {}
