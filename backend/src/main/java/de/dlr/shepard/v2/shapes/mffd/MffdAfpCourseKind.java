package de.dlr.shepard.v2.shapes.mffd;

import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;

/**
 * MFFD-TPL-AFP-COURSE — DATAOBJECT_RECIPE shape for {@code mffd:afp-course}.
 *
 * <p>Seeds via {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} (B7 pattern) a
 * system-tagged {@code DATAOBJECT_RECIPE} template named {@code mffd-afp-course-data-shape}.
 * Each AFP course DataObject captures the key process parameters for one robot traverse
 * (ply ID, course ID, machine setpoints) plus a reference to the material batch it consumed,
 * enabling "show all courses from batch X" queries without ad-hoc text search.
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}.
 */
public final class MffdAfpCourseKind implements PayloadKind {

  static final String SHAPE_IRI = "urn:shepard:shape:mffd-afp-course";

  static final String PRED_TCP_PATH_REF        = "urn:shepard:mffd:tcp-path-ref";
  static final String PRED_TAPE_SPEED_SETPOINT = "urn:shepard:mffd:tape-speed-setpoint";
  static final String PRED_LASER_TEMP_SETPOINT = "urn:shepard:mffd:laser-temp-setpoint";
  static final String PRED_PLY_ID              = "urn:shepard:mffd:ply-id";
  static final String PRED_COURSE_ID           = "urn:shepard:mffd:course-id";
  static final String PRED_MATERIAL_BATCH      = "urn:shepard:mffd:material-batch";

  static final String XSD_STRING  = "http://www.w3.org/2001/XMLSchema#string";
  static final String XSD_DECIMAL = "http://www.w3.org/2001/XMLSchema#decimal";

  @Override
  public String name() {
    return "mffd-afp-course";
  }

  @Override
  public List<String> entityPackages() {
    return List.of();
  }

  /**
   * MFFD-TPL-AFP-COURSE — SHACL NodeShape for AFP course DataObjects.
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
        // IRI of the TimeseriesReference containing TCP path channels
        // (tcp_x_mm / tcp_y_mm / tcp_z_mm / tcp_rx_deg / tcp_ry_deg / tcp_rz_deg).
        // Resolved via GET /v2/references/{appId}.
        new PropertyShapeSpec(PRED_TCP_PATH_REF, null, 0, 1, null, null),
        // AFP robot tape-laying speed setpoint (m/min, xsd:decimal).
        new PropertyShapeSpec(PRED_TAPE_SPEED_SETPOINT, XSD_DECIMAL, 0, 1, null, null),
        // Laser consolidation temperature setpoint (°C, xsd:decimal).
        new PropertyShapeSpec(PRED_LASER_TEMP_SETPOINT, XSD_DECIMAL, 0, 1, null, null),
        // Ply identifier within the layup sequence (e.g. "ply-003").
        new PropertyShapeSpec(PRED_PLY_ID, XSD_STRING, 0, 1, null, null),
        // Course identifier within the ply (e.g. "AFP-AFPT-MTLH-S1-003-07").
        new PropertyShapeSpec(PRED_COURSE_ID, XSD_STRING, 0, 1, null, null),
        // IRI of the material-batch DataObject that supplied the tape for this course.
        // Links to an mffd:material-batch DataObject; resolved via /v2/data-objects/{appId}.
        new PropertyShapeSpec(PRED_MATERIAL_BATCH, null, 0, 1, null, null)
      )
    );
  }
}
