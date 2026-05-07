package de.dlr.shepard.context.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Optional request body for the RO-Crate export endpoint.
 *
 * <p>Empty / absent selection ⇒ byte-identical legacy export. The selection actually applied is
 * recorded in {@code ro-crate-metadata.json} so consumers can tell what was excluded.
 */
@Schema(name = "ExportSelection", description = "Optional filter for what payloads and metadata bundle into the RO-Crate export.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportSelection(@Valid Payloads payloads, @Valid Metadata metadata) {
  public ExportSelection {
    // record canonical constructor; nulls allowed and treated as "use defaults"
  }

  /** Allow-list of payload kinds and deny-list of payload ids. */
  @Schema(name = "ExportSelectionPayloads")
  public record Payloads(Set<PayloadKind> include, List<String> excludeIds) {}

  /** Booleans gating which categories of metadata bundle into the crate. */
  @Schema(name = "ExportSelectionMetadata")
  public record Metadata(
    Boolean permissions,
    Boolean annotations,
    Boolean labJournal,
    Boolean versions,
    Boolean subscriptions
  ) {}

  /** Payload kinds the exporter currently knows how to bundle. */
  public enum PayloadKind {
    FileReference,
    TimeseriesReference,
    StructuredDataReference,
    URIReference,
    BasicReference,
  }

  // -------- helpers --------------------------------------------------------

  public boolean isEmpty() {
    return payloads == null && metadata == null;
  }

  public boolean includesKind(PayloadKind kind) {
    if (payloads == null || payloads.include() == null || payloads.include().isEmpty()) return true;
    return payloads.include().contains(kind);
  }

  public boolean excludesId(String id) {
    if (id == null || payloads == null || payloads.excludeIds() == null) return false;
    return payloads.excludeIds().contains(id);
  }

  // metadata defaults reflect today's behaviour: only labJournal is emitted today.
  public boolean includePermissions() {
    return metadata != null && Boolean.TRUE.equals(metadata.permissions());
  }

  public boolean includeAnnotations() {
    return metadata != null && Boolean.TRUE.equals(metadata.annotations());
  }

  public boolean includeLabJournal() {
    return metadata == null || metadata.labJournal() == null || Boolean.TRUE.equals(metadata.labJournal());
  }

  public boolean includeVersions() {
    return metadata != null && Boolean.TRUE.equals(metadata.versions());
  }

  public boolean includeSubscriptions() {
    return metadata != null && Boolean.TRUE.equals(metadata.subscriptions());
  }
}
