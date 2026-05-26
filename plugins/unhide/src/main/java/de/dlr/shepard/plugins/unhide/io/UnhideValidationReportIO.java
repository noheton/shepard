package de.dlr.shepard.plugins.unhide.io;

import java.util.List;

/**
 * UH1e — response shape for {@code GET /v2/unhide/feed.jsonld?validate=true}.
 *
 * <p>Returned as {@code application/json} (not {@code application/ld+json} —
 * this is a diagnostic report, not a JSON-LD document).
 *
 * <p>The validation is structural, not full SHACL graph-inference.
 * Checks performed per entry in the {@code @graph}:
 *
 * <ol>
 *   <li>{@code @id} present and non-blank (required for JSON-LD
 *       identity; Unhide's inward-mappings reject entries without one).</li>
 *   <li>{@code name} present and non-blank (schema:name / rdfs:label;
 *       Unhide's UI displays this as the dataset title).</li>
 *   <li>{@code description} present and non-blank (schema:description;
 *       mandatory for human-readable metadata).</li>
 *   <li>{@code license} present and non-blank <em>when</em> the
 *       Collection has {@code accessRights="OPEN"} — currently inert
 *       because {@code Collection.accessRights} and
 *       {@code FeedEntryIO.license} are not yet wired; the check
 *       activates automatically once those fields land without
 *       requiring a UH1e change.</li>
 * </ol>
 *
 * <p>An empty graph ({@code @graph: []}) is always valid — there are
 * no entries to fail. Page semantics: when {@code ?validate=true&page=N}
 * is used, only the requested page's entries are validated.
 *
 * @param valid      {@code true} iff {@code errorCount == 0}.
 * @param errorCount total number of validation errors across all entries.
 * @param errors     human-readable error messages; at most one message
 *                   per failed field per entry in the form
 *                   {@code "[index=N] <what is missing>"}; never {@code null}
 *                   (empty list when valid).
 */
public record UnhideValidationReportIO(
  boolean valid,
  int errorCount,
  List<String> errors
) {}
