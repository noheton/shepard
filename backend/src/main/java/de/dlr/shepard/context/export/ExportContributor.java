package de.dlr.shepard.context.export;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.io.IOException;

/**
 * CDI SPI for plugin-supplied export contributions. A plugin that introduces
 * a new {@link BasicReference} subtype can implement this interface and annotate
 * the implementation {@code @ApplicationScoped} to hook into the RO-Crate
 * export pipeline without requiring a backend-core compile dependency on the plugin.
 *
 * <p>The {@link ExportService} collects all implementations via
 * {@code @Inject @Any Instance<ExportContributor>} and calls the first matching
 * contributor (ordered by {@link #priority()}, lower = higher precedence).
 *
 * <p>A contributor is responsible for:
 * <ol>
 *   <li>Writing the reference's JSON sidecar via
 *       {@link ExportBuilder#addReference(de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO,
 *       de.dlr.shepard.auth.users.entities.User)}.</li>
 *   <li>Adding any additional contextual entities to the RO-Crate (e.g.
 *       {@code schema:SoftwareSourceCode}).</li>
 * </ol>
 *
 * <p>Implementations that don't match a given reference type are skipped; the
 * existing default {@code switch} path in {@link ExportService} handles all
 * reference types that no contributor claims.
 */
public interface ExportContributor {

  /** Returns {@code true} when this contributor handles the given Neo4j entity type string. */
  boolean handles(String referenceType);

  /**
   * Contribute this reference's data to the RO-Crate export.
   *
   * @param builder        the active export builder
   * @param reference      the reference entity (implementors may cast to their subtype)
   * @param callerUsername the exporting user's username (for PAT / credential lookups)
   * @throws IOException when writing to the export builder fails
   */
  void contribute(ExportBuilder builder, BasicReference reference, String callerUsername) throws IOException;

  /**
   * Ordering hint. Lower = higher priority. Default 1000.
   * Used when multiple contributors claim the same reference type (shouldn't happen,
   * but gives a predictable tie-break).
   */
  default int priority() {
    return 1000;
  }
}
