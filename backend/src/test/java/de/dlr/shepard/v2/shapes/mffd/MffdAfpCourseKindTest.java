package de.dlr.shepard.v2.shapes.mffd;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MFFD-TPL-AFP-COURSE — unit tests for {@link MffdAfpCourseKind}.
 * No CDI or Neo4j; verifies the static shape descriptor.
 */
class MffdAfpCourseKindTest {

  private final MffdAfpCourseKind kind = new MffdAfpCourseKind();

  @Test
  void name_returnsMffdAfpCourse() {
    assertThat(kind.name()).isEqualTo("mffd-afp-course");
  }

  @Test
  void entityPackages_isEmpty() {
    assertThat(kind.entityPackages()).isEmpty();
  }

  @Test
  void shapeDescriptor_isNotNull() {
    assertThat(kind.shapeDescriptor()).isNotNull();
  }

  @Test
  void shapeDescriptor_hasExpectedIri() {
    assertThat(kind.shapeDescriptor().shapeIri())
        .isEqualTo(MffdAfpCourseKind.SHAPE_IRI);
  }

  @Test
  void shapeDescriptor_isOpenShape() {
    assertThat(kind.shapeDescriptor().closed()).isFalse();
  }

  @Test
  void shapeDescriptor_hasSixProperties() {
    assertThat(kind.shapeDescriptor().properties()).hasSize(6);
  }

  @Test
  void shapeDescriptor_tcpPathRefIsIriValued() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdAfpCourseKind.PRED_TCP_PATH_REF);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isNull();
    assertThat(prop.in()).isNull();
  }

  @Test
  void shapeDescriptor_tapeSpeedSetpointIsDecimal() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdAfpCourseKind.PRED_TAPE_SPEED_SETPOINT,
        MffdAfpCourseKind.XSD_DECIMAL
    );
  }

  @Test
  void shapeDescriptor_laserTempSetpointIsDecimal() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdAfpCourseKind.PRED_LASER_TEMP_SETPOINT,
        MffdAfpCourseKind.XSD_DECIMAL
    );
  }

  @Test
  void shapeDescriptor_plyIdIsString() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdAfpCourseKind.PRED_PLY_ID,
        MffdAfpCourseKind.XSD_STRING
    );
  }

  @Test
  void shapeDescriptor_courseIdIsString() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdAfpCourseKind.PRED_COURSE_ID,
        MffdAfpCourseKind.XSD_STRING
    );
  }

  @Test
  void shapeDescriptor_materialBatchIsIriValued() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdAfpCourseKind.PRED_MATERIAL_BATCH);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isNull();
    assertThat(prop.in()).isNull();
  }

  @Test
  void shapeDescriptor_allPropertiesAreOptional() {
    List<PropertyShapeSpec> props = kind.shapeDescriptor().properties();
    assertThat(props).allMatch(p -> p.minCount() != null && p.minCount() == 0);
  }

  @Test
  void shapeDescriptor_allPropertiesHaveMaxCountOne() {
    List<PropertyShapeSpec> props = kind.shapeDescriptor().properties();
    assertThat(props).allMatch(p -> p.maxCount() != null && p.maxCount() == 1);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static void assertPropertyPresent(ShapeSpec spec, String path, String datatype) {
    PropertyShapeSpec found = findByPath(spec, path);
    assertThat(found).as("property with path=%s", path).isNotNull();
    assertThat(found.datatype()).isEqualTo(datatype);
  }

  private static PropertyShapeSpec findByPath(ShapeSpec spec, String path) {
    return spec.properties().stream()
        .filter(p -> path.equals(p.path()))
        .findFirst()
        .orElse(null);
  }
}
