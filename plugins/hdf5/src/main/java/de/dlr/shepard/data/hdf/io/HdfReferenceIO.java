package de.dlr.shepard.data.hdf.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.data.hdf.entities.HdfReference;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A5c — outbound wire shape for {@link HdfReference} responses.
 *
 * <p>Carries the server-minted {@link #appId}, the target
 * {@link #hdfContainerAppId}, the {@link #datasetPath}, and the
 * optional {@link #description}. The underlying DataObject appId is
 * not repeated here — it's already in the URL
 * ({@code /v2/data-objects/{dataObjectAppId}/hdf-references}).
 */
@Data
@NoArgsConstructor
@Schema(name = "HdfReference")
public class HdfReferenceIO {

  /**
   * Server-minted UUID v7 identifying this reference. Read-only on
   * the wire.
   */
  @Schema(readOnly = true)
  private String appId;

  /**
   * UUID v7 of the {@code HdfContainer} this reference points into.
   */
  private String hdfContainerAppId;

  /**
   * HDF5 dataset path within the container.
   */
  private String datasetPath;

  /**
   * Optional free-form description.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String description;

  public HdfReferenceIO(HdfReference ref) {
    this.appId = ref.getAppId();
    this.datasetPath = ref.getDatasetPath();
    this.description = ref.getDescription();
    if (ref.getHdfContainer() != null) {
      this.hdfContainerAppId = ref.getHdfContainer().getAppId();
    }
  }

  /**
   * Helper to map a list of entities to their IOs in one pass.
   */
  public static List<HdfReferenceIO> fromEntities(List<HdfReference> entities) {
    if (entities == null) return List.of();
    return entities.stream().map(HdfReferenceIO::new).collect(Collectors.toList());
  }
}
