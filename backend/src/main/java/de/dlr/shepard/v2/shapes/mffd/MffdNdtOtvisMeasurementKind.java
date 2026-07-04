package de.dlr.shepard.v2.shapes.mffd;

import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;

/**
 * MFFD-TPL-NDT-OTVIS-MEASUREMENT — DATAOBJECT_RECIPE shape for {@code mffd:ndt-otvis-measurement}.
 *
 * <p>Seeds via {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} (B7 pattern) a
 * system-tagged {@code DATAOBJECT_RECIPE} template named
 * {@code mffd-ndt-otvis-measurement-data-shape}.
 *
 * <p>Each NDT thermography DataObject captures:
 * <ul>
 *   <li>The (Section, Module, Layer, Frame) grid position on the MFFD upper-shell
 *       as parsed by {@code OTvisFilenameParser} (e.g. S4_M13_L18_F4) — the key
 *       join field linking an NDT tile to the AFP layup course at the same (S, M) location.</li>
 *   <li>Camera acquisition parameters (frame rate, camera setup identifier).</li>
 *   <li>Environmental conditions at measurement time (ambient temperature and humidity).</li>
 *   <li>An IRI back-reference to the source OTvis FileReference for raw data access.</li>
 * </ul>
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}.
 */
public final class MffdNdtOtvisMeasurementKind implements PayloadKind {

  static final String SHAPE_IRI = "urn:shepard:shape:mffd-ndt-otvis-measurement";

  // Grid position — four components as parsed by OTvisFilenameParser.
  // Values carry the literal prefix letter from the filename (e.g. "S4", "M13", "L18+", "F4")
  // for round-trip fidelity; annotation value matches the filename segment exactly.
  static final String PRED_NDT_SECTION  = "urn:shepard:mffd:ndt-section";
  static final String PRED_NDT_MODULE   = "urn:shepard:mffd:ndt-module";
  static final String PRED_NDT_LAYER    = "urn:shepard:mffd:ndt-layer";
  static final String PRED_NDT_FRAME    = "urn:shepard:mffd:ndt-frame";

  // Acquisition parameters.
  static final String PRED_NDT_FRAME_RATE    = "urn:shepard:mffd:ndt-frame-rate";
  static final String PRED_NDT_CAMERA_SETUP  = "urn:shepard:mffd:ndt-camera-setup";

  // Environmental conditions recorded at measurement time.
  static final String PRED_NDT_AMBIENT_TEMP     = "urn:shepard:mffd:ndt-ambient-temp";
  static final String PRED_NDT_AMBIENT_HUMIDITY = "urn:shepard:mffd:ndt-ambient-humidity";

  // IRI of the OTvis FileReference — resolved via GET /v2/files/{appId}/content.
  static final String PRED_NDT_SOURCE_FILEREF = "urn:shepard:mffd:ndt-source-fileref";

  static final String XSD_STRING  = "http://www.w3.org/2001/XMLSchema#string";
  static final String XSD_DECIMAL = "http://www.w3.org/2001/XMLSchema#decimal";

  @Override
  public String name() {
    return "mffd-ndt-otvis-measurement";
  }

  @Override
  public List<String> entityPackages() {
    return List.of();
  }

  /**
   * MFFD-TPL-NDT-OTVIS-MEASUREMENT — SHACL NodeShape for NDT OTvis measurement DataObjects.
   * Open shape ({@code sh:closed false}); all properties optional
   * ({@code sh:minCount 0}) so DataObjects lacking a field remain valid.
   */
  @Override
  public ShapeSpec shapeDescriptor() {
    return new ShapeSpec(
      SHAPE_IRI,
      null,
      false,
      List.of(
        // Section component from filename (e.g. "S4"). Matches OTvisFilenameParser output.
        new PropertyShapeSpec(PRED_NDT_SECTION, XSD_STRING, 0, 1, null, null),
        // Module component from filename (e.g. "M13").
        new PropertyShapeSpec(PRED_NDT_MODULE, XSD_STRING, 0, 1, null, null),
        // Layer component from filename (e.g. "L18" or "L19+" for planned extra layers).
        new PropertyShapeSpec(PRED_NDT_LAYER, XSD_STRING, 0, 1, null, null),
        // Frame component from filename (e.g. "F4").
        new PropertyShapeSpec(PRED_NDT_FRAME, XSD_STRING, 0, 1, null, null),
        // Camera acquisition frame rate (Hz, xsd:decimal).
        new PropertyShapeSpec(PRED_NDT_FRAME_RATE, XSD_DECIMAL, 0, 1, null, null),
        // Camera system identifier (free text, e.g. "Edevis-OTvis-S2-IR-uncooled").
        new PropertyShapeSpec(PRED_NDT_CAMERA_SETUP, XSD_STRING, 0, 1, null, null),
        // Ambient temperature at measurement time (°C, xsd:decimal).
        new PropertyShapeSpec(PRED_NDT_AMBIENT_TEMP, XSD_DECIMAL, 0, 1, null, null),
        // Relative ambient humidity at measurement time (%, xsd:decimal).
        new PropertyShapeSpec(PRED_NDT_AMBIENT_HUMIDITY, XSD_DECIMAL, 0, 1, null, null),
        // IRI of the source OTvis FileReference DataObject.
        // Resolved via GET /v2/files/{appId}/content for raw phase-image download.
        new PropertyShapeSpec(PRED_NDT_SOURCE_FILEREF, null, 0, 1, null, null)
      )
    );
  }
}
