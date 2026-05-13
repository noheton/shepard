package de.dlr.shepard.context.references.git.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/data-objects/{do}/git-references/{ref}/preview}
 * (G1b mode-(b) tracked-artifact preview).
 *
 * <p>Two terminal states:
 * <ul>
 *   <li>{@code available=false} + {@code reason} — the server skipped the
 *       fetch because (e.g.) the caller has no PAT for the host, or the
 *       reference is mode-(a) loose-link, or the host has no adapter.
 *       Surfaced as 200 (NOT 4xx) so the UI can render the explanation
 *       inline without surfacing it as an error.</li>
 *   <li>{@code available=true} — {@code sha}, {@code mimeType}, {@code byteSize},
 *       optionally {@code content} (UTF-8 string), {@code contentTruncated}
 *       indicates the file was too large to inline and the client should
 *       fall back to "open in GitLab".</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@Schema(name = "GitArtifactPreview")
public class GitArtifactPreviewIO {

  @Schema(description = "True if the preview was fetched successfully.", required = true)
  private boolean available;

  @Schema(
    nullable = true,
    description = "When available=false, a short machine-readable reason: " +
    "no-credential / unsupported-host / not-tracked / fetch-failed."
  )
  private String reason;

  @Schema(nullable = true, description = "Commit SHA that fulfilled `ref` at the fetch.")
  private String sha;

  @Schema(nullable = true, description = "MIME type as reported by the git host (charset suffix stripped).")
  private String mimeType;

  @Schema(nullable = true, description = "Total file size in bytes as reported by the git host.")
  private Long byteSize;

  @Schema(nullable = true, description = "UTF-8 file content. Null when contentTruncated=true or available=false.")
  private String content;

  @Schema(
    description = "True if the file exceeds shepard.git.preview.max-bytes (default 1 MB); content is null and the client should fall back to an `open in <host>` link."
  )
  private boolean contentTruncated;
}
