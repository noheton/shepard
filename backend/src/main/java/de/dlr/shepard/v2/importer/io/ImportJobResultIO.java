package de.dlr.shepard.v2.importer.io;

import java.util.List;

/**
 * IMP2 — response body for {@code POST /v2/import/jobs}.
 *
 * <p>Returned with HTTP 201 on success, or HTTP 207 (multi-status) when
 * some entities failed to create ({@code status = "PARTIAL_FAILURE"}).
 *
 * <p>The {@code jobAppId} is a UUID v7 minted per execution; no
 * {@code :ImportJob} node is persisted — it is informational only.
 *
 * @param jobAppId   UUID v7 minted for this execution (informational).
 * @param planCommitId  The commitId of the plan that was consumed.
 * @param status     {@code "COMPLETED"} when all entities were created,
 *                   {@code "PARTIAL_FAILURE"} when at least one failed.
 * @param dataObjects  Every DataObject that was (or was attempted to be) created.
 * @param containers   Every Container that was (or was attempted to be) created.
 * @param errors     Human-readable error messages for any failures;
 *                   empty list on full success.
 */
public record ImportJobResultIO(
  String jobAppId,
  String planCommitId,
  String status,
  List<CreatedEntityIO> dataObjects,
  List<CreatedEntityIO> containers,
  List<String> errors
) {

  /**
   * Summary of one entity that was created (or failed to be created) during
   * the import execution.
   *
   * @param localRef  The caller-assigned {@code localRef} from the manifest,
   *                  or {@code null} for entities without a localRef.
   * @param appId     The UUID v7 assigned to the entity, or {@code null} when
   *                  creation failed.
   * @param kind      Entity kind label, e.g. {@code "DataObject"},
   *                  {@code "FileContainer"}, {@code "TimeseriesContainer"},
   *                  {@code "StructuredDataContainer"}, {@code "FileReference"},
   *                  {@code "TimeseriesReference"}, {@code "StructuredDataReference"}.
   */
  public record CreatedEntityIO(String localRef, String appId, String kind) {}
}
