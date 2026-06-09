package de.dlr.shepard.v2.shapes.mffd;

import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;

/**
 * MFFD-TPL-APPROVAL-GATE — DATAOBJECT_RECIPE shape for {@code mffd:approval-gate}.
 *
 * <p>Seeds via {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} (B7 pattern) a
 * system-tagged {@code DATAOBJECT_RECIPE} template named {@code mffd-approval-gate-data-shape}.
 *
 * <p>An approval-gate DataObject records the EN 9100 disposition decision at a quality gate
 * in the MFFD production chain — e.g. at the exit of an AFP course, after an NDT tile
 * measurement, or at final assembly. It carries:
 * <ul>
 *   <li>The gate type (which step in the process chain this gate guards).</li>
 *   <li>The disposition outcome ({@code accept-as-is}, {@code rework}, {@code repair},
 *       or {@code scrap}) per AS9100 §8.7 non-conforming output vocabulary.</li>
 *   <li>The inspector identifier (person or system that issued the decision).</li>
 *   <li>The approval date (ISO 8601 date of the decision).</li>
 *   <li>An IRI back-reference to a related NCR DataObject (when disposition ≠
 *       {@code accept-as-is}).</li>
 *   <li>An IRI back-reference to the signed evidence FileReference (e.g. a scanned
 *       inspection record or a PDF concession letter).</li>
 * </ul>
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}.
 */
public final class MffdApprovalGateKind implements PayloadKind {

  static final String SHAPE_IRI = "urn:shepard:shape:mffd-approval-gate";

  // Quality-gate identifier: which step's exit this gate guards.
  static final String PRED_GATE_TYPE       = "urn:shepard:mffd:gate-type";
  // EN 9100 / AS9100 §8.7 disposition for non-conforming output.
  static final String PRED_DISPOSITION     = "urn:shepard:mffd:disposition";
  // Inspector or system that issued the decision (e.g. employee ID or tool name).
  static final String PRED_INSPECTOR_ID    = "urn:shepard:mffd:inspector-id";
  // Date the decision was recorded (xsd:date).
  static final String PRED_APPROVAL_DATE   = "urn:shepard:mffd:approval-date";
  // IRI of the NCR DataObject created when disposition ≠ accept-as-is.
  static final String PRED_RELATED_NCR     = "urn:shepard:mffd:related-ncr";
  // IRI of the FileReference carrying the signed evidence document.
  static final String PRED_EVIDENCE_FILEREF = "urn:shepard:mffd:evidence-fileref";

  static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
  static final String XSD_DATE   = "http://www.w3.org/2001/XMLSchema#date";

  @Override
  public String name() {
    return "mffd-approval-gate";
  }

  @Override
  public List<String> entityPackages() {
    return List.of();
  }

  /**
   * MFFD-TPL-APPROVAL-GATE — SHACL NodeShape for approval-gate DataObjects.
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
        // Which production step this gate guards (free text, e.g. "afp-course-exit").
        new PropertyShapeSpec(PRED_GATE_TYPE, XSD_STRING, 0, 1, null, null),
        // AS9100 §8.7 disposition vocabulary.
        new PropertyShapeSpec(
          PRED_DISPOSITION, XSD_STRING, 0, 1,
          List.of(
            InMember.literal("accept-as-is"),
            InMember.literal("rework"),
            InMember.literal("repair"),
            InMember.literal("scrap")
          ),
          null
        ),
        // Inspector or automated system that issued the decision.
        new PropertyShapeSpec(PRED_INSPECTOR_ID, XSD_STRING, 0, 1, null, null),
        // ISO 8601 date the decision was recorded (xsd:date).
        new PropertyShapeSpec(PRED_APPROVAL_DATE, XSD_DATE, 0, 1, null, null),
        // IRI of the NCR DataObject (present when disposition ≠ accept-as-is).
        new PropertyShapeSpec(PRED_RELATED_NCR, null, 0, 1, null, null),
        // IRI of the FileReference carrying the signed evidence document.
        new PropertyShapeSpec(PRED_EVIDENCE_FILEREF, null, 0, 1, null, null)
      )
    );
  }
}
