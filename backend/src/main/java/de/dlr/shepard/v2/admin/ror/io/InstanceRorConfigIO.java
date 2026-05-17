package de.dlr.shepard.v2.admin.ror.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;

/**
 * ROR1 — JSON shape returned by {@code GET/PATCH /v2/admin/instance/ror}.
 *
 * <p>{@code rorUrl} is computed as {@code "https://ror.org/" + rorId}
 * when {@code rorId} is non-null, and {@code null} otherwise. It is
 * never stored in Neo4j — only {@link InstanceRorConfig#getRorId()} and
 * {@link InstanceRorConfig#getOrganizationName()} are persisted.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so optional fields
 * ({@code rorId}, {@code organizationName}, {@code rorUrl}) are
 * omitted when not configured.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstanceRorConfigIO(
  String rorId,
  String organizationName,
  String rorUrl
) {
  /** Base URL for all ROR identifiers. */
  public static final String ROR_BASE_URL = "https://ror.org/";

  /**
   * Project an {@link InstanceRorConfig} entity onto the IO,
   * computing {@code rorUrl} from the stored {@code rorId}.
   */
  public static InstanceRorConfigIO from(InstanceRorConfig cfg) {
    String id = cfg.getRorId();
    String url = id != null ? ROR_BASE_URL + id : null;
    return new InstanceRorConfigIO(id, cfg.getOrganizationName(), url);
  }
}
