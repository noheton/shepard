package de.dlr.shepard.v2.shapes.mffd;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MFFD-TPL-APPROVAL-GATE — unit tests for {@link MffdApprovalGateKind}.
 * No CDI or Neo4j; verifies the static shape descriptor.
 */
class MffdApprovalGateKindTest {

  private final MffdApprovalGateKind kind = new MffdApprovalGateKind();

  @Test
  void name_returnsMffdApprovalGate() {
    assertThat(kind.name()).isEqualTo("mffd-approval-gate");
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
        .isEqualTo(MffdApprovalGateKind.SHAPE_IRI);
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
  void shapeDescriptor_gateTypeIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdApprovalGateKind.PRED_GATE_TYPE,
        MffdApprovalGateKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_dispositionIsStringWithFourMembers() {
    PropertyShapeSpec prop = findByPath(
        kind.shapeDescriptor(), MffdApprovalGateKind.PRED_DISPOSITION);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isEqualTo(MffdApprovalGateKind.XSD_STRING);
    assertThat(prop.in()).hasSize(4);
    assertThat(prop.in().stream().map(m -> m.value()))
        .containsExactlyInAnyOrder("accept-as-is", "rework", "repair", "scrap");
  }

  @Test
  void shapeDescriptor_inspectorIdIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdApprovalGateKind.PRED_INSPECTOR_ID,
        MffdApprovalGateKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_approvalDateIsDate() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdApprovalGateKind.PRED_APPROVAL_DATE,
        MffdApprovalGateKind.XSD_DATE);
  }

  @Test
  void shapeDescriptor_relatedNcrIsIriValued() {
    PropertyShapeSpec prop = findByPath(
        kind.shapeDescriptor(), MffdApprovalGateKind.PRED_RELATED_NCR);
    assertThat(prop).isNotNull();
    assertThat(prop.datatype()).isNull();
    assertThat(prop.in()).isNull();
  }

  @Test
  void shapeDescriptor_evidenceFilerefIsIriValued() {
    PropertyShapeSpec prop = findByPath(
        kind.shapeDescriptor(), MffdApprovalGateKind.PRED_EVIDENCE_FILEREF);
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
