package de.dlr.shepard.auth.apikey.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "ApiKey")
public class ApiKeyIO {

  @Schema(readOnly = true, required = true)
  private UUID uid;

  @NotBlank
  @Schema(required = true)
  private String name;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, required = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date createdAt;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(
    required = false,
    nullable = true,
    format = "date-time",
    example = "2025-08-15T11:18:44.632+00:00",
    description = "Optional expiry of this api key. If null, the key never expires."
  )
  private Date validUntil;

  /**
   * A0 §4.2 — explicit role-strings to mint this key with. Each must
   * already be held by the caller (no privilege escalation) and must
   * be in {@code shepard.apikey.role-allowlist} (default
   * {@code ["instance-admin"]}; operator can shrink to {@code []} to
   * forbid role-bearing keys entirely). Empty array (default) preserves
   * today's behaviour — keys without role claims.
   */
  @Schema(
    required = false,
    nullable = true,
    description = "Roles to mint this API key with. Each role must be held by the caller and " +
    "appear in shepard.apikey.role-allowlist. Empty / null = no role claims (default).",
    example = "[\"instance-admin\"]"
  )
  private Set<String> roles = new HashSet<>();

  @Schema(readOnly = true, required = true)
  private String belongsTo;

  public ApiKeyIO(ApiKey key) {
    this.uid = key.getUid();
    this.name = key.getName();
    this.createdAt = key.getCreatedAt();
    this.validUntil = key.getValidUntil();
    this.roles = key.getRoles() == null ? new HashSet<>() : new HashSet<>(key.getRoles());
    this.belongsTo = key.getBelongsTo() != null ? key.getBelongsTo().getUsername() : null;
  }
}
