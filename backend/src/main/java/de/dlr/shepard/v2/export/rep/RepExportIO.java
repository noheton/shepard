package de.dlr.shepard.v2.export.rep;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code POST /v2/collections/{appId}/export/regulatory-evidence}
 * and {@code GET /v2/collections/{appId}/export/regulatory-evidence/latest}.
 *
 * <p>TPL14 — Regulatory Evidence Pack (REP) export. Designed in
 * {@code aidocs/16-dispatcher-backlog.md §TPL14}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepExportIO {

  /**
   * Stable identifier for this export artefact (UUID v4, generated at export time).
   * Callers can use this to correlate an export with a specific submission event.
   */
  private String exportId;

  /**
   * Always {@code "READY"} for the synchronous build path.
   * Reserved for future async variant (status values: PENDING / BUILDING / READY / FAILED).
   */
  private String status;

  /**
   * Base64-encoded ZIP bytes of the BagIt bag.
   * Present only when the bag is ≤ 1 MB (inline delivery).
   * For larger bags this field is {@code null} and {@code downloadUrl} carries the content.
   */
  private String bagBase64;

  /**
   * Pre-signed or direct download URL. {@code null} when the bag is delivered inline
   * via {@code bagBase64}.
   */
  private String downloadUrl;

  /**
   * Suggested file name for download: {@code <collectionAppId>-rep.bag.zip}.
   */
  private String fileName;

  /**
   * ISO-8601 instant at which this export was built.
   */
  private Instant exportedAt;

  /**
   * Number of DataObjects included in the RO-Crate.
   */
  private int dataObjectCount;

  /**
   * Total size of the BagIt ZIP in bytes.
   */
  private long bagSizeBytes;
}
