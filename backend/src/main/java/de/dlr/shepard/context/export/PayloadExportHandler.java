package de.dlr.shepard.context.export;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.io.IOException;

/**
 * Internal SPI for per-payload-kind export logic in the built-in reference types.
 *
 * <p>This is intentionally a separate interface from the plugin-facing
 * {@link ExportContributor} — it carries the richer dispatch context
 * ({@link ExportContext}) that built-in handlers need (collection id, data-object
 * id, per-payload selection) and that would be a breaking change to expose on the
 * plugin SPI.
 *
 * <p>Implementations are {@code @ApplicationScoped} CDI beans collected by
 * {@link ExportService} via {@code @Inject @Any Instance<PayloadExportHandler>}.
 * The first handler whose {@link #handles(String)} returns {@code true} for the
 * reference type is used; tie-breaking is by {@link #priority()} (lower = higher
 * precedence, default {@value #DEFAULT_PRIORITY}).
 *
 * <p>Dispatch order in {@link ExportService}:
 * <ol>
 *   <li>Plugin-supplied {@link ExportContributor} implementations (first by priority).</li>
 *   <li>Built-in {@link PayloadExportHandler} implementations (first by priority).</li>
 *   <li>Fallback: {@link BasicReferenceExportHandler} (handles every type, lowest priority).</li>
 * </ol>
 */
public interface PayloadExportHandler {

  /** Returns {@code true} when this handler manages the given Neo4j entity-type string. */
  boolean handles(String referenceType);

  /**
   * Write the reference data to the export builder.
   *
   * @param builder     the active export builder
   * @param reference   the reference entity
   * @param ctx         the per-dispatch context (ids, selection, caller username)
   * @throws IOException when writing to the builder fails
   */
  void export(ExportBuilder builder, BasicReference reference, ExportContext ctx) throws IOException;

  /**
   * Ordering hint. Lower = higher priority. Default {@value #DEFAULT_PRIORITY}.
   * Used when multiple handlers claim the same reference type.
   */
  int DEFAULT_PRIORITY = 1000;

  default int priority() {
    return DEFAULT_PRIORITY;
  }
}
