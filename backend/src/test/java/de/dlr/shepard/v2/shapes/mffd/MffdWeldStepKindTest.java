package de.dlr.shepard.v2.shapes.mffd;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MFFD-TPL-WELD-STEP — unit tests for {@link MffdWeldStepKind}.
 * No CDI or Neo4j; verifies the static shape descriptor.
 */
class MffdWeldStepKindTest {

  private final MffdWeldStepKind kind = new MffdWeldStepKind();

  @Test
  void name_returnsMffdWeldStep() {
    assertThat(kind.name()).isEqualTo("mffd-weld-step");
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
        .isEqualTo(MffdWeldStepKind.SHAPE_IRI);
  }

  @Test
  void shapeDescriptor_isOpenShape() {
    assertThat(kind.shapeDescriptor().closed()).isFalse();
  }

  @Test
  void shapeDescriptor_hasEightProperties() {
    assertThat(kind.shapeDescriptor().properties()).hasSize(8);
  }

  @Test
  void shapeDescriptor_weldSubtypeHasThreeInMembers() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdWeldStepKind.PRED_WELD_SUBTYPE);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isEqualTo(MffdWeldStepKind.XSD_STRING);
    assertThat(prop.in()).hasSize(3);
    assertThat(prop.in())
        .extracting(InMember::value)
        .containsExactlyInAnyOrder("ultrasonic", "resistance", "stud");
  }

  @Test
  void shapeDescriptor_weldSubtypeInMembersAreLiterals() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdWeldStepKind.PRED_WELD_SUBTYPE);
    assertThat(prop).isNotNull();
    assertThat(prop.in()).allMatch(m -> m.kind() == InMember.Kind.LITERAL);
  }

  @Test
  void shapeDescriptor_weldModeHasTwoInMembers() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdWeldStepKind.PRED_WELD_MODE);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isEqualTo(MffdWeldStepKind.XSD_STRING);
    assertThat(prop.in()).hasSize(2);
    assertThat(prop.in())
        .extracting(InMember::value)
        .containsExactlyInAnyOrder("spot", "continuous");
  }

  @Test
  void shapeDescriptor_weldEnergyIsDecimal() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdWeldStepKind.PRED_WELD_ENERGY,
        MffdWeldStepKind.XSD_DECIMAL
    );
  }

  @Test
  void shapeDescriptor_weldForceIsDecimal() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdWeldStepKind.PRED_WELD_FORCE,
        MffdWeldStepKind.XSD_DECIMAL
    );
  }

  @Test
  void shapeDescriptor_weldDwellIsDecimal() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdWeldStepKind.PRED_WELD_DWELL,
        MffdWeldStepKind.XSD_DECIMAL
    );
  }

  @Test
  void shapeDescriptor_jointIdIsString() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdWeldStepKind.PRED_JOINT_ID,
        MffdWeldStepKind.XSD_STRING
    );
  }

  @Test
  void shapeDescriptor_weldPassHasTwoIntegerInMembers() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdWeldStepKind.PRED_WELD_PASS);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isEqualTo(MffdWeldStepKind.XSD_INTEGER);
    assertThat(prop.in()).hasSize(2);
    assertThat(prop.in())
        .extracting(InMember::value)
        .containsExactlyInAnyOrder("1", "2");
    assertThat(prop.in())
        .extracting(InMember::datatype)
        .allMatch(dt -> MffdWeldStepKind.XSD_INTEGER.equals(dt));
  }

  @Test
  void shapeDescriptor_materialBatchIsIriValued() {
    PropertyShapeSpec prop = findByPath(kind.shapeDescriptor(), MffdWeldStepKind.PRED_MATERIAL_BATCH);
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
