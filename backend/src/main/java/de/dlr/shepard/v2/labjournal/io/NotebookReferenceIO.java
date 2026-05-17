package de.dlr.shepard.v2.labjournal.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * J1b — wire shape for a single {@code .ipynb} file reference returned by
 * {@code GET /v2/lab-journal/{dataObjectAppId}/notebooks}.
 *
 * <p>Covers two source shapes:
 * <ul>
 *   <li>{@link ReferenceKind#SINGLETON} — the file lives in a FR1b
 *       {@link de.dlr.shepard.context.references.file.entities.FileReference}
 *       singleton. {@link #appId} is the singleton's own appId.</li>
 *   <li>{@link ReferenceKind#BUNDLE_FILE} — the file lives inside a FR1a
 *       {@link de.dlr.shepard.context.references.file.entities.FileBundleReference}.
 *       {@link #appId} is the bundle's appId (the addressable
 *       {@code :FileReference} node a client can reach via
 *       {@code /shepard/api/.../fileReferences/{id}} or
 *       {@code /v2/bundles/{appId}}). The {@link #fileName} field
 *       identifies which file inside the bundle is the notebook.</li>
 * </ul>
 *
 * <p>{@code mimeType} is always {@code "application/x-ipynb+json"} for
 * every row this endpoint emits (all results are {@code .ipynb} files).
 *
 * <p>{@code createdAt} and {@code createdBy} come from the parent
 * Reference entity (singleton or bundle), not the underlying
 * {@link de.dlr.shepard.data.file.entities.ShepardFile} — consistent with
 * what admin UIs display on the reference card.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "NotebookReference", description = "A .ipynb file reference attached to a DataObject.")
public class NotebookReferenceIO {

  /**
   * Differentiates the two backing storage shapes. Clients that need
   * to download the bytes use this to pick the correct v2 endpoint:
   * singleton → {@code GET /v2/files/{appId}/content};
   * bundle file → {@code GET /shepard/api/.../fileReferences/{bundleId}/...}.
   */
  public enum ReferenceKind {
    /** File is a FR1b singleton — addressed directly by {@code appId}. */
    SINGLETON,
    /** File is a member of a FR1a bundle — {@code appId} addresses the bundle. */
    BUNDLE_FILE,
  }

  /** AppId of the parent Reference (singleton or bundle). */
  @Schema(readOnly = true, required = true, description = "AppId of the parent Reference (singleton or bundle).")
  private String appId;

  /** Original filename of the notebook file (ends with {@code .ipynb}). */
  @Schema(readOnly = true, required = true, description = "Filename of the notebook (ends with .ipynb).")
  private String fileName;

  /** Payload size in bytes; {@code null} for files uploaded before FB1a. */
  @Schema(readOnly = true, nullable = true, description = "Payload size in bytes. Null for pre-FB1a uploads.")
  private Long fileSize;

  /**
   * Always {@code "application/x-ipynb+json"} — the canonical IANA
   * media type for Jupyter notebook files.
   */
  @Schema(readOnly = true, nullable = true, description = "MIME type of the notebook.")
  private String mimeType;

  /** Wall-clock creation time of the parent Reference. */
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(
    readOnly = true,
    nullable = true,
    format = "date-time",
    example = "2024-08-15T11:18:44.632+00:00",
    description = "Creation time of the parent Reference."
  )
  private Date createdAt;

  /** Display name of the user who created the parent Reference. */
  @Schema(readOnly = true, nullable = true, description = "Creator of the parent Reference.")
  private String createdBy;

  /** Backing storage shape. */
  @Schema(readOnly = true, required = true, description = "SINGLETON or BUNDLE_FILE.")
  private ReferenceKind referenceKind;
}
