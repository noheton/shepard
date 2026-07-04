package de.dlr.shepard.plugin.fileformat.svdx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Immutable summary of the trailing {@code <ScopeProject>} XML manifest
 * extracted by {@link SvdxManifestExtractor} from a TwinCAT Scope
 * project file.
 *
 * <p>Records-shaped containers carry only the fields the tier-1
 * annotation emitter needs. Channel detail is captured at the
 * resolution the {@link SvdxAnnotations} catalogue documents — name,
 * symbol name, data type — not every {@code <Channel>} sub-property.
 *
 * <p>All collection fields are returned as unmodifiable views.
 */
public final class SvdxManifest {

    private final Optional<String> assemblyName;
    private final Optional<String> projectGuid;
    private final Optional<String> projectName;
    private final Optional<String> dataPoolGuid;
    private final Optional<String> mainServer;
    private final Optional<String> recordTimeNs;
    private final Optional<String> autoSaveMode;
    private final List<String> amsNetIds;
    private final List<String> ports;
    private final List<String> dataTypes;
    private final List<Channel> channels;
    private final List<Channel> acquisitions;

    private SvdxManifest(Builder b) {
        this.assemblyName = b.assemblyName;
        this.projectGuid = b.projectGuid;
        this.projectName = b.projectName;
        this.dataPoolGuid = b.dataPoolGuid;
        this.mainServer = b.mainServer;
        this.recordTimeNs = b.recordTimeNs;
        this.autoSaveMode = b.autoSaveMode;
        this.amsNetIds = List.copyOf(b.amsNetIds);
        this.ports = List.copyOf(b.ports);
        this.dataTypes = List.copyOf(b.dataTypes);
        this.channels = List.copyOf(b.channels);
        this.acquisitions = List.copyOf(b.acquisitions);
    }

    public Optional<String> assemblyName() { return assemblyName; }
    public Optional<String> projectGuid() { return projectGuid; }
    public Optional<String> projectName() { return projectName; }
    public Optional<String> dataPoolGuid() { return dataPoolGuid; }
    public Optional<String> mainServer() { return mainServer; }
    public Optional<String> recordTimeNs() { return recordTimeNs; }
    public Optional<String> autoSaveMode() { return autoSaveMode; }

    /** Deduplicated AmsNetIds appearing on any channel. */
    public List<String> amsNetIds() { return amsNetIds; }

    /** Deduplicated ADS ports appearing on any channel. */
    public List<String> ports() { return ports; }

    /** Deduplicated IEC 61131-3 data types in use. */
    public List<String> dataTypes() { return dataTypes; }

    /** Channel records in document order. Empty {@code symbolName}/
     *  {@code dataType}/{@code amsNetId}/{@code port} fields — those
     *  semantics live on the matched AdsAcquisition; see
     *  {@link #acquisitions()}. */
    public List<Channel> channels() { return channels; }

    /** Number of {@code <Channel>} elements (rendered chart channels). */
    public int channelCount() { return channels.size(); }

    /** AdsAcquisition records in document order — each carries the
     *  symbol-name + data-type + AmsNetId + port of one ADS data
     *  source. There may be MORE acquisitions than channels (trigger
     *  groups). */
    public List<Channel> acquisitions() { return acquisitions; }

    /** Number of {@code <AdsAcquisition>} elements. */
    public int acquisitionCount() { return acquisitions.size(); }

    /**
     * Per-channel record. Only the fields used by the tier-1 annotation
     * emitter; the manifest carries many more (IndexGroup, IndexOffset,
     * ScaleFactor, BitMask, …) that we deliberately do not surface.
     */
    public record Channel(
        Optional<String> name,
        Optional<String> symbolName,
        Optional<String> dataType,
        Optional<String> amsNetId,
        Optional<String> port
    ) {
        public Channel {
            // Optional<>.of(null) blows up; defensively normalise.
            name = name == null ? Optional.empty() : name;
            symbolName = symbolName == null ? Optional.empty() : symbolName;
            dataType = dataType == null ? Optional.empty() : dataType;
            amsNetId = amsNetId == null ? Optional.empty() : amsNetId;
            port = port == null ? Optional.empty() : port;
        }
    }

    /** Mutable builder used by the StAX parser. */
    static final class Builder {
        Optional<String> assemblyName = Optional.empty();
        Optional<String> projectGuid = Optional.empty();
        Optional<String> projectName = Optional.empty();
        Optional<String> dataPoolGuid = Optional.empty();
        Optional<String> mainServer = Optional.empty();
        Optional<String> recordTimeNs = Optional.empty();
        Optional<String> autoSaveMode = Optional.empty();
        final List<String> amsNetIds = new ArrayList<>();
        final List<String> ports = new ArrayList<>();
        final List<String> dataTypes = new ArrayList<>();
        final List<Channel> channels = new ArrayList<>();
        final List<Channel> acquisitions = new ArrayList<>();

        SvdxManifest build() {
            // Deduplicate in-place while preserving first-seen order.
            dedupePreserveOrder(amsNetIds);
            dedupePreserveOrder(ports);
            dedupePreserveOrder(dataTypes);
            return new SvdxManifest(this);
        }

        private static void dedupePreserveOrder(List<String> xs) {
            if (xs.size() < 2) return;
            List<String> out = new ArrayList<>(xs.size());
            for (String s : xs) {
                if (!out.contains(s)) out.add(s);
            }
            xs.clear();
            xs.addAll(out);
            Collections.unmodifiableList(xs); // documentation-only call
        }
    }
}
