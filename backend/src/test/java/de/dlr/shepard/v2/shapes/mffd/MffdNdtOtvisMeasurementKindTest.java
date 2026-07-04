package de.dlr.shepard.v2.shapes.mffd;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MFFD-TPL-NDT-OTVIS-MEASUREMENT — unit tests for {@link MffdNdtOtvisMeasurementKind}.
 * No CDI or Neo4j; verifies the static shape descriptor.
 */
class MffdNdtOtvisMeasurementKindTest {

  private final MffdNdtOtvisMeasurementKind kind = new MffdNdtOtvisMeasurementKind();

  @Test
  void name_returnsMffdNdtOtvisMeasurement() {
    assertThat(kind.name()).isEqualTo("mffd-ndt-otvis-measurement");
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
        .isEqualTo(MffdNdtOtvisMeasurementKind.SHAPE_IRI);
  }

  @Test
  void shapeDescriptor_isOpenShape() {
    assertThat(kind.shapeDescriptor().closed()).isFalse();
  }

  @Test
  void shapeDescriptor_hasNineProperties() {
    assertThat(kind.shapeDescriptor().properties()).hasSize(9);
  }

  @Test
  void shapeDescriptor_sectionIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_SECTION,
        MffdNdtOtvisMeasurementKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_moduleIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_MODULE,
        MffdNdtOtvisMeasurementKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_layerIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_LAYER,
        MffdNdtOtvisMeasurementKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_frameIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_FRAME,
        MffdNdtOtvisMeasurementKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_frameRateIsDecimal() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_FRAME_RATE,
        MffdNdtOtvisMeasurementKind.XSD_DECIMAL);
  }

  @Test
  void shapeDescriptor_cameraSetupIsString() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_CAMERA_SETUP,
        MffdNdtOtvisMeasurementKind.XSD_STRING);
  }

  @Test
  void shapeDescriptor_ambientTempIsDecimal() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_AMBIENT_TEMP,
        MffdNdtOtvisMeasurementKind.XSD_DECIMAL);
  }

  @Test
  void shapeDescriptor_ambientHumidityIsDecimal() {
    assertPropertyPresent(kind.shapeDescriptor(),
        MffdNdtOtvisMeasurementKind.PRED_NDT_AMBIENT_HUMIDITY,
        MffdNdtOtvisMeasurementKind.XSD_DECIMAL);
  }

  @Test
  void shapeDescriptor_sourceFilerefIsIriValued() {
    PropertyShapeSpec prop = findByPath(
        kind.shapeDescriptor(), MffdNdtOtvisMeasurementKind.PRED_NDT_SOURCE_FILEREF);
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
