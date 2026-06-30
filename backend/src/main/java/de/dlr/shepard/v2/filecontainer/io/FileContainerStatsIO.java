package de.dlr.shepard.v2.filecontainer.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * UI21-SIZEBAR-DATA — cardinality stats for a FileContainer.
 *
 * <p>{@code fileCount} is the number of {@link de.dlr.shepard.data.file.entities.ShepardFile}
 * nodes attached to the container. Used by the /containers list sizebar to display a
 * domain-meaningful scale indicator instead of the CC1e referenced-by proxy.
 */
@Schema(name = "FileContainerStats")
public record FileContainerStatsIO(
  @Schema(description = "Number of files stored in this container.", required = true)
  long fileCount
) {}
