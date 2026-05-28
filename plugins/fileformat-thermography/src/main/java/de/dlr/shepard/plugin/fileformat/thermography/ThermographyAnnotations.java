package de.dlr.shepard.plugin.fileformat.thermography;

/**
 * Canonical predicate IRIs emitted by the OTvis tier-1 parser.
 *
 * Follow the {@code urn:shepard:&lt;domain&gt;:&lt;role&gt;} namespace convention.
 * Two domains are touched:
 *
 * <ul>
 *   <li>{@code urn:shepard:thermography:*} — acquisition parameters,
 *       camera + excitation device + lock-in setup.</li>
 *   <li>{@code urn:shepard:mffd:*} — physical-grid coordinates
 *       (Section / Module / Layer / Frame) extracted from the
 *       {@code S&lt;n&gt;_M&lt;n&gt;_L&lt;n&gt;_F&lt;n&gt;.OTvis} filename pattern.</li>
 * </ul>
 *
 * Reference: aidocs/integrations/114 §4.
 */
public final class ThermographyAnnotations {

    private ThermographyAnnotations() {
        // utility class — no instances
    }

    // ─── urn:shepard:thermography:* (acquisition parameters) ──────────────────

    public static final String FRAME_RATE_HZ = "urn:shepard:thermography:frameRate_Hz";
    public static final String INTEGRATION_TIME_S = "urn:shepard:thermography:integrationTime_s";
    public static final String EXCITATION_DEVICE = "urn:shepard:thermography:excitationDevice";
    public static final String EXCITATION_FREQUENCY_HZ = "urn:shepard:thermography:excitationFrequency_Hz";
    public static final String EXCITATION_AMPLITUDE_PCT = "urn:shepard:thermography:excitationAmplitude_pct";
    public static final String EXCITATION_SIGNAL_TYPE = "urn:shepard:thermography:excitationSignalType";
    public static final String RECORDING_TYPE = "urn:shepard:thermography:recordingType";
    public static final String RESOLUTION = "urn:shepard:thermography:resolution";
    public static final String CONDITIONING_PERIODS = "urn:shepard:thermography:conditioningPeriods";
    public static final String ACQUISITION_PERIODS = "urn:shepard:thermography:acquisitionPeriods";
    public static final String CAMPAIGN = "urn:shepard:thermography:campaign";
    public static final String MODULE_NAME = "urn:shepard:thermography:moduleName";
    public static final String CREATING_VERSION = "urn:shepard:thermography:creatingVersion";
    public static final String CREATED_AT = "urn:shepard:thermography:createdAt";

    // ─── urn:shepard:mffd:* (grid coordinates from filename) ─────────────────

    public static final String MFFD_SECTION = "urn:shepard:mffd:section";
    public static final String MFFD_MODULE = "urn:shepard:mffd:module";
    public static final String MFFD_LAYER = "urn:shepard:mffd:layer";
    public static final String MFFD_FRAME = "urn:shepard:mffd:frame";
}
