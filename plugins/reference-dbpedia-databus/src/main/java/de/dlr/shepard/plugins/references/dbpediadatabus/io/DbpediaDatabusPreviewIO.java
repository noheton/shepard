package de.dlr.shepard.plugins.references.dbpediadatabus.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF1c — wire shape for a Databus artifact preview. Returned by
 * {@code GET /v2/data-objects/{id}/dbpedia-databus-references/{refId}/preview}
 * and used internally to carry the parsed JSON-LD fields back from
 * {@link de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient}.
 *
 * <p>All failure modes surface as {@code available=false} + a {@code reason}
 * discriminator so the UI can render an explanation inline.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "DbpediaDatabusPreview")
public class DbpediaDatabusPreviewIO {

  @Schema(description = "true when the artifact was reachable and parsed successfully.")
  private boolean available;

  @Schema(
    description = "Short machine-readable failure reason when available=false. " +
    "Values: disabled, host-not-allowed, invalid-uri, auth.failed, fetch-failed, parse-failed."
  )
  private String reason;

  @Schema(description = "dcat:title / dct:title from the artifact JSON-LD (cached).")
  private String title;

  @Schema(description = "dct:abstract / dct:description (cached).")
  private String description;

  @Schema(description = "dcat:version / dct:hasVersion (cached).")
  private String version;

  @Schema(description = "dct:license / dct:rights (cached).")
  private String licence;

  @Schema(description = "dct:modified — artifact-side modification timestamp (cached).")
  private Date modifiedAt;

  @Schema(description = "Epoch when this server last successfully refreshed the cache.")
  private Date cacheFetchedAt;

  @Schema(description = "fresh / stale / unavailable.")
  private String cacheStatus;

  @Schema(description = "dcat:distribution entries from the JSON-LD (up to 100).")
  private List<Distribution> distributions;

  /** A single dcat:distribution entry parsed from the artifact JSON-LD. */
  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "DbpediaDatabusDistribution")
  public static class Distribution {

    @Schema(description = "dct:title of the distribution file.")
    private String name;

    @Schema(description = "dcat:mediaType / dct:format (MIME type).")
    private String mimeType;

    @Schema(description = "dcat:downloadURL of the distribution.")
    private String downloadUrl;

    @Schema(description = "dcat:byteSize in bytes.")
    private Long byteSize;
  }
}
