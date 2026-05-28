package de.dlr.shepard.plugin.fileformat.thermography;

import java.util.Optional;

/**
 * Local SPI shim for the {@code FileParserPlugin} interface sketched in
 * aidocs/integrations/110 §3.
 *
 * <p>The canonical interface lives in the main backend module
 * ({@code de.dlr.shepard.spi.fileparser.FileParserPlugin}), but the
 * standalone-module build constraint (see the module's pom.xml — the
 * Quarkus/Jandex hang in {@code backend/pom.xml} prevents pulling
 * backend as a dependency right now) means we can't compile against the
 * real interface. This file is the smallest viable surrogate: only the
 * methods the tier-1 parser needs to honour, with the same names so
 * binding the real SPI later is a single import-rewrite.
 *
 * <p>Follow-up: when the backend Jandex hang clears, replace this file
 * with {@code @Override} bindings against the real interface and add a
 * {@code META-INF/services/de.dlr.shepard.spi.fileparser.FileParserPlugin}
 * service descriptor pointing at {@link OTvisParser}.
 *
 * @see OTvisParser
 */
public interface FileParserPlugin {

    /**
     * Cheap reject path: does this plugin claim to know how to parse the
     * file identified by the given filename + MIME type? Implementations
     * should return {@code true} for matches and {@code false} otherwise
     * — heavy work is left for {@link #parse(ParseContext)}.
     */
    boolean accepts(String mimeType, String filename);

    /**
     * Parse the file referenced by the {@link ParseContext} and emit
     * annotations via {@link ParseContext#annotations()}.
     *
     * @param ctx the per-invocation context — input bytes + write target
     * @return the number of annotations emitted (purely informational)
     */
    int parse(ParseContext ctx);

    /**
     * Minimum {@code ParseContext} stub. The canonical context (per
     * aidocs/integrations/110 §3) carries an OID handle, a Garage
     * download stream, container service handles, and a progress
     * reporter — none of which we can express against a backend we can't
     * link to. Tier-1 only needs:
     *
     * <ul>
     *   <li>the file bytes (already downloaded by the caller),</li>
     *   <li>the filename (for the {@link OTvisFilenameParser}),</li>
     *   <li>the parent {@code DataObject} appId (anchor for annotations),</li>
     *   <li>the {@code FileReference} appId (anchor for annotations),</li>
     *   <li>an {@link AnnotationWriter} callback.</li>
     * </ul>
     */
    interface ParseContext {
        byte[] bytes();
        String filename();
        Optional<String> parentDataObjectAppId();
        Optional<String> fileReferenceAppId();
        AnnotationWriter annotations();
    }

    /**
     * Receive an emitted semantic annotation. The real backend
     * implementation persists each call as a {@code :SemanticAnnotation}
     * Neo4j node anchored to the {@code subjectAppId}. Tier-1 callers
     * (and tests) provide a recording implementation.
     */
    @FunctionalInterface
    interface AnnotationWriter {
        /**
         * @param subjectAppId  appId of the entity the predicate is asserted on
         * @param predicate     IRI (e.g. {@code urn:shepard:thermography:frameRate_Hz})
         * @param value         literal string value
         */
        void write(String subjectAppId, String predicate, String value);
    }
}
