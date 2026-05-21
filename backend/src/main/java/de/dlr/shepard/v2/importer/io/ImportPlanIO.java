package de.dlr.shepard.v2.importer.io;

import java.util.List;

/**
 * IMP1 — response body for {@code POST /v2/import/validate} and
 * {@code GET /v2/import/plans/{commitId}}.
 *
 * <p>When the manifest is valid, {@link #commitId()} is non-null and the
 * caller must pass it to {@code POST /v2/import/jobs} to execute the import.
 *
 * <p>When the manifest has hard errors, {@link #errors()} is non-empty and
 * {@link #commitId()} is {@code null} — no plan is persisted. The response
 * status in that case is {@code 422 Unprocessable Entity}.
 *
 * <p>Soft issues (name conflicts, etc.) appear in {@link #warnings()} and do
 * not prevent the commitId from being issued.
 *
 * @param commitId  plan seal; {@code null} when {@link #errors()} is non-empty
 * @param status    {@code VALID} when the plan is ready to execute; {@code INVALIDATED} on error
 * @param expiresAt ISO-8601 timestamp after which the plan is no longer accepted
 * @param summary   counts of objects that would be created / skipped
 * @param warnings  soft issues (non-blocking)
 * @param errors    hard errors that prevented a commitId from being issued
 */
public record ImportPlanIO(
  String commitId,
  String status,
  String expiresAt,
  ImportSummaryIO summary,
  List<String> warnings,
  List<String> errors
) {

  /**
   * Counts of what the import would do if committed.
   *
   * @param wouldCreateDataObjects  number of new DataObjects to be created
   * @param wouldCreateContainers   number of new Containers to be created
   * @param wouldCreateReferences   number of new DataObject-Container links to be created
   * @param wouldSkipDataObjects    number of DataObjects skipped because a name already exists in the collection
   */
  public record ImportSummaryIO(
    int wouldCreateDataObjects,
    int wouldCreateContainers,
    int wouldCreateReferences,
    int wouldSkipDataObjects
  ) {}
}
