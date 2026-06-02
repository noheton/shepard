package de.dlr.shepard.plugin.fileformat.svdx;

import java.util.List;
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
 * <p>Shape matches {@code de.dlr.shepard.plugin.fileformat.robotics.FileParserPlugin}
 * verbatim (see {@code plugins/fileformat-robotics/...}) — the canonical
 * SPI when it lands will subsume both. The SVDX parser uses
 * {@link ParseContext#siblingFiles()} to detect a sibling
 * {@code <basename>.csv} (the TwinCAT Scope Export Tool output) and
 * emit {@code urn:shepard:svdx:companionCsv} pointing at the CSV's
 * FileReference appId, in exact analogy to RDK's
 * {@code companionSpatialAnalyzer}.
 *
 * <p>Follow-up: when the backend Jandex hang clears, replace this file
 * with {@code @Override} bindings against the real interface and add a
 * {@code META-INF/services/de.dlr.shepard.spi.fileparser.FileParserPlugin}
 * service descriptor pointing at {@link SvdxManifestParser}.
 *
 * @see SvdxManifestParser
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
     *   <li>the filename (used for sibling-file matching),</li>
     *   <li>the parent {@code DataObject} appId (anchor for annotations),</li>
     *   <li>the {@code FileReference} appId (anchor for annotations),</li>
     *   <li>an enumeration of sibling files inside the same
     *       {@code FileContainer} (for companion-file detection),</li>
     *   <li>an {@link AnnotationWriter} callback.</li>
     * </ul>
     */
    interface ParseContext {
        byte[] bytes();
        String filename();
        Optional<String> parentDataObjectAppId();
        Optional<String> fileReferenceAppId();

        /**
         * Other files in the same {@code FileContainer} as the file
         * being parsed. Used by the SVDX parser to look up a paired
         * {@code <basename>.csv} sibling (the TwinCAT Scope Export
         * Tool output). Default empty — implementations that cannot
         * enumerate siblings simply skip the companion-file annotation,
         * which is allowed by the SPI contract (the predicate is
         * optional).
         */
        default List<SiblingFile> siblingFiles() { return List.of(); }

        AnnotationWriter annotations();
    }

    /**
     * Lightweight record describing a sibling file in the same
     * {@code FileContainer} as the file being parsed.
     */
    record SiblingFile(String filename, String fileReferenceAppId) {}

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
         * @param predicate     IRI (e.g. {@code urn:shepard:svdx:projectGuid})
         * @param value         literal string value (for multi-valued
         *                      predicates the writer is called once per
         *                      value)
         */
        void write(String subjectAppId, String predicate, String value);
    }
}
