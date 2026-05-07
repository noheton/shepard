package de.dlr.shepard.context.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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

  /**
   * Allow-list of payload kinds and deny-list of payload ids, plus optional per-payload picks.
   *
   * <p>Selection precedence: {@link #include()} and {@link #excludeIds()} apply first (does this
   * kind / id ship at all?). {@link #perPayload()} entries apply <em>within</em> the payloads
   * that survived that filter and only affect the bytes / rows / columns shipped — they do not
   * gate inclusion of the reference itself.
   *
   * <p>{@code perPayload} entries for kinds that don't apply (e.g. {@code fileOids} on a
   * TimeseriesReference id) are silently ignored — the fields are kind-specific.
   */
  @Schema(name = "ExportSelectionPayloads")
  public record Payloads(
    Set<PayloadKind> include,
    List<String> excludeIds,
    @Valid Map<String, PerPayloadSelection> perPayload,
    Boolean strictPerPayload
  ) {
    /** Backwards-compatible 2-arg constructor used by R2-Phase-1 callers and tests. */
    public Payloads(Set<PayloadKind> include, List<String> excludeIds) {
      this(include, excludeIds, null, null);
    }
  }

  /**
   * Per-entity-id pick of which bytes / rows / columns to ship for an included payload.
   *
   * <ul>
   *   <li>{@code fileOids} — applies only to FileReference payloads; selects which file OIDs to
   *       bundle. Stale OIDs (not on the reference) are silently skipped and recorded under
   *       {@code selection.warnings} unless {@code payloads.strictPerPayload=true}.</li>
   *   <li>{@code columns} — applies only to TimeseriesReference payloads; restricts the CSV to
   *       these field/column names (the timestamp column is always present). Unknown columns
   *       are silently skipped and recorded under {@code selection.warnings} unless strict.</li>
   *   <li>{@code timeRange} — applies only to TimeseriesReference payloads; truncates the CSV
   *       to {@code [start, end]}. Either bound may be omitted to leave that side open-ended.</li>
   * </ul>
   *
   * <p>All three fields are optional; an entry with all three absent is invalid. Kinds for which
   * the field doesn't apply silently ignore it.
   */
  @Schema(
    name = "PerPayloadSelection",
    description = "Per-entity selection of bytes / rows / columns within an already-included payload. " +
    "fileOids applies to FileReference; columns + timeRange apply to TimeseriesReference; other kinds ignore these fields. " +
    "Unknown OIDs / columns are silently skipped (recorded under selection.warnings) unless payloads.strictPerPayload=true."
  )
  public record PerPayloadSelection(List<String> fileOids, List<String> columns, @Valid TimeRange timeRange) {
    /** A {@link PerPayloadSelection} with all three fields absent is invalid. */
    @AssertTrue(message = "PerPayloadSelection must specify at least one of fileOids, columns, or timeRange")
    public boolean isAtLeastOneFieldPresent() {
      return (fileOids != null && !fileOids.isEmpty()) ||
      (columns != null && !columns.isEmpty()) ||
      timeRange != null;
    }
  }

  /**
   * Inclusive time window. Either bound may be {@code null} for open-ended; if both are
   * present, {@code start} must be ≤ {@code end}.
   */
  @Schema(name = "TimeRange", description = "Inclusive [start, end] window. Either bound may be null for open-ended; if both present, start ≤ end.")
  public record TimeRange(Instant start, Instant end) {
    @AssertTrue(message = "timeRange.start must be ≤ timeRange.end")
    public boolean isStartBeforeEnd() {
      return start == null || end == null || !start.isAfter(end);
    }
  }

  /**
   * Booleans gating which categories of metadata bundle into the crate, plus an optional
   * per-field redaction set (R2c).
   *
   * <p>Defaults match today's exporter behaviour: {@code labJournal} is included by default
   * (legacy behaviour); {@code permissions}, {@code annotations}, and {@code versions} default to
   * {@code false} and flipping them to {@code true} causes the exporter to emit per-entity
   * {@code <id>-permissions.json}, {@code <id>-annotations.json}, and {@code <id>-versions.json}
   * documents (collection-only for versions). {@code subscriptions} is currently
   * recorded-only — the exporter does not yet emit a per-entity subscriptions document because
   * subscriptions are URL-pattern based with no clean per-entity API; flipping it has no effect
   * beyond appearing in the {@code selection} block of {@code ro-crate-metadata.json}.
   *
   * <p>{@code redactFields} (R2c) is a closed set of redactable fields on the IO documents
   * actually emitted. Redaction is post-permission-check: a caller who can read the entity may
   * still want to hide specific fields when sharing the crate. Redaction has no effect on a
   * metadata kind that is not also opted-in via the booleans — the document is simply not
   * emitted, so there is nothing to redact. The redacted-field set is recorded in
   * {@code ro-crate-metadata.json} under {@code selection.metadata.redactFields} so consumers
   * know what was hidden; the replacement values are <em>not</em> recorded.
   */
  @Schema(
    name = "ExportSelectionMetadata",
    description = "Per-kind opt-in for bundling metadata documents. Defaults: labJournal=true (legacy); permissions/annotations/versions=false; subscriptions recorded-only (no document emitted). " +
    "redactFields is a closed set of fields on the emitted IO documents that are replaced with a sentinel before they are added to the crate (privacy: post-permission-check redaction). " +
    "Redaction has no effect on metadata kinds that are not also opted-in (no document emitted ⇒ nothing to redact)."
  )
  public record Metadata(
    Boolean permissions,
    Boolean annotations,
    Boolean labJournal,
    Boolean versions,
    Boolean subscriptions,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(
      description = "Closed set of fields on the emitted metadata IO documents that are replaced with a sentinel before bundling. Privacy semantics: post-permission-check redaction; the redacted field set is recorded in the manifest, the replacement values are not."
    ) Set<RedactableField> redactFields
  ) {
    /** Backwards-compatible 5-arg constructor used by R2-Phase-1 / R2d callers and tests. */
    public Metadata(
      Boolean permissions,
      Boolean annotations,
      Boolean labJournal,
      Boolean versions,
      Boolean subscriptions
    ) {
      this(permissions, annotations, labJournal, versions, subscriptions, null);
    }
  }

  /**
   * Closed set of fields on the emitted metadata IO documents that the caller can opt to redact
   * before the document lands in the RO-Crate. Each value targets a single field on a single IO
   * type. Redaction replaces strings with the sentinel {@code "[REDACTED]"}; numeric / id arrays
   * are emptied (they cannot hold a string sentinel and revealing the count alone is not a
   * meaningful privacy concession).
   *
   * <p>This is intentionally a closed enum — adding a redactable surface is a deliberate team
   * decision, not a freeform path expression that drifts with DTO shape changes.
   */
  @Schema(
    name = "RedactableField",
    description = "Closed set of fields on metadata IO documents that can be redacted before bundling. Strings are replaced with [REDACTED]; long[] arrays are emptied."
  )
  public enum RedactableField {
    /** Replaces every username field on PermissionsIO ({@code owner}, {@code reader[]}, {@code writer[]}, {@code manager[]}) with the sentinel {@code [REDACTED]}. */
    PERMISSION_USERNAME,
    /** Empties {@code readerGroupIds} and {@code writerGroupIds} on PermissionsIO. (PermissionsIO carries group ids, not group names.) */
    PERMISSION_GROUP_IDS,
    /** Replaces {@code propertyName} on SemanticAnnotationIO with {@code [REDACTED]} (the human-readable label of the property side of the triple; the IRI is left intact). */
    ANNOTATION_LABEL,
    /** Replaces {@code valueName} on SemanticAnnotationIO with {@code [REDACTED]} (the human-readable label of the value side of the triple; the IRI is left intact). */
    ANNOTATION_VALUE,
    /** Replaces {@code createdBy} on VersionIO with {@code [REDACTED]}. (VersionIO has no separate {@code author} field; {@code createdBy} carries the username.) */
    VERSION_AUTHOR,
    /** Replaces {@code journalContent} on LabJournalEntryIO with {@code [REDACTED]} (the body of an emitted lab-journal entry; redaction does not suppress emission). */
    LAB_JOURNAL_CONTENT,
  }

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

  /**
   * Returns the per-payload pick for the entity id, or {@code null} if none was configured.
   */
  public PerPayloadSelection perPayloadFor(String id) {
    if (id == null || payloads == null || payloads.perPayload() == null) return null;
    return payloads.perPayload().get(id);
  }

  /** Whether stale OIDs / unknown columns should fail the export instead of being skipped. */
  public boolean isStrictPerPayload() {
    return payloads != null && Boolean.TRUE.equals(payloads.strictPerPayload());
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

  /**
   * Returns the (immutable, never-null) set of fields the caller asked to redact on emitted
   * metadata documents. Used by the per-kind emitters in {@link ExportBuilder}.
   */
  public Set<RedactableField> redactFields() {
    if (metadata == null || metadata.redactFields() == null || metadata.redactFields().isEmpty()) {
      return EnumSet.noneOf(RedactableField.class);
    }
    return EnumSet.copyOf(metadata.redactFields());
  }

  /** Convenience predicate for the emitters. */
  public boolean isRedacted(RedactableField field) {
    return field != null && redactFields().contains(field);
  }
}
