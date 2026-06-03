package de.dlr.shepard.plugin.fileformat.svdx;

import java.util.List;
import java.util.Optional;

/**
 * Local SPI shim for the {@code FileParserPlugin} interface sketched in
 * aidocs/integrations/110 §3.
 *
 * <p>Mirrors the shim in {@code plugins/fileformat-robotics/} verbatim
 * (same shape, same note about the Quarkus/Jandex hang preventing a
 * direct backend dependency). When the Jandex hang clears, replace this
 * file with {@code @Override} bindings against the real
 * {@code de.dlr.shepard.spi.fileparser.FileParserPlugin} interface.
 *
 * @see SvdxManifestParser
 */
public interface FileParserPlugin {

    /**
     * Cheap reject: does this plugin handle the file described by the
     * given MIME type and filename? Heavy work is left for
     * {@link #parse(ParseContext)}.
     */
    boolean accepts(String mimeType, String filename);

    /**
     * Parse the file and emit annotations via
     * {@link ParseContext#annotations()}.
     *
     * @param ctx per-invocation context — bytes + write target
     * @return the number of annotations emitted (informational)
     */
    int parse(ParseContext ctx);

    /**
     * Minimum {@code ParseContext} needed by tier-1 parsers.
     */
    interface ParseContext {
        byte[] bytes();
        String filename();
        Optional<String> parentDataObjectAppId();
        Optional<String> fileReferenceAppId();

        /** Other files in the same container; default empty. */
        default List<SiblingFile> siblingFiles() { return List.of(); }

        AnnotationWriter annotations();
    }

    /** A sibling file in the same {@code FileContainer}. */
    record SiblingFile(String filename, String fileReferenceAppId) {}

    /**
     * Receive an emitted semantic annotation. The real backend
     * implementation persists each call as a {@code :SemanticAnnotation}
     * Neo4j node anchored to the {@code subjectAppId}.
     */
    @FunctionalInterface
    interface AnnotationWriter {
        /**
         * @param subjectAppId appId of the annotation subject
         * @param predicate    IRI (e.g. {@code urn:shepard:svdx:channelName})
         * @param value        literal string value
         */
        void write(String subjectAppId, String predicate, String value);
    }
}
