package de.dlr.shepard.plugin.fileformat.thermography;

/**
 * Names of the XML element paths read from {@code content.xml} in an
 * Edevis OTvis tar archive. The schema is documented in the Edevis
 * "DisplayImg Dateiformat Rev_H" PDF (see aidocs/integrations/114).
 *
 * The root element is {@code <DisplayImgFileFormat>}; the metadata block
 * we mine for tier-1 is its {@code <FileInfo>} child. Sequence blocks
 * (per-stream {@code <Sequence>}) carry frame data that is OUT OF SCOPE
 * for tier-1 (frame extraction = tier-2, OTVIS-PARSE-2).
 *
 * <p>Tag names are kept here as constants rather than inlined into the
 * extractor so the schema is one tap away when a new field needs adding.
 */
public final class OTvisContentSchema {

    private OTvisContentSchema() {
        // constants only
    }

    /** Tar entry name to read inside the {@code .OTvis} archive. */
    public static final String CONTENT_XML_ENTRY = "content.xml";

    /** Root element of {@code content.xml}. */
    public static final String ELEM_ROOT = "DisplayImgFileFormat";

    /** Top-level metadata block. Only this subtree is parsed in tier-1. */
    public static final String ELEM_FILE_INFO = "FileInfo";

    // ─── FileInfo children parsed in tier-1 ──────────────────────────────────

    public static final String ELEM_CREATION_DATE = "CreationDate";
    public static final String ELEM_CREATING_VERSION = "CreatingVersion";
    public static final String ELEM_FRAME_RATE = "FrameRate";
    public static final String ELEM_WINDOW = "Window";
    public static final String ELEM_INTEGRATION_TIME = "IntegrationTime";
    public static final String ELEM_EXCITATION_DEVICE_SELECTION = "ExcitationDeviceSelection";
    public static final String ELEM_EXCITATION_AMPLITUDE = "ExcitationAmplitude";
    public static final String ELEM_RECORDING_TYPE = "RecordingType";
    public static final String ELEM_EXCITATION_FREQUENCY = "ExcitationFrequency";
    public static final String ELEM_CONDITION_PERIODS = "ConditionPeriods";
    public static final String ELEM_ACQUISITION_PERIODS = "AcquisitionPeriods";
    public static final String ELEM_EXCITATION_SIGNAL_TYPE = "ExcitationSignalType";
    public static final String ELEM_MODULE_NAME = "ModuleName";
    public static final String ELEM_CAMPAIGN = "Campaign";
}
