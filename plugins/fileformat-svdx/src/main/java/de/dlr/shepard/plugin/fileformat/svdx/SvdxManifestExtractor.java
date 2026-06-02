package de.dlr.shepard.plugin.fileformat.svdx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Extracts the trailing {@code <ScopeProject>} XML manifest from an
 * SVDX file and parses it via streaming StAX into a {@link SvdxManifest}.
 *
 * <p>Streaming throughout: the front section (which can be > 1 GB —
 * the largest file in the campaign is 1.4 GB) is never loaded; only
 * the XML tail starting at {@link SvdxEnvelope#xmlBodyOffset()} is
 * read into memory. The tail is ~800 KB in every observed sample, so
 * this is comfortable.
 *
 * <p>Mapping of TwinCAT Scope concepts to manifest elements (observed
 * empirically across the MFFD campaign):
 *
 * <ul>
 *   <li>{@code <ScopeProject>} — root project node carrying GUID,
 *       Name, MainServer (AMS NetId of the main ADS host), RecordTime,
 *       AutoSaveMode.</li>
 *   <li>{@code <DataPool>} — direct child of ScopeProject;
 *       its {@code <Guid>} is captured as the {@code dataPoolGuid}.</li>
 *   <li>{@code <AdsAcquisition>} — one per ADS data source the scope
 *       acquires. Carries the real per-signal metadata:
 *       {@code <SymbolName>}, {@code <DataType>}, {@code <AmsNetId>},
 *       {@code <TargetPort>}, {@code <Name>} (the symbol-display name),
 *       {@code <IndexGroup>}, {@code <IndexOffset>}. There are usually
 *       MORE AdsAcquisitions than rendered Channels (the campaign
 *       observed 149 acquisitions for 46 channels — multiple trigger
 *       groups per signal).</li>
 *   <li>{@code <Channel>} — one per rendered chart channel. Carries
 *       only {@code <Name>} (display name) and {@code <Guid>}; data
 *       semantics live on the referenced {@code AdsAcquisition}.</li>
 * </ul>
 *
 * <p>The XML parsing is StAX (pull) — no DOM, no schema validation, no
 * DTD resolution. The reader is hardened against XXE via the standard
 * "secure processing" features (no external DTDs, no external entities).
 */
public final class SvdxManifestExtractor {

    private SvdxManifestExtractor() {}

    public static final String ROOT_ELEMENT = "ScopeProject";
    public static final String DATA_POOL_ELEMENT = "DataPool";
    public static final String CHANNEL_ELEMENT = "Channel";
    public static final String ADS_ACQUISITION_ELEMENT = "AdsAcquisition";

    /** Cap on tail bytes to slurp — protects against malformed pointers. */
    public static final int MAX_TAIL_BYTES = 64 * 1024 * 1024; // 64 MB

    public static SvdxManifest extract(byte[] bytes, SvdxEnvelope envelope) throws IOException {
        long bodyOffset = envelope.xmlBodyOffset();
        if (bodyOffset >= bytes.length) {
            throw new IOException("xml body offset " + bodyOffset
                + " past file end " + bytes.length);
        }
        long tailLen = bytes.length - bodyOffset;
        if (tailLen > MAX_TAIL_BYTES) {
            throw new IOException("xml tail " + tailLen
                + " exceeds safety cap " + MAX_TAIL_BYTES);
        }
        int offsetInt = (int) bodyOffset;
        int lenInt = (int) tailLen;
        try (InputStream in = new ByteArrayInputStream(bytes, offsetInt, lenInt)) {
            return parseXml(in);
        }
    }

    public static SvdxManifest parseXml(InputStream xmlStream) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);

        SvdxManifest.Builder b = new SvdxManifest.Builder();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(xmlStream, StandardCharsets.UTF_8.name());
            walk(reader, b);
            return b.build();
        } catch (XMLStreamException xse) {
            throw new IOException("malformed SVDX XML manifest", xse);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (XMLStreamException ignored) { /* fire-and-forget */ }
            }
        }
    }

    private static void walk(XMLStreamReader r, SvdxManifest.Builder b) throws XMLStreamException {
        Deque<String> path = new ArrayDeque<>();
        // Acquisition scratch — populated when we are at the *top* of
        // an AdsAcquisition element (not in a nested ChannelStyle, etc.).
        AcqScratch acq = null;
        // Channel scratch.
        ChannelScratch ch = null;
        int channelDepth = 0;        // > 0 while inside a <Channel> (allows nested)
        int adsDepth = 0;            // > 0 while inside an <AdsAcquisition>
        int dataPoolDepth = 0;       // > 0 while inside the top-level <DataPool>
        boolean rootSeen = false;

        while (r.hasNext()) {
            int evt = r.next();
            switch (evt) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String name = r.getLocalName();
                    path.push(name);

                    if (!rootSeen && ROOT_ELEMENT.equals(name)) {
                        rootSeen = true;
                        String asm = r.getAttributeValue(null, "AssemblyName");
                        if (asm != null && !asm.isBlank()) {
                            b.assemblyName = Optional.of(asm);
                        }
                    }
                    if (DATA_POOL_ELEMENT.equals(name)) {
                        dataPoolDepth++;
                    }
                    if (CHANNEL_ELEMENT.equals(name)) {
                        if (channelDepth == 0) {
                            ch = new ChannelScratch();
                        }
                        channelDepth++;
                    }
                    if (ADS_ACQUISITION_ELEMENT.equals(name)) {
                        if (adsDepth == 0) {
                            acq = new AcqScratch();
                        }
                        adsDepth++;
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (path.isEmpty()) break;
                    String tip = path.peek();
                    String text = r.getText();
                    if (text == null) break;
                    String trimmed = text.trim();
                    if (trimmed.isEmpty()) break;

                    // Inside an AdsAcquisition: capture only direct-child
                    // text. Path depth check: tip is a direct child when
                    // the element-just-below-the-tip on the stack is
                    // "AdsAcquisition". Use second peek.
                    if (adsDepth > 0 && acq != null && isDirectChildOf(path, ADS_ACQUISITION_ELEMENT)) {
                        switch (tip) {
                            case "Name" -> acq.name = first(acq.name, trimmed);
                            case "SymbolName" -> acq.symbolName = first(acq.symbolName, trimmed);
                            case "DataType" -> acq.dataType = first(acq.dataType, trimmed);
                            case "AmsNetId" -> acq.amsNetId = first(acq.amsNetId, trimmed);
                            case "TargetPort", "Port" -> acq.port = first(acq.port, trimmed);
                            default -> { /* ignore */ }
                        }
                    }
                    // Inside a Channel: capture only direct-child text.
                    if (channelDepth > 0 && ch != null && isDirectChildOf(path, CHANNEL_ELEMENT)) {
                        switch (tip) {
                            case "Name" -> ch.name = first(ch.name, trimmed);
                            default -> { /* ignore */ }
                        }
                    }
                    // Top-level (ScopeProject) properties — only when
                    // we are not inside any nested DataPool/Channel/Ads
                    // element. Special-case <Guid> for DataPool.
                    if (channelDepth == 0 && adsDepth == 0) {
                        // ScopeProject-direct children:
                        if (isDirectChildOf(path, ROOT_ELEMENT)) {
                            switch (tip) {
                                case "Guid" -> b.projectGuid = first(b.projectGuid, trimmed);
                                case "Name" -> b.projectName = first(b.projectName, trimmed);
                                case "MainServer" -> b.mainServer = first(b.mainServer, trimmed);
                                case "RecordTime" -> b.recordTimeNs = first(b.recordTimeNs, trimmed);
                                case "AutoSaveMode" -> b.autoSaveMode = first(b.autoSaveMode, trimmed);
                                default -> { /* ignore */ }
                            }
                        }
                        // DataPool-direct children — only top-level DataPool, dataPoolDepth==1.
                        if (dataPoolDepth == 1 && isDirectChildOf(path, DATA_POOL_ELEMENT)) {
                            if ("Guid".equals(tip)) {
                                b.dataPoolGuid = first(b.dataPoolGuid, trimmed);
                            }
                        }
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    String name = r.getLocalName();
                    if (CHANNEL_ELEMENT.equals(name)) {
                        channelDepth--;
                        if (channelDepth == 0 && ch != null) {
                            b.channels.add(new SvdxManifest.Channel(
                                ch.name, Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty()
                            ));
                            ch = null;
                        }
                    }
                    if (ADS_ACQUISITION_ELEMENT.equals(name)) {
                        adsDepth--;
                        if (adsDepth == 0 && acq != null) {
                            // Aggregate top-level dedup lists; also expose
                            // the acquisition as a "channel" record so
                            // callers can iterate symbol-name/data-type
                            // pairs without needing the chart channel
                            // join. This is the practical analogue of
                            // what the CSV ground truth gives us.
                            acq.amsNetId.ifPresent(b.amsNetIds::add);
                            acq.port.ifPresent(b.ports::add);
                            acq.dataType.ifPresent(b.dataTypes::add);
                            // Acquisition-level "channel" record — useful
                            // for emitting one SymbolName + matching
                            // DataType per acquisition.
                            b.acquisitions.add(new SvdxManifest.Channel(
                                acq.name, acq.symbolName, acq.dataType,
                                acq.amsNetId, acq.port
                            ));
                            acq = null;
                        }
                    }
                    if (DATA_POOL_ELEMENT.equals(name)) {
                        dataPoolDepth--;
                    }
                    if (!path.isEmpty()) path.pop();
                }
                default -> { /* skip */ }
            }
        }
    }

    /** True iff the second element of {@code path} (parent of tip) equals {@code parentName}. */
    private static boolean isDirectChildOf(Deque<String> path, String parentName) {
        if (path.size() < 2) return false;
        // ArrayDeque pushes onto the head; the second-from-head is the parent of the tip.
        var it = path.iterator();
        it.next(); // skip tip
        return parentName.equals(it.next());
    }

    private static Optional<String> first(Optional<String> existing, String v) {
        return (existing == null || existing.isEmpty()) ? Optional.of(v) : existing;
    }

    /** Per-AdsAcquisition mutable accumulator. */
    private static final class AcqScratch {
        Optional<String> name = Optional.empty();
        Optional<String> symbolName = Optional.empty();
        Optional<String> dataType = Optional.empty();
        Optional<String> amsNetId = Optional.empty();
        Optional<String> port = Optional.empty();
    }

    /** Per-Channel mutable accumulator. */
    private static final class ChannelScratch {
        Optional<String> name = Optional.empty();
    }
}
