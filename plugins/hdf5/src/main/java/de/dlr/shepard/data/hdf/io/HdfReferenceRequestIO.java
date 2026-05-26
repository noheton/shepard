package de.dlr.shepard.data.hdf.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A5c — inbound (POST) wire shape for creating a new
 * {@link de.dlr.shepard.data.hdf.entities.HdfReference}.
 *
 * <p>The caller supplies:
 * <ul>
 *   <li>{@link #hdfContainerAppId} — UUID v7 of the target
 *       {@code HdfContainer} (required).</li>
 *   <li>{@link #datasetPath} — HDF5 path within that container,
 *       e.g. {@code "/sensor_data/channel_A"} (required).</li>
 *   <li>{@link #description} — free-form text, optional.</li>
 * </ul>
 *
 * <p>The server mints the {@code appId} and sets {@code createdAt};
 * neither field is accepted from the caller.
 */
@Data
@NoArgsConstructor
@Schema(name = "HdfReferenceRequest")
public class HdfReferenceRequestIO {

  /**
   * UUID v7 of the {@code HdfContainer} this reference points into.
   * Required.
   */
  @Schema(required = true, description = "appId of the target HdfContainer (UUID v7).")
  private String hdfContainerAppId;

  /**
   * HDF5 dataset path within the container, e.g.
   * {@code "/sensor_data/channel_A"}. Required; must be non-blank.
   */
  @Schema(required = true, description = "HDF5 dataset path within the container (e.g. \"/sensor_data/channel_A\").")
  private String datasetPath;

  /**
   * Optional free-form description shown alongside the reference
   * in the UI.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Optional description.")
  private String description;
}
