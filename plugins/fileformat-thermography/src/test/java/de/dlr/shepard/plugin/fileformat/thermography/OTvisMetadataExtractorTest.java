package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OTvisMetadataExtractorTest {

    /**
     * Mirror of the FileInfo block in the real
     * {@code sample_S4_M13_L18_F4.OTvis} fixture (see content.xml on
     * lines 1-73 of the extracted archive). Re-built as a Java string
     * so the test runs independently of the binary fixture.
     */
    private static final String SAMPLE_CONTENT_XML = "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<DisplayImgFileFormat>" +
            "<FileInfo>" +
            "<UniqueIdentifier>7ea9fd38-e264-46fb-ae05-e76dd2787d52</UniqueIdentifier>" +
            "<CreationDate>02/07/2023 06:55:41.414</CreationDate>" +
            "<CreatingVersion>7.0.425.8903</CreatingVersion>" +
            "<FrameRate>30Hz</FrameRate>" +
            "<Window>0,0,1024,768</Window>" +
            "<IntegrationTime>0.007s</IntegrationTime>" +
            "<ExcitationDeviceSelection>Halogen lamps</ExcitationDeviceSelection>" +
            "<ExcitationAmplitude>70.00 %</ExcitationAmplitude>" +
            "<RecordingType>Evaluation</RecordingType>" +
            "<ExcitationFrequency>0.015Hz</ExcitationFrequency>" +
            "<ConditionPeriods>1</ConditionPeriods>" +
            "<AcquisitionPeriods>2</AcquisitionPeriods>" +
            "<ExcitationSignalType>Sine</ExcitationSignalType>" +
            "<ModuleName>OTvis</ModuleName>" +
            "<Campaign>MFFD</Campaign>" +
            "</FileInfo>" +
            "</DisplayImgFileFormat>";

    private static byte[] utf16Le(String s) {
        // BOM + UTF-16 LE body, mimicking the real Edevis encoding.
        byte[] body = s.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xFE;
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    @Test
    void extractsAllAcquisitionFields() {
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(SAMPLE_CONTENT_XML));

        assertThat(meta).containsEntry(ThermographyAnnotations.FRAME_RATE_HZ, "30");
        assertThat(meta).containsEntry(ThermographyAnnotations.INTEGRATION_TIME_S, "0.007");
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_DEVICE, "halogen");
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_FREQUENCY_HZ, "0.015");
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_AMPLITUDE_PCT, "70.00");
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_SIGNAL_TYPE, "sine");
        assertThat(meta).containsEntry(ThermographyAnnotations.RECORDING_TYPE, "evaluation");
        assertThat(meta).containsEntry(ThermographyAnnotations.RESOLUTION, "1024x768");
        assertThat(meta).containsEntry(ThermographyAnnotations.CONDITIONING_PERIODS, "1");
        assertThat(meta).containsEntry(ThermographyAnnotations.ACQUISITION_PERIODS, "2");
        assertThat(meta).containsEntry(ThermographyAnnotations.CAMPAIGN, "MFFD");
        assertThat(meta).containsEntry(ThermographyAnnotations.MODULE_NAME, "OTvis");
        assertThat(meta).containsEntry(ThermographyAnnotations.CREATING_VERSION, "7.0.425.8903");
    }

    @Test
    void normalisesCreationDateToIso8601Utc() {
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(SAMPLE_CONTENT_XML));
        // Edevis format dd/MM/yyyy → 2023-07-02
        assertThat(meta.get(ThermographyAnnotations.CREATED_AT))
                .isNotNull()
                .startsWith("2023-07-02T06:55:41.414");
    }

    @Test
    void canonicaliseExcitationDeviceFlash() {
        String xml = SAMPLE_CONTENT_XML.replace(
                "<ExcitationDeviceSelection>Halogen lamps</ExcitationDeviceSelection>",
                "<ExcitationDeviceSelection>Flash bank</ExcitationDeviceSelection>");
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(xml));
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_DEVICE, "flash");
    }

    @Test
    void canonicaliseExcitationDeviceUltrasound() {
        String xml = SAMPLE_CONTENT_XML.replace(
                "<ExcitationDeviceSelection>Halogen lamps</ExcitationDeviceSelection>",
                "<ExcitationDeviceSelection>Ultrasound horn</ExcitationDeviceSelection>");
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(xml));
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_DEVICE, "ultrasound");
    }

    @Test
    void emptyByteArrayReturnsEmptyMap() {
        assertThat(OTvisMetadataExtractor.extract(new byte[0])).isEmpty();
        assertThat(OTvisMetadataExtractor.extract(null)).isEmpty();
    }

    @Test
    void missingFieldsAreSilentlyDropped() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<DisplayImgFileFormat><FileInfo>"
                + "<FrameRate>15Hz</FrameRate>"
                + "</FileInfo></DisplayImgFileFormat>";
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(xml));
        assertThat(meta).containsEntry(ThermographyAnnotations.FRAME_RATE_HZ, "15");
        assertThat(meta).doesNotContainKey(ThermographyAnnotations.INTEGRATION_TIME_S);
        assertThat(meta).doesNotContainKey(ThermographyAnnotations.RESOLUTION);
    }

    @Test
    void malformedXmlReturnsEmptyMap() {
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le("not xml at all"));
        assertThat(meta).isEmpty();
    }

    @Test
    void unknownExcitationDevicePassesThroughLowerCased() {
        String xml = SAMPLE_CONTENT_XML.replace(
                "<ExcitationDeviceSelection>Halogen lamps</ExcitationDeviceSelection>",
                "<ExcitationDeviceSelection>Laser Diode Stack</ExcitationDeviceSelection>");
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(xml));
        assertThat(meta).containsEntry(ThermographyAnnotations.EXCITATION_DEVICE, "laser diode stack");
    }

    @Test
    void resolutionNonNumericWindowDropped() {
        String xml = SAMPLE_CONTENT_XML.replace(
                "<Window>0,0,1024,768</Window>",
                "<Window>top,left,wide,tall</Window>");
        Map<String, String> meta = OTvisMetadataExtractor.extract(utf16Le(xml));
        assertThat(meta).doesNotContainKey(ThermographyAnnotations.RESOLUTION);
    }
}
