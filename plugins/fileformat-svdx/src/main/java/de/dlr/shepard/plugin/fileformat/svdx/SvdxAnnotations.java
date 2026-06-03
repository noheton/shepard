package de.dlr.shepard.plugin.fileformat.svdx;

/**
 * Canonical predicate IRIs emitted by the Beckhoff TwinCAT Scope
 * ({@code .svdx}) tier-1 manifest parser. Follows the
 * {@code urn:shepard:<domain>:<role>} namespace convention; this module
 * owns {@code urn:shepard:svdx:*} and borrows the cross-cutting
 * {@code urn:shepard:phys:*} physical-quantity namespace.
 *
 * <p>The {@code svdx:*} predicates capture channel metadata extracted from
 * the XML manifest prefix that Beckhoff TwinCAT Scope embeds before the
 * binary payload. The {@code phys:*} predicates are <em>derived</em> —
 * inferred from Beckhoff channel-name conventions (see
 * {@link SvdxManifestParser#classifyChannelPhysics(String)}) and assert the
 * physical quantity a channel measures, independent of the raw channel name.
 *
 * <p>Reference: aidocs/integrations/110 §4.2,
 * aidocs/16-dispatcher-backlog.md MFFD-PLUGIN-SVDX-1 +
 * MFFD-PLUGIN-SVDX-SEMANTIC-1.
 */
public final class SvdxAnnotations {

    private SvdxAnnotations() {
        // utility class — no instances
    }

    // ── SVDX channel-metadata predicates (urn:shepard:svdx:*) ────────────

    /** Prefix shared by every svdx-domain predicate. */
    public static final String SVDX_PREDICATE_PREFIX = "urn:shepard:svdx:";

    /**
     * Raw channel name from the SVDX manifest
     * (e.g. {@code "aTemperatureAnalogInput1"}). Multi-valued — emitted
     * once per channel present in the file.
     */
    public static final String CHANNEL_NAME = SVDX_PREDICATE_PREFIX + "channelName";

    /**
     * Symbolic name (human-readable alias) from the SVDX manifest
     * (e.g. {@code "TC Head 01"}). Multi-valued — one per channel; absent
     * channels that carry no symbolic name.
     */
    public static final String SYMBOL_NAME = SVDX_PREDICATE_PREFIX + "symbolName";

    /**
     * Sample rate declared in the SVDX manifest header
     * (e.g. {@code "1000 Hz"}). Single-valued per file.
     */
    public static final String SAMPLE_RATE = SVDX_PREDICATE_PREFIX + "sampleRate";

    /**
     * Engineering unit of a channel as declared in the manifest
     * (e.g. {@code "°C"}, {@code "N"}, {@code "mm"}). Multi-valued —
     * one per channel.
     */
    public static final String UNIT = SVDX_PREDICATE_PREFIX + "unit";

    /**
     * Process type derived from the filename or manifest comment
     * (e.g. {@code "welding"}, {@code "afp-consolidation"}). Single-valued.
     */
    public static final String PROCESS_TYPE = SVDX_PREDICATE_PREFIX + "processType";

    /**
     * Weld seam or pass identifier derived from the filename
     * (e.g. {@code "P08_2teBahn"}, {@code "TG258_spot_001"}). Single-valued.
     */
    public static final String SEAM_ID = SVDX_PREDICATE_PREFIX + "seamId";

    // ── Physical-quantity predicates (urn:shepard:phys:*) ─────────────────
    //
    // Derived from Beckhoff TwinCAT Scope channel-name conventions.
    // Value "true" is used (presence = quantity asserted). These predicates
    // are cross-cutting — they may be emitted by other parsers in future
    // (e.g. the Spatial Analyzer parser for force/torque channels).

    /** Prefix shared by every phys-domain predicate. */
    public static final String PHYS_PREDICATE_PREFIX = "urn:shepard:phys:";

    /** {@code urn:shepard:phys:temperature} — channel measures temperature (°C). */
    public static final String PHYS_TEMPERATURE = PHYS_PREDICATE_PREFIX + "temperature";

    /** {@code urn:shepard:phys:position} — channel measures position or angle. */
    public static final String PHYS_POSITION = PHYS_PREDICATE_PREFIX + "position";

    /** {@code urn:shepard:phys:torque} — channel measures torque (N·m). */
    public static final String PHYS_TORQUE = PHYS_PREDICATE_PREFIX + "torque";

    /** {@code urn:shepard:phys:force} — channel measures force (N). */
    public static final String PHYS_FORCE = PHYS_PREDICATE_PREFIX + "force";

    /** {@code urn:shepard:phys:ultrasonic-power} — channel is an ultrasonic actuator signal. */
    public static final String PHYS_ULTRASONIC_POWER = PHYS_PREDICATE_PREFIX + "ultrasonic-power";

    /** {@code urn:shepard:phys:pressure} — channel measures pressure (Pa or bar). */
    public static final String PHYS_PRESSURE = PHYS_PREDICATE_PREFIX + "pressure";

    /** {@code urn:shepard:phys:velocity} — channel measures velocity or speed. */
    public static final String PHYS_VELOCITY = PHYS_PREDICATE_PREFIX + "velocity";
}
