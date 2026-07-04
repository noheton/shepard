package de.dlr.shepard.spi.fileparser;

import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.daos.VersionableEntityConcreteDAO;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CDI registry that discovers all {@link FileParserPlugin} implementations at
 * startup and fires them as secondary, fire-and-forget side-effects on singleton
 * file upload.
 *
 * <h2>Wiring</h2>
 * <ol>
 *   <li>Plugin JARs annotate their parser with {@code @ApplicationScoped} and list
 *       the class in
 *       {@code META-INF/services/de.dlr.shepard.spi.fileparser.FileParserPlugin}.
 *   <li>Quarkus indexes the plugin JAR via
 *       {@code quarkus.index-dependency.<id>.*} in {@code application.properties}.
 *   <li>This registry receives all beans via {@code Instance<FileParserPlugin>}
 *       and lazily re-iterates them on each {@link #runAll} call (so plugins
 *       installed while running in dev-mode also show up).
 * </ol>
 *
 * <h2>Fire-and-forget contract</h2>
 * Parsers are secondary writers — any unchecked exception they throw is caught
 * here, logged at WARN, and silently discarded.  The primary upload call-site
 * never sees parser failures (per the
 * "secondary writes are fire-and-forget" CLAUDE.md rule).
 *
 * <h2>Annotation write path</h2>
 * The registry receives the newly created {@code FileReference}'s {@code appId}
 * and reloads the entity inside the request scope via
 * {@link SingletonFileReferenceDAO} before writing annotations — mirrors the
 * {@code AttributeAnnotationDualWriteService} reload-before-annotate pattern.
 * Each annotation is persisted via
 * {@link SemanticAnnotationDAO#createOrUpdate(de.dlr.shepard.common.neo4j.entities.BasicEntity)},
 * attached to the reloaded entity via
 * {@code entity.addAnnotation(saved)}, then the entity itself is saved via
 * {@link VersionableEntityConcreteDAO}.
 */
@ApplicationScoped
public class FileParserRegistry {

  @Inject
  Instance<FileParserPlugin> parsers;

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Inject
  VersionableEntityConcreteDAO versionableEntityConcreteDAO;

  @Inject
  SingletonFileReferenceDAO singletonFileReferenceDAO;

  /** CDI constructor. */
  public FileParserRegistry() {}

  /** Log discovered parsers at startup — informational only; never fails. */
  void onStartup(@Observes StartupEvent ev) {
    try {
      List<String> names = new ArrayList<>();
      for (FileParserPlugin p : parsers) {
        if (p != null) names.add(p.getClass().getName());
      }
      Log.infof(
        "FileParserRegistry: discovered %d parser(s): [%s]",
        names.size(),
        String.join(", ", names)
      );
    } catch (Exception e) {
      Log.warnf(e, "FileParserRegistry: startup discovery check failed — parser list may be incomplete");
    }
  }

  /**
   * Cheap acceptance check performed <em>before</em> buffering the upload bytes.
   *
   * @param mimeType declared MIME type, or {@code null} when unknown
   * @param filename original upload filename
   * @return {@code true} when at least one installed parser accepts this file
   */
  public boolean anyAccepts(String mimeType, String filename) {
    if (filename == null) return false;
    try {
      for (FileParserPlugin p : parsers) {
        if (p != null && p.accepts(mimeType, filename)) return true;
      }
    } catch (Exception e) {
      Log.warnf(e, "FileParserRegistry.anyAccepts: error probing parsers for filename=%s", filename);
    }
    return false;
  }

  /**
   * Run all parsers that accept the given file, write their emitted annotations
   * to the {@link FileReference} identified by {@code fileRefAppId}, and
   * optionally tag the parent DataObject when the parser targets it.
   *
   * <p>Each parser runs independently — a failure in one does not prevent the
   * next from running.
   *
   * @param bytes              file bytes (must not be {@code null}; array must not be mutated by callers)
   * @param filename           original upload filename
   * @param fileRefAppId       appId of the freshly created singleton FileReference
   * @param parentDataObjectAppId appId of the parent DataObject, or {@code null} when absent
   */
  public void runAll(
    byte[] bytes,
    String filename,
    String fileRefAppId,
    String parentDataObjectAppId
  ) {
    if (bytes == null || filename == null) return;

    for (FileParserPlugin parser : parsers) {
      if (parser == null) continue;
      try {
        if (!parser.accepts(null, filename)) continue;

        List<WrittenAnnotation> buffer = new ArrayList<>();

        FileParserPlugin.ParseContext ctx = new FileParserPlugin.ParseContext() {
          @Override
          public byte[] bytes() {
            return bytes;
          }

          @Override
          public String filename() {
            return filename;
          }

          @Override
          public Optional<String> parentDataObjectAppId() {
            return Optional.ofNullable(parentDataObjectAppId);
          }

          @Override
          public Optional<String> fileReferenceAppId() {
            return Optional.ofNullable(fileRefAppId);
          }

          @Override
          public FileParserPlugin.AnnotationWriter annotations() {
            return (subjectAppId, predicate, value) -> {
              if (subjectAppId == null || predicate == null || value == null) return;
              buffer.add(new WrittenAnnotation(subjectAppId, predicate, value));
            };
          }
        };

        int count = parser.parse(ctx);
        Log.debugf(
          "FileParserRegistry: %s parsed '%s' → %d annotation(s) buffered",
          parser.getClass().getSimpleName(),
          filename,
          count
        );

        // Persist buffered annotations grouped by subject appId
        flushAnnotations(buffer);

      } catch (Exception e) {
        Log.warnf(
          e,
          "FileParserRegistry: secondary write failed in %s for filename='%s' fileRefAppId=%s",
          parser.getClass().getSimpleName(),
          filename,
          fileRefAppId
        );
      }
    }
  }

  /**
   * Persist all buffered annotations. Each annotation is written via the DAO,
   * then the subject entity is reloaded (if it is a {@link FileReference}) and
   * the annotation edge is attached.
   */
  private void flushAnnotations(List<WrittenAnnotation> buffer) {
    if (buffer == null || buffer.isEmpty()) return;

    for (WrittenAnnotation wa : buffer) {
      try {
        SemanticAnnotation annotation = new SemanticAnnotation();
        annotation.setPropertyIRI(wa.predicate());
        annotation.setPropertyName(wa.predicate());
        annotation.setValueName(wa.value());
        annotation.setSubjectAppId(wa.subjectAppId());
        annotation.setSubjectKind("FileReference");
        annotation.setSource("file-parser");

        SemanticAnnotation saved = semanticAnnotationDAO.createOrUpdate(annotation);
        if (saved == null) {
          Log.warnf(
            "FileParserRegistry: createOrUpdate returned null for predicate=%s subject=%s",
            wa.predicate(),
            wa.subjectAppId()
          );
          continue;
        }

        // Reload the entity inside the current request scope and attach the edge
        FileReference ref = singletonFileReferenceDAO.findByAppId(wa.subjectAppId());
        if (ref != null) {
          ref.addAnnotation(saved);
          versionableEntityConcreteDAO.createOrUpdate(ref);
        } else {
          Log.warnf(
            "FileParserRegistry: FileReference appId=%s not found — annotation node created but edge not attached",
            wa.subjectAppId()
          );
        }
      } catch (Exception e) {
        Log.warnf(
          e,
          "FileParserRegistry: failed to persist annotation predicate=%s subject=%s",
          wa.predicate(),
          wa.subjectAppId()
        );
      }
    }
  }

  /** Intermediate record used to buffer annotation writes before flushing. */
  private record WrittenAnnotation(String subjectAppId, String predicate, String value) {}
}
