package de.dlr.shepard.v2.shapes.mffd;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MFFD-TPL-MATERIAL-BATCH — unit tests for {@link MffdMaterialBatchKind}.
 * No CDI or Neo4j; verifies the static shape descriptor.
 */
class MffdMaterialBatchKindTest {

  private final MffdMaterialBatchKind kind = new MffdMaterialBatchKind();

  @Test
  void name_returnsMffdMaterialBatch() {
    assertThat(kind.name()).isEqualTo("mffd-material-batch");
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
        .isEqualTo(MffdMaterialBatchKind.SHAPE_IRI);
  }

  @Test
  void shapeDescriptor_isOpenShape() {
    assertThat(kind.shapeDescriptor().closed()).isFalse();
  }

  @Test
  void shapeDescriptor_hasSevenProperties() {
    assertThat(kind.shapeDescriptor().properties()).hasSize(7);
  }

  @Test
  void shapeDescriptor_containsBatchIdProperty() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdMaterialBatchKind.PRED_BATCH_ID,
        MffdMaterialBatchKind.XSD_STRING
    );
  }

  @Test
  void shapeDescriptor_materialClassHasSixInMembers() {
    ShapeSpec spec = kind.shapeDescriptor();
    PropertyShapeSpec mc = findByPath(spec, MffdMaterialBatchKind.PRED_MATERIAL_CLASS);
    assertThat(mc).isNotNull();
    assertThat(mc.in()).hasSize(6);
    assertThat(mc.in())
        .extracting(InMember::value)
        .containsExactlyInAnyOrder(
            "LMPAEK", "SAERTEX-NCF", "EPOXY-PREPREG",
            "GLASS-FIBER-NCF", "CARBON-FIBER-TAPE", "OTHER"
        );
  }

  @Test
  void shapeDescriptor_materialClassInMembersAreLiterals() {
    PropertyShapeSpec mc = findByPath(
        kind.shapeDescriptor(), MffdMaterialBatchKind.PRED_MATERIAL_CLASS);
    assertThat(mc).isNotNull();
    assertThat(mc.in()).allMatch(m -> m.kind() == InMember.Kind.LITERAL);
  }

  @Test
  void shapeDescriptor_containsManufacturedDateAsXsdDate() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdMaterialBatchKind.PRED_MANUFACTURED_DATE,
        MffdMaterialBatchKind.XSD_DATE
    );
  }

  @Test
  void shapeDescriptor_containsExpiryDateAsXsdDate() {
    assertPropertyPresent(
        kind.shapeDescriptor(),
        MffdMaterialBatchKind.PRED_EXPIRY_DATE,
        MffdMaterialBatchKind.XSD_DATE
    );
  }

  @Test
  void shapeDescriptor_certificateRefHasNoDatatypeIsIriValued() {
    PropertyShapeSpec certRef = findByPath(
        kind.shapeDescriptor(), MffdMaterialBatchKind.PRED_CERTIFICATE_REF);
    assertThat(certRef).isNotNull();
    assertThat(certRef.datatype()).isNull();
    assertThat(certRef.in()).isNull();
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
