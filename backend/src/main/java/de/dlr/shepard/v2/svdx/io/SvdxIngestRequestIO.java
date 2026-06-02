package de.dlr.shepard.v2.svdx.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/svdx/ingest}.
 *
 * <p>Operator flow:
 * <ol>
 *   <li>Upload {@code .svdx} as a singleton FileReference on a
 *       DataObject (existing {@code POST /v2/files}).</li>
 *   <li>Generate the CSV sibling from Beckhoff's TwinCAT Scope Export
 *       Tool and upload it the same way.</li>
 *   <li>Call this endpoint with both appIds; the backend resolves the
 *       SVDX manifest (already parsed by AAB1 / {@code SvdxManifestParser}),
 *       cross-references its channels with the CSV header columns, and
 *       streams rows into TimescaleDB via the existing
 *       {@code TimeseriesService.saveDataPoints} path.</li>
 * </ol>
 *
 * <p>Idempotency: when {@link #svdxFileAppId} + {@link #csvFileAppId}
 * have already been ingested for the same parent DataObject, the
 * service short-circuits and returns the existing
 * {@code TimeseriesReference} unchanged. Detection is by a
 * {@code urn:shepard:svdx:ingestedFromCsv} annotation written on the
 * SVDX FileReference at the end of the first successful ingest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SvdxIngestRequest", description = "Request body for /v2/svdx/ingest.")
public class SvdxIngestRequestIO {

  @NotBlank
  @Schema(required = true, description = "appId (UUID v7) of the .svdx singleton FileReference.")
  private String svdxFileAppId;

  @NotBlank
  @Schema(required = true, description = "appId (UUID v7) of the .csv singleton FileReference produced by the TwinCAT Scope Export Tool.")
  private String csvFileAppId;

  @NotBlank
  @Schema(required = true, description = "appId (UUID v7) of the parent DataObject the new TimeseriesReference will attach to. Must be the same DataObject the two file singletons are attached to.")
  private String dataObjectAppId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "appId (UUID v7) of an existing TimeseriesContainer to ingest into. When omitted, a new container is minted in the same Collection.")
  private String tsContainerAppId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Display name for the new TimeseriesReference. Defaults to the SVDX project name + a suffix when omitted.")
  private String referenceName;
}
