package de.dlr.shepard.plugin.fileformat.svdx;

/**
 * IRI catalogue for SVDX-derived semantic annotations.
 *
 * <p>Stable predicate namespace under {@code urn:shepard:svdx:*}; bound
 * to the {@code <ScopeProject>} XML manifest schema embedded in every
 * Beckhoff TwinCAT Scope project file (verified across the 21 MFFD AFP
 * ultrasonic-spot-welding files from 2023-03-20).
 *
 * <p>Cardinality column documents whether a predicate is asserted at
 * most once per FileReference ("one") or zero-to-many times
 * ("many" — the writer is called once per value).
 *
 * <table>
 *   <caption>SVDX predicate catalogue</caption>
 *   <tr><th>Predicate</th><th>Cardinality</th><th>Source XML element</th></tr>
 *   <tr><td>{@link #FORMAT_VERSION}</td><td>one</td><td>envelope bytes 8-15 (decoded hex)</td></tr>
 *   <tr><td>{@link #PROJECT_GUID}</td><td>one</td><td>{@code <ScopeProject>/<Guid>}</td></tr>
 *   <tr><td>{@link #PROJECT_NAME}</td><td>one</td><td>{@code <ScopeProject>/<Name>}</td></tr>
 *   <tr><td>{@link #DATA_POOL_GUID}</td><td>one</td><td>{@code <DataPool>/<Guid>}</td></tr>
 *   <tr><td>{@link #MAIN_SERVER}</td><td>one</td><td>{@code <ScopeProject>/<MainServer>}</td></tr>
 *   <tr><td>{@link #RECORD_TIME_NS}</td><td>one</td><td>{@code <ScopeProject>/<RecordTime>}</td></tr>
 *   <tr><td>{@link #AUTO_SAVE_MODE}</td><td>one</td><td>{@code <ScopeProject>/<AutoSaveMode>}</td></tr>
 *   <tr><td>{@link #ASSEMBLY_NAME}</td><td>one</td><td>{@code <ScopeProject AssemblyName="…">}</td></tr>
 *   <tr><td>{@link #CHANNEL_COUNT}</td><td>one</td><td>derived: count of {@code <Channel>} elements</td></tr>
 *   <tr><td>{@link #AMS_NET_ID}</td><td>many</td><td>{@code <AmsNetId>} (one per channel, deduplicated)</td></tr>
 *   <tr><td>{@link #PORT}</td><td>many</td><td>{@code <Port>} (deduplicated)</td></tr>
 *   <tr><td>{@link #CHANNEL_NAME}</td><td>many</td><td>{@code <Channel>/<Name>}</td></tr>
 *   <tr><td>{@link #SYMBOL_NAME}</td><td>many</td><td>{@code <Channel>/<SymbolName>}</td></tr>
 *   <tr><td>{@link #DATA_TYPE}</td><td>many</td><td>{@code <Channel>/<DataType>} (deduplicated)</td></tr>
 *   <tr><td>{@link #COMPANION_CSV}</td><td>one</td><td>derived: sibling {@code <basename>.csv} or
 *       {@code <basename>_parsed.csv} FileReference appId, when present</td></tr>
 * </table>
 *
 * <p>Sample-block predicates (start-time, sample-count, channel data
 * offsets) are NOT in this list — the binary sample layout is
 * undocumented by Beckhoff and would require a multi-week reverse
 * engineering effort. See MFFD-PLUGIN-SVDX-BINARY-PARSER-1.
 */
public final class SvdxAnnotations {

    private SvdxAnnotations() {}

    /** {@code urn:shepard:svdx:formatVersion} — hex of the 8-byte version stamp at envelope offset 8 (e.g. {@code 0x71960c00}). */
    public static final String FORMAT_VERSION = "urn:shepard:svdx:formatVersion";

    /** {@code urn:shepard:svdx:projectGuid} — top-level ScopeProject GUID. */
    public static final String PROJECT_GUID = "urn:shepard:svdx:projectGuid";

    /** {@code urn:shepard:svdx:projectName} — human-readable scope project name (e.g. "Scope Project"). */
    public static final String PROJECT_NAME = "urn:shepard:svdx:projectName";

    /** {@code urn:shepard:svdx:dataPoolGuid} — GUID of the embedded DataPool. */
    public static final String DATA_POOL_GUID = "urn:shepard:svdx:dataPoolGuid";

    /** {@code urn:shepard:svdx:mainServer} — TwinCAT AMS NetId of the main ADS server (e.g. "127.0.0.1.1.1"). */
    public static final String MAIN_SERVER = "urn:shepard:svdx:mainServer";

    /** {@code urn:shepard:svdx:recordTimeNs} — configured record duration in 100-ns ticks. */
    public static final String RECORD_TIME_NS = "urn:shepard:svdx:recordTimeNs";

    /** {@code urn:shepard:svdx:autoSaveMode} — AutoSaveMode setting (e.g. "SVDX", "CSV"). */
    public static final String AUTO_SAVE_MODE = "urn:shepard:svdx:autoSaveMode";

    /** {@code urn:shepard:svdx:assemblyName} — .NET assembly name embedded in the root element. */
    public static final String ASSEMBLY_NAME = "urn:shepard:svdx:assemblyName";

    /** {@code urn:shepard:svdx:channelCount} — decimal count of {@code <Channel>} sub-elements (rendered chart channels). */
    public static final String CHANNEL_COUNT = "urn:shepard:svdx:channelCount";

    /** {@code urn:shepard:svdx:acquisitionCount} — decimal count of {@code <AdsAcquisition>} sub-elements (ADS data sources; usually higher than channelCount because of trigger groups). */
    public static final String ACQUISITION_COUNT = "urn:shepard:svdx:acquisitionCount";

    /** {@code urn:shepard:svdx:amsNetId} — TwinCAT AmsNetId(s) the channels were acquired from. */
    public static final String AMS_NET_ID = "urn:shepard:svdx:amsNetId";

    /** {@code urn:shepard:svdx:port} — ADS port(s) the channels were acquired through (e.g. "851"). */
    public static final String PORT = "urn:shepard:svdx:port";

    /** {@code urn:shepard:svdx:channelName} — short channel name as displayed in TwinCAT Scope. */
    public static final String CHANNEL_NAME = "urn:shepard:svdx:channelName";

    /** {@code urn:shepard:svdx:symbolName} — fully qualified TwinCAT symbol path (e.g. "RobotData.rRoboPosA"). */
    public static final String SYMBOL_NAME = "urn:shepard:svdx:symbolName";

    /** {@code urn:shepard:svdx:dataType} — IEC 61131-3 data type of a channel (e.g. "INT16", "REAL32", "REAL64", "BIT", "UINT64"). */
    public static final String DATA_TYPE = "urn:shepard:svdx:dataType";

    /** {@code urn:shepard:svdx:companionCsv} — FileReference appId of the sibling TwinCAT Scope Export Tool CSV/TXT export, when present in the same FileContainer. */
    public static final String COMPANION_CSV = "urn:shepard:svdx:companionCsv";
}
