package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * CLI-side wire-shape mirror of the backend's {@code AasIdtaImportResultIO}.
 * Decoupled from the backend stack so the CLI JAR stays thin.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AasIdtaImportResult {

  private final List<CreatedTemplate> created;
  private final int skipped;

  @JsonCreator
  public AasIdtaImportResult(
      @JsonProperty("created") List<CreatedTemplate> created,
      @JsonProperty("skipped") int skipped) {
    this.created = created == null ? List.of() : List.copyOf(created);
    this.skipped = skipped;
  }

  public List<CreatedTemplate> getCreated() {
    return created == null ? Collections.emptyList() : created;
  }

  public int getSkipped() {
    return skipped;
  }

  /** Minimal projection of one created/updated ShepardTemplateIO. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class CreatedTemplate {

    private final String appId;
    private final String name;
    private final Integer version;

    @JsonCreator
    public CreatedTemplate(
        @JsonProperty("appId") String appId,
        @JsonProperty("name") String name,
        @JsonProperty("version") Integer version) {
      this.appId = appId;
      this.name = name;
      this.version = version;
    }

    public String getAppId() {
      return appId;
    }

    public String getName() {
      return name;
    }

    public Integer getVersion() {
      return version;
    }
  }
}
