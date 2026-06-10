package de.dlr.shepard.plugin.fileformat.svdx;

import de.dlr.shepard.spi.fileparser.FileParserPlugin;
import de.dlr.shepard.spi.fileparser.FileParserPlugin.AnnotationWriter;
import de.dlr.shepard.spi.fileparser.FileParserPlugin.ParseContext;
import de.dlr.shepard.spi.fileparser.FileParserPlugin.SiblingFile;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tier-1 {@link FileParserPlugin} implementation for Beckhoff TwinCAT
 * Scope project files (.svdx).
 *
 * <p>The {@link #parse(FileParserPlugin.ParseContext)} entry point:
 *
 * <ol>
 *   <li>verifies the 16-byte envelope via {@link SvdxEnvelope#tryDecode},
 *       short-circuiting on failure (no annotations emitted);</li>
 *   <li>locates the trailing XML manifest via the envelope pointer and
 *       parses it with {@link SvdxManifestExtractor};</li>
 *   <li>emits {@code urn:shepard:svdx:*} annotations onto the parent
 *       FileReference's appId per the {@link SvdxAnnotations} catalogue;</li>
 *   <li>looks for a sibling {@code <basename>.csv} or
 *       {@code <basename>_parsed.csv} in the same FileContainer and,
 *       when found, emits {@link SvdxAnnotations#COMPANION_CSV} with
 *       the sibling FileReference's appId.</li>
 * </ol>
 *
 * <p>The proprietary binary sample blocks ahead of the XML manifest
 * are NOT decoded — Beckhoff publishes no spec and the
 * community-standard {@code pytcs} package punts on the binary in
 * favour of CSV/TXT exports. See {@code docs/byte-layout-notes.md}
 * for the reverse-engineering notes and
 * MFFD-PLUGIN-SVDX-BINARY-PARSER-1 for the deferred research row.
 *
 * <p>All exceptions are caught inside {@link #parse} and logged at
 * {@link Level#WARNING} — never propagated. Semantic annotations are a
 * secondary write per CLAUDE.md §secondary-writes-fire-and-forget.
 */
@ApplicationScoped
public final class SvdxManifestParser implements FileParserPlugin {

    private static final Logger LOG = Logger.getLogger(SvdxManifestParser.class.getName());

    /** File-extension match used by {@link #accepts}. */
    public static final String EXTENSION = ".svdx";

    /** MIME types that should reach this plugin (best-effort — most
     *  filesystems do not register an SVDX MIME, so the extension
     *  match is the primary path). */
    public static final String MIME_OCTET_STREAM = "application/octet-stream";
    public static final String MIME_X_BECKHOFF_SVDX = "application/vnd.beckhoff.scope+svdx";

    @Override
    public boolean accepts(String mimeType, String filename) {
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return true;
        }
        if (mimeType != null && MIME_X_BECKHOFF_SVDX.equalsIgnoreCase(mimeType)) {
            return true;
        }
        return false;
    }

    @Override
    public int parse(ParseContext ctx) {
        if (ctx == null) {
            return 0;
        }
        Optional<String> subjectOpt = ctx.fileReferenceAppId();
        if (subjectOpt.isEmpty()) {
            // Without a FileReference appId we have no anchor for
            // annotations; the parent DataObject is a coarser anchor
            // and may be acceptable in future shapes, but the current
            // SPI shim treats it as a hard requirement for clarity.
            return 0;
        }
        String subject = subjectOpt.get();

        byte[] bytes = ctx.bytes();
        if (bytes == null || bytes.length < SvdxEnvelope.MIN_SIZE_BYTES) {
            return 0;
        }

        Optional<SvdxEnvelope> envOpt = SvdxEnvelope.tryDecode(bytes, bytes.length);
        if (envOpt.isEmpty()) {
            return 0;
        }
        SvdxEnvelope env = envOpt.get();

        // Emit format-version eagerly (lightest signal, useful even if
        // the manifest parse later fails — the file is at least
        // recognisably a TwinCAT Scope project).
        AnnotationWriter w = ctx.annotations();
        int n = 0;
        w.write(subject, SvdxAnnotations.FORMAT_VERSION, env.formatVersionHex());
        n++;

        SvdxManifest manifest;
        try {
            manifest = SvdxManifestExtractor.extract(bytes, env);
        } catch (IOException ioe) {
            LOG.log(Level.WARNING,
                "svdx manifest parse failed for fileReferenceAppId=" + subject, ioe);
            // Still return n=1: the formatVersion annotation we already
            // wrote stands; it documents that this *is* an .svdx that
            // we recognised but could not deeply parse.
            return n;
        }

        n += emitManifestAnnotations(w, subject, manifest);
        n += emitCompanionCsv(w, subject, ctx);

        return n;
    }

    private int emitManifestAnnotations(AnnotationWriter w, String subject, SvdxManifest m) {
        int n = 0;
        n += writeOne(w, subject, SvdxAnnotations.ASSEMBLY_NAME, m.assemblyName());
        n += writeOne(w, subject, SvdxAnnotations.PROJECT_GUID, m.projectGuid());
        n += writeOne(w, subject, SvdxAnnotations.PROJECT_NAME, m.projectName());
        n += writeOne(w, subject, SvdxAnnotations.DATA_POOL_GUID, m.dataPoolGuid());
        n += writeOne(w, subject, SvdxAnnotations.MAIN_SERVER, m.mainServer());
        n += writeOne(w, subject, SvdxAnnotations.RECORD_TIME_NS, m.recordTimeNs());
        n += writeOne(w, subject, SvdxAnnotations.AUTO_SAVE_MODE, m.autoSaveMode());

        // Channel count is always emitted, even when zero, so a query
        // for "which uploads were unparseable" can distinguish "no
        // manifest" (no count annotation) from "manifest empty" (count = 0).
        w.write(subject, SvdxAnnotations.CHANNEL_COUNT, Integer.toString(m.channelCount()));
        n++;
        // Acquisition count distinguishes "46 rendered channels" from
        // "149 ADS data sources" — the trigger-group fan-out the
        // user can't see in the chart UI but matters for ingest.
        w.write(subject, SvdxAnnotations.ACQUISITION_COUNT, Integer.toString(m.acquisitionCount()));
        n++;

        for (String netId : m.amsNetIds()) {
            w.write(subject, SvdxAnnotations.AMS_NET_ID, netId);
            n++;
        }
        for (String port : m.ports()) {
            w.write(subject, SvdxAnnotations.PORT, port);
            n++;
        }
        for (String dt : m.dataTypes()) {
            w.write(subject, SvdxAnnotations.DATA_TYPE, dt);
            n++;
        }
        // Channel names come from the <Channel> render layer.
        for (SvdxManifest.Channel ch : m.channels()) {
            n += writeOne(w, subject, SvdxAnnotations.CHANNEL_NAME, ch.name());
        }
        // Symbol names come from the <AdsAcquisition> data-source layer
        // (this is the GVL_IO_US_Endeffektor.* fully qualified path).
        for (SvdxManifest.Channel acq : m.acquisitions()) {
            n += writeOne(w, subject, SvdxAnnotations.SYMBOL_NAME, acq.symbolName());
        }
        return n;
    }

    /**
     * Look for a sibling {@code <basename>.csv} or
     * {@code <basename>_parsed.csv} (the TwinCAT Scope Export Tool
     * output, observed e.g. as {@code Scope Project_AutoSave_19_04_29.csv}
     * sitting next to {@code Scope Project_AutoSave_19_04_29.svdx} in
     * the MFFD campaign). Emit {@link SvdxAnnotations#COMPANION_CSV}
     * with the sibling FileReference appId when found.
     */
    private int emitCompanionCsv(AnnotationWriter w, String subject, ParseContext ctx) {
        String filename = ctx.filename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return 0;
        }
        String base = filename.substring(0, filename.length() - EXTENSION.length());
        String exactCsv = (base + ".csv").toLowerCase(Locale.ROOT);
        String parsedCsv = (base + "_parsed.csv").toLowerCase(Locale.ROOT);
        String foundAppId = null;
        // Prefer the *_parsed.csv when both exist (it's the post-processed
        // semicolon-separated form, more directly machine-readable).
        for (SiblingFile sib : ctx.siblingFiles()) {
            if (sib == null || sib.filename() == null) continue;
            String lc = sib.filename().toLowerCase(Locale.ROOT);
            if (lc.equals(parsedCsv)) {
                foundAppId = sib.fileReferenceAppId();
                break;
            }
        }
        if (foundAppId == null) {
            for (SiblingFile sib : ctx.siblingFiles()) {
                if (sib == null || sib.filename() == null) continue;
                String lc = sib.filename().toLowerCase(Locale.ROOT);
                if (lc.equals(exactCsv)) {
                    foundAppId = sib.fileReferenceAppId();
                    break;
                }
            }
        }
        if (foundAppId == null || foundAppId.isBlank()) {
            return 0;
        }
        w.write(subject, SvdxAnnotations.COMPANION_CSV, foundAppId);
        return 1;
    }

    private static int writeOne(AnnotationWriter w, String subject, String predicate, Optional<String> v) {
        if (v == null || v.isEmpty()) return 0;
        String value = v.get();
        if (value == null || value.isBlank()) return 0;
        w.write(subject, predicate, value);
        return 1;
    }
}
