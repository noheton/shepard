package de.dlr.shepard.context.export;

import de.dlr.shepard.common.exceptions.ShepardException;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Built-in {@link PayloadExportHandler} for {@code StructuredDataReference} rows.
 *
 * <p>Writes the reference JSON sidecar and all associated structured-data payloads.
 */
@ApplicationScoped
public class StructuredDataReferenceExportHandler implements PayloadExportHandler {

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Override
  public boolean handles(String referenceType) {
    return "StructuredDataReference".equals(referenceType);
  }

  @Override
  public void export(ExportBuilder builder, BasicReference reference, ExportContext ctx) throws IOException {
    var sdRef = structuredDataReferenceService.getReference(
      ctx.collectionId(),
      ctx.dataObjectId(),
      reference.getShepardId(),
      null
    );
    builder.addReference(new StructuredDataReferenceIO(sdRef), sdRef.getCreatedBy());

    List<StructuredDataPayload> payloads = Collections.emptyList();
    try {
      payloads = structuredDataReferenceService.getAllPayloads(
        ctx.collectionId(),
        ctx.dataObjectId(),
        reference.getShepardId()
      );
    } catch (ShepardException e) {
      Log.warn("Cannot access structured data payload during export");
    }

    for (var sdp : payloads) {
      if (sdp.getPayload() != null) writeStructuredDataPayload(builder, sdp);
    }
  }

  private static void writeStructuredDataPayload(
    ExportBuilder builder,
    StructuredDataPayload sdp
  ) throws IOException {
    var filename = sdp.getStructuredData().getOid() + ExportConstants.JSON_FILE_EXTENSION;
    builder.addPayload(sdp.getPayload().getBytes(), filename, sdp.getStructuredData().getName(), "application/json");
  }
}
