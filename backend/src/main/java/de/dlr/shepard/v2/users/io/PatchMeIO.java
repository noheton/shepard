package de.dlr.shepard.v2.users.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * RFC 7396 JSON Merge Patch shape for {@code PATCH /v2/users/me}.
 * Every field is nullable; only fields supplied in the body apply,
 * absent fields stay unchanged on the {@code :User} entity.
 *
 * <p>v1 (U1a) shipped {@code orcid}. U1b adds {@code displayName}.
 * Later U1 sub-slices grow this IO with avatar URL (U1c/U1d), etc.
 */
@Data
@NoArgsConstructor
@Schema(name = "PatchMe")
public class PatchMeIO {

  @Schema(
    nullable = true,
    description = "ORCID identifier, format `NNNN-NNNN-NNNN-NNN[N|X]`. Empty string clears the field; " +
    "malformed input returns 400. Null = leave unchanged.",
    example = "0000-0002-1825-0097"
  )
  private String orcid;

  /**
   * U1b: user-chosen display-name override. Empty string clears
   * (UI then falls back to firstName+lastName, then redacted-username
   * per `aidocs/16 U1b`).
   */
  @Schema(
    nullable = true,
    description = "User-chosen display name. Empty string clears the override (UI falls back to firstName+lastName, " +
    "then redacted-username). Null = leave unchanged."
  )
  private String displayName;
}
