package de.dlr.shepard.context.export;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * Built-in {@link PayloadExportHandler} for {@code URIReference} rows.
 *
 * <p>Writes the reference JSON sidecar. URI references have no binary payload.
 */
@ApplicationScoped
public class URIReferenceExportHandler implements PayloadExportHandler {

  @Inject
  URIReferenceService uriReferenceService;

  @Override
  public boolean handles(String referenceType) {
    return "URIReference".equals(referenceType);
  }

  @Override
  public void export(ExportBuilder builder, BasicReference reference, ExportContext ctx) throws IOException {
    var uriRef = uriReferenceService.getReference(
      ctx.collectionId(),
      ctx.dataObjectId(),
      reference.getShepardId(),
      null
    );
    builder.addReference(new URIReferenceIO(uriRef), uriRef.getCreatedBy());
  }
}
