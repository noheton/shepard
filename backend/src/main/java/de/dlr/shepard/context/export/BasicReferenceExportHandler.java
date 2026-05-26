package de.dlr.shepard.context.export;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * Built-in {@link PayloadExportHandler} fallback that handles every reference type
 * not claimed by a more specific handler.
 *
 * <p>This is the equivalent of the old {@code default} switch branch: it writes a
 * standard {@link BasicReferenceIO} sidecar so every reference in the RO-Crate
 * has at least a JSON descriptor. It intentionally has the lowest possible priority
 * so that more specific handlers (and plugin contributors) always take precedence.
 */
@ApplicationScoped
public class BasicReferenceExportHandler implements PayloadExportHandler {

  /** Max priority value — guarantees this handler is always last. */
  private static final int FALLBACK_PRIORITY = Integer.MAX_VALUE;

  @Inject
  BasicReferenceService basicReferenceService;

  /** Matches every type — this is the catch-all fallback. */
  @Override
  public boolean handles(String referenceType) {
    return true;
  }

  @Override
  public int priority() {
    return FALLBACK_PRIORITY;
  }

  @Override
  public void export(ExportBuilder builder, BasicReference reference, ExportContext ctx) throws IOException {
    var ref = basicReferenceService.getReference(
      ctx.collectionId(),
      ctx.dataObjectId(),
      reference.getShepardId()
    );
    builder.addReference(new BasicReferenceIO(ref), ref.getCreatedBy());
  }
}
