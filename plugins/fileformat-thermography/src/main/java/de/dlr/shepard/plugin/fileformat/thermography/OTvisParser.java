package de.dlr.shepard.plugin.fileformat.thermography;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Tier-1 parser for Edevis OTvis active lock-in thermography
 * measurements (MFFD upper-shell campaign at DLR ZLP Augsburg).
 *
 * <p><b>Scope (tier-1).</b> Given the raw bytes of a {@code .OTvis} file,
 * extract the embedded {@code content.xml} manifest and the four-field
 * grid coordinate from the filename, and emit one
 * {@code SemanticAnnotation} per discovered field on the parent
 * {@code DataObject} via the {@link AnnotationWriter} callback. Filename
 * grid fields are emitted on the parent DataObject only; acquisition
 * fields are emitted on the FileReference when one is supplied, falling
 * back to the parent DataObject otherwise. The parser creates no
 * DataObjects, no containers, and never writes anywhere except through
 * the supplied callback.
 *
 * <p><b>Out of scope (tier-2).</b> Frame data inside {@code sequence0/}
 * and {@code sequence1/} is left untouched — that work is filed as
 * {@code OTVIS-PARSE-2}. The companion {@code .diproj} project manifest
 * is deliberately ignored to honour the DO-sprawl containment rule from
 * aidocs/integrations/114 §0.
 *
 * <p><b>Error policy.</b> Any failure (corrupt tar, missing
 * {@code content.xml}, malformed XML) results in zero emitted
 * annotations but no thrown exception — tier-1 is a best-effort
 * enrichment hook, never the cause of an upload failure.
 *
 * <p>Reference: aidocs/integrations/114 §1.1 + §4.
 */
public final class OTvisParser implements FileParserPlugin {

    /** MIME type that we recognise. Edevis tooling uses {@code application/x-tar}; we also accept generic octet-stream when the extension matches. */
    public static final String MIME_TAR = "application/x-tar";
    /** Accepted extension (case-insensitive). */
    public static final String EXTENSION = ".otvis";

    @Override
    public boolean accepts(String mimeType, String filename) {
        if (filename == null) return false;
        String lc = filename.toLowerCase(Locale.ROOT);
        if (lc.endsWith(EXTENSION)) {
            // Filename match is enough — Edevis files don't carry a
            // registered IANA MIME type and uploaders typically tag them
            // application/octet-stream.
            return true;
        }
        return MIME_TAR.equals(mimeType);
    }

    @Override
    public int parse(ParseContext ctx) {
        if (ctx == null || ctx.bytes() == null || ctx.bytes().length == 0) {
            return 0;
        }

        // The annotation subject preference is FileReference (the
        // uploaded artefact) but the filename-derived grid coordinates
        // describe the parent DataObject. When no FileReference appId is
        // supplied, fall back to the parent DataObject for everything.
        String fileRefSubject = ctx.fileReferenceAppId()
                .or(ctx::parentDataObjectAppId)
                .orElse(null);
        String parentSubject = ctx.parentDataObjectAppId()
                .or(ctx::fileReferenceAppId)
                .orElse(null);
        if (fileRefSubject == null && parentSubject == null) {
            // Nothing to anchor annotations to; nothing to do.
            return 0;
        }

        int count = 0;

        // 1) Acquisition metadata from content.xml on the FileReference.
        byte[] contentXml = readContentXml(ctx.bytes());
        if (contentXml.length > 0) {
            Map<String, String> meta = OTvisMetadataExtractor.extract(contentXml);
            for (Map.Entry<String, String> e : meta.entrySet()) {
                ctx.annotations().write(fileRefSubject, e.getKey(), e.getValue());
                count++;
            }
        }

        // 2) MFFD grid coordinates from filename on the parent DO.
        Optional<OTvisFilenameParser.GridPosition> grid =
                OTvisFilenameParser.parse(ctx.filename());
        if (grid.isPresent() && parentSubject != null) {
            OTvisFilenameParser.GridPosition g = grid.get();
            ctx.annotations().write(parentSubject, ThermographyAnnotations.MFFD_SECTION, g.section());
            ctx.annotations().write(parentSubject, ThermographyAnnotations.MFFD_MODULE,  g.module());
            ctx.annotations().write(parentSubject, ThermographyAnnotations.MFFD_LAYER,   g.layer());
            ctx.annotations().write(parentSubject, ThermographyAnnotations.MFFD_FRAME,   g.frame());
            count += 4;
        }
        return count;
    }

    // ─── tar helpers ─────────────────────────────────────────────────────────

    /**
     * Read the {@code content.xml} entry out of an OTvis tar bytestream.
     * Returns an empty array when the entry is absent or the tar is
     * malformed — never throws.
     */
    private static byte[] readContentXml(byte[] tarBytes) {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isFile()) continue;
                // Tar entries may be stored as "content.xml" or with
                // platform path quirks; the basename match is what we
                // care about.
                String name = entry.getName();
                if (name == null) continue;
                int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                String basename = slash >= 0 ? name.substring(slash + 1) : name;
                if (!OTvisContentSchema.CONTENT_XML_ENTRY.equalsIgnoreCase(basename)) continue;

                long size = entry.getSize();
                ByteArrayOutputStream buf = size > 0 && size < Integer.MAX_VALUE
                        ? new ByteArrayOutputStream((int) size)
                        : new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = tar.read(chunk)) >= 0) {
                    buf.write(chunk, 0, n);
                }
                return buf.toByteArray();
            }
        } catch (IOException ignored) {
            // Best-effort tier-1: corrupt archive → empty result.
        }
        return new byte[0];
    }
}
