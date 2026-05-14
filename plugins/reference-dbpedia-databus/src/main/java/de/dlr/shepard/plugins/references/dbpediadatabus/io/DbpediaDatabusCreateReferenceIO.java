package de.dlr.shepard.plugins.references.dbpediadatabus.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF1c — request body for
 * {@code POST /v2/data-objects/{appId}/dbpedia-databus-references}.
 *
 * <p>The {@code apiKey} field is handled once (inbound) and used
 * during the metadata fetch only — never persisted, never returned in
 * any response.
 */
@Data
@NoArgsConstructor
@Schema(name = "DbpediaDatabusCreateReference")
public class DbpediaDatabusCreateReferenceIO {

  @Schema(required = true, description = "Canonical DBpedia Databus artifact URI.")
  private String artifactUri;

  @Schema(
    description = "Optional API key header value for private Databus instances " +
    "(sent as X-API-Key). Not persisted — used for the initial metadata fetch only.",
    nullable = true
  )
  private String apiKey;
}
