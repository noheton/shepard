package de.dlr.shepard.spi.fileparser;

import java.util.List;
import java.util.Optional;

/**
 * SPI contract for file-format metadata parsers.
 *
 * <p>Implementations are discovered via CDI ({@code Instance<FileParserPlugin>}) at
 * build time — each plugin JAR annotates its parser with {@code @ApplicationScoped}
 * and lists the class in
 * {@code META-INF/services/de.dlr.shepard.spi.fileparser.FileParserPlugin}.
 *
 * <p>The canonical wiring point is
 * {@link de.dlr.shepard.context.references.file.services.SingletonFileReferenceService
 * SingletonFileReferenceService#createSingleton}, which fires all matching parsers as
 * a secondary, fire-and-forget side-effect after the primary GridFS upload succeeds.
 * Parsers must never throw propagating exceptions — unchecked exceptions are caught
 * by the registry and logged as WARN.
 *
 * <p>The {@link ParseContext#siblingFiles()} default method returns an empty list so
 * the singleton-upload path (which always passes no siblings) never needs an
 * implementation; SVDX / robotics companion-file logic overrides it at parse time.
 */
public interface FileParserPlugin {

  /**
   * Returns {@code true} when this parser can handle the given content.
   *
   * <p>This method is called cheaply (with {@code mimeType == null} and only the
   * filename) before any byte buffering to avoid heap cost for unrecognised extensions.
   *
   * @param mimeType declared MIME type, or {@code null} when unknown
   * @param filename original upload filename (never {@code null})
   */
  boolean accepts(String mimeType, String filename);

  /**
   * Parses the supplied file bytes and emits semantic annotations via
   * {@link ParseContext#annotations()}.
   *
   * @param ctx parse context providing bytes, filename, parent appIds, and the
   *            annotation writer
   * @return the number of annotations written (informational, may be zero)
   */
  int parse(ParseContext ctx);

  /** Context supplied to {@link #parse}. */
  interface ParseContext {

    /** Raw file bytes. Never {@code null}; the array must not be mutated. */
    byte[] bytes();

    /** Original upload filename. Never {@code null}. */
    String filename();

    /**
     * The {@code appId} of the parent DataObject, if known.
     *
     * <p>May be empty when the upload is not yet associated with a DataObject.
     */
    Optional<String> parentDataObjectAppId();

    /**
     * The {@code appId} of the newly created {@link
     * de.dlr.shepard.context.references.file.entities.FileReference FileReference}.
     * Used as the default subject for emitted annotations.
     */
    Optional<String> fileReferenceAppId();

    /**
     * Companion files in the same bundle or upload batch, if any.
     *
     * <p>The singleton-upload path always returns an empty list; multi-file bundle
     * parsers (SVDX, robotics) may return non-empty lists here.
     */
    default List<SiblingFile> siblingFiles() {
      return List.of();
    }

    /** Writer for emitting semantic annotations during parsing. */
    AnnotationWriter annotations();
  }

  /**
   * A companion file in the same upload batch.
   *
   * @param filename         original filename of the companion file
   * @param fileReferenceAppId {@code appId} of the companion's FileReference entity
   */
  record SiblingFile(String filename, String fileReferenceAppId) {}

  /**
   * Writes a semantic annotation for a given subject entity.
   *
   * @param subjectAppId the {@code appId} of the entity to annotate
   * @param predicate    the annotation predicate (e.g. {@code urn:shepard:cad:step:schema})
   * @param value        the annotation value
   */
  @FunctionalInterface
  interface AnnotationWriter {
    void write(String subjectAppId, String predicate, String value);
  }
}
