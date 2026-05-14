package de.dlr.shepard.plugins.references.dbpediadatabus.io;

import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusReference;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF1c — wire shape for
 * {@code /v2/data-objects/{appId}/dbpedia-databus-references}.
 */
@Data
@NoArgsConstructor
@Schema(name = "DbpediaDatabusReference")
public class DbpediaDatabusReferenceIO {

  @Schema(readOnly = true, nullable = true, description = "Application identifier (UUID v7).")
  private String appId;

  @Schema(required = true, description = "Canonical DBpedia Databus artifact URI.")
  private String artifactUri;

  @Schema(readOnly = true, nullable = true, description = "dcat:title from the artifact's JSON-LD (cached).")
  private String cachedTitle;

  @Schema(readOnly = true, nullable = true, description = "dct:abstract / dct:description (cached).")
  private String cachedAbstract;

  @Schema(readOnly = true, nullable = true, description = "dcat:version (cached).")
  private String cachedVersion;

  @Schema(readOnly = true, nullable = true, description = "dct:license (cached).")
  private String cachedLicence;

  @Schema(readOnly = true, nullable = true, description = "dct:modified — artifact-side timestamp (cached).")
  private Date cachedModifiedAt;

  @Schema(readOnly = true, nullable = true, description = "Epoch when this server last refreshed the cache.")
  private Date cacheFetchedAt;

  @Schema(readOnly = true, nullable = true, description = "fresh / stale / unavailable.")
  private String cacheStatus;

  public DbpediaDatabusReferenceIO(DbpediaDatabusReference src) {
    this.appId = src.getAppId();
    this.artifactUri = src.getArtifactUri();
    this.cachedTitle = src.getCachedTitle();
    this.cachedAbstract = src.getCachedAbstract();
    this.cachedVersion = src.getCachedVersion();
    this.cachedLicence = src.getCachedLicence();
    Long modifiedAt = src.getCachedModifiedAtMillis();
    this.cachedModifiedAt = modifiedAt == null ? null : new Date(modifiedAt);
    Long fetchedAt = src.getCacheFetchedAtMillis();
    this.cacheFetchedAt = fetchedAt == null ? null : new Date(fetchedAt);
    this.cacheStatus = src.getCacheStatus();
  }
}
