package de.dlr.shepard.v2.shapes.mffd;

import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;

/**
 * MFFD-TPL-WELD-STEP — DATAOBJECT_RECIPE shape for {@code mffd:weld-step}.
 *
 * <p>Seeds via {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} (B7 pattern) a
 * system-tagged {@code DATAOBJECT_RECIPE} template named {@code mffd-weld-step-data-shape}.
 * A single shape covers all three MFFD welding technologies (ultrasonic / resistance / stud)
 * via the controlled {@code weldSubtype} field; the {@code weldMode} field distinguishes
 * spot (discrete point) from continuous (scan-path) ultrasonics.
 *
 * <p>Continuous-mode passes (1teBahn / 2teBahn in the stringer corpus) are distinguished by
 * {@code weld-pass ∈ {1, 2}}; spot welds leave that field absent ({@code sh:minCount 0}).
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}.
 */
public final class MffdWeldStepKind implements PayloadKind {

  static final String SHAPE_IRI = "urn:shepard:shape:mffd-weld-step";

  static final String PRED_WELD_SUBTYPE   = "urn:shepard:mffd:weld-subtype";
  static final String PRED_WELD_MODE      = "urn:shepard:mffd:weld-mode";
  static final String PRED_WELD_ENERGY    = "urn:shepard:mffd:weld-energy";
  static final String PRED_WELD_FORCE     = "urn:shepard:mffd:weld-force";
  static final String PRED_WELD_DWELL     = "urn:shepard:mffd:weld-dwell";
  static final String PRED_JOINT_ID       = "urn:shepard:mffd:joint-id";
  static final String PRED_WELD_PASS      = "urn:shepard:mffd:weld-pass";
  static final String PRED_MATERIAL_BATCH = "urn:shepard:mffd:material-batch";

  static final String XSD_STRING  = "http://www.w3.org/2001/XMLSchema#string";
  static final String XSD_DECIMAL = "http://www.w3.org/2001/XMLSchema#decimal";
  static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";

  @Override
  public String name() {
    return "mffd-weld-step";
  }

  @Override
  public List<String> entityPackages() {
    return List.of();
  }

  /**
   * MFFD-TPL-WELD-STEP — SHACL NodeShape for weld-step DataObjects.
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
        // Welding technology: ultrasonic (stringer/spot), resistance (frame), stud (cleats).
        new PropertyShapeSpec(
          PRED_WELD_SUBTYPE, XSD_STRING, 0, 1,
          List.of(
            InMember.literal("ultrasonic"),
            InMember.literal("resistance"),
            InMember.literal("stud")
          ),
          null
        ),
        // Welding pattern: spot (discrete) or continuous (scan path, 1teBahn / 2teBahn).
        new PropertyShapeSpec(
          PRED_WELD_MODE, XSD_STRING, 0, 1,
          List.of(
            InMember.literal("spot"),
            InMember.literal("continuous")
          ),
          null
        ),
        // Welding energy setpoint (kJ, xsd:decimal).
        new PropertyShapeSpec(PRED_WELD_ENERGY, XSD_DECIMAL, 0, 1, null, null),
        // Consolidation force setpoint (N, xsd:decimal).
        new PropertyShapeSpec(PRED_WELD_FORCE, XSD_DECIMAL, 0, 1, null, null),
        // Dwell time at weld location (ms, xsd:decimal).
        new PropertyShapeSpec(PRED_WELD_DWELL, XSD_DECIMAL, 0, 1, null, null),
        // Joint / seam identifier (e.g. stringer position "P02Strich_S").
        new PropertyShapeSpec(PRED_JOINT_ID, XSD_STRING, 0, 1, null, null),
        // Pass within a continuous seam: 1 (1teBahn) or 2 (2teBahn).
        // Absent for spot welds (sh:minCount 0).
        new PropertyShapeSpec(
          PRED_WELD_PASS, XSD_INTEGER, 0, 1,
          List.of(
            InMember.literal("1", XSD_INTEGER),
            InMember.literal("2", XSD_INTEGER)
          ),
          null
        ),
        // IRI of the material-batch DataObject consumed by this weld step.
        // Enables "show all weld steps from batch X" queries.
        new PropertyShapeSpec(PRED_MATERIAL_BATCH, null, 0, 1, null, null)
      )
    );
  }
}
