package de.dlr.shepard.v2.template.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.builder.FormHintSpec;
import de.dlr.shepard.v2.shapes.builder.GroupSpec;
import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FORM-DESCRIPTOR-1 round-trip tests: builder-emitted SHACL → descriptor
 * (the doc 125 §5.1 contract). The input shape is authored through the
 * {@link ShaclShapeBuilder} DSL — the BTKVS-B2 acceptance path, never
 * hand-written Turtle.
 */
class FormDescriptorCompilerTest {

  static final String SHAPE_IRI = "urn:btkvs:shape:docket-general";
  static final String GROUP_IRI = "urn:btkvs:group:identity";
  static final String ATTR = "urn:shepard:attribute:";
  static final String XSD = "http://www.w3.org/2001/XMLSchema#";
  static final String DASH = "http://datashapes.org/dash#";

  final ShaclShapeBuilder builder = new ShaclShapeBuilder();
  final FormDescriptorCompiler compiler = new FormDescriptorCompiler();

  ShepardTemplate template;

  @BeforeEach
  void setUp() {
    template = new ShepardTemplate("Docket — general section", "STRUCTURED_RECIPE", "{}");
    template.setAppId("tmpl-docket-general");
  }

  /** The docket {@code :general} shape — the BTKVS-B2 acceptance artifact. */
  static ShapeSpec docketGeneralShape() {
    return new ShapeSpec(
      SHAPE_IRI,
      null,
      false,
      List.of(
        new PropertyShapeSpec(
          ATTR + "docket_id",
          XSD + "string",
          1,
          1,
          null,
          null,
          "^[A-Z][0-9]{3}$",
          new FormHintSpec(
            "Docket ID",
            null,
            1.0,
            GROUP_IRI,
            null,
            DASH + "TextFieldEditor",
            null,
            "I123",
            null,
            new FormHintSpec.CellMappingSpec("Laufzettel C-C bzw C-C-SiC", "K1")
          )
        ),
        new PropertyShapeSpec(
          ATTR + "project",
          XSD + "string",
          1,
          null,
          null,
          null,
          null,
          new FormHintSpec(
            "Project",
            null,
            2.0,
            GROUP_IRI,
            null,
            DASH + "TextFieldEditor",
            null,
            null,
            null,
            new FormHintSpec.CellMappingSpec(null, "C4")
          )
        ),
        new PropertyShapeSpec(
          ATTR + "project_lead",
          XSD + "string",
          null,
          null,
          null,
          null,
          null,
          new FormHintSpec("Project lead", null, 3.0, GROUP_IRI, null, DASH + "TextFieldEditor", null, null, null, null)
        ),
        new PropertyShapeSpec(
          ATTR + "delivery_date",
          XSD + "date",
          null,
          null,
          null,
          null,
          null,
          new FormHintSpec("Delivery date", null, 4.0, null, null, null, null, null, null, null)
        ),
        new PropertyShapeSpec(
          ATTR + "ktr",
          XSD + "integer",
          null,
          null,
          null,
          null,
          null,
          new FormHintSpec("KTR (cost centre)", null, 5.0, null, null, null, null, "123456", null, null)
        ),
        new PropertyShapeSpec(
          ATTR + "comments",
          XSD + "string",
          null,
          null,
          null,
          null,
          null,
          new FormHintSpec("Comments", null, 6.0, null, null, null, Boolean.FALSE, null, null, null)
        )
      ),
      List.of(new GroupSpec(GROUP_IRI, "Identity", 1.0)),
      "urn:shepard:instance:candidate"
    );
  }

  TemplateFormDescriptorIO compileDocket() {
    return compiler.compile(template, builder.toTurtle(docketGeneralShape()));
  }

  @Test
  void descriptorCarriesTemplateIdentityAndShapeIri() {
    var d = compileDocket();
    assertThat(d.templateAppId()).isEqualTo("tmpl-docket-general");
    assertThat(d.templateKind()).isEqualTo("STRUCTURED_RECIPE");
    assertThat(d.title()).isEqualTo("Docket — general section");
    assertThat(d.shapeIri()).isEqualTo(SHAPE_IRI);
  }

  @Test
  void fieldsAreOrderedByShOrder() {
    var d = compileDocket();
    assertThat(d.fields()).extracting(TemplateFormDescriptorIO.FieldIO::label)
      .containsExactly("Docket ID", "Project", "Project lead", "Delivery date", "KTR (cost centre)", "Comments");
  }

  @Test
  void docketIdFieldSurfacesEveryHint() {
    var f = compileDocket().fields().get(0);
    assertThat(f.path()).isEqualTo(ATTR + "docket_id");
    assertThat(f.attributeKey()).isEqualTo("docket_id");
    assertThat(f.required()).isTrue();
    assertThat(f.pattern()).isEqualTo("^[A-Z][0-9]{3}$");
    assertThat(f.editor()).isEqualTo(DASH + "TextFieldEditor");
    assertThat(f.placeholder()).isEqualTo("I123");
    assertThat(f.group()).isEqualTo(GROUP_IRI);
    assertThat(f.order()).isEqualTo(1.0);
    assertThat(f.cellMapping()).isNotNull();
    assertThat(f.cellMapping().sheet()).isEqualTo("Laufzettel C-C bzw C-C-SiC");
    assertThat(f.cellMapping().cell()).isEqualTo("K1");
  }

  @Test
  void secondCellMappingSurvivesWithoutSheet() {
    var project = compileDocket().fields().get(1);
    assertThat(project.cellMapping()).isNotNull();
    assertThat(project.cellMapping().cell()).isEqualTo("C4");
    assertThat(project.cellMapping().sheet()).isNull();
  }

  @Test
  void dashScoringDefaultsDateAndTextareaEditors() {
    var d = compileDocket();
    var byKey = d.fields();
    assertThat(byKey.get(3).editor()).isEqualTo(DASH + "DatePickerEditor"); // delivery_date — xsd:date, no explicit editor
    assertThat(byKey.get(4).editor()).isEqualTo(DASH + "TextFieldEditor"); // ktr — plain
    assertThat(byKey.get(5).editor()).isEqualTo(DASH + "TextAreaEditor"); // comments — singleLine false
    assertThat(byKey.get(5).singleLine()).isFalse();
  }

  @Test
  void groupsAreSurfacedAndOrdered() {
    var d = compileDocket();
    assertThat(d.groups()).hasSize(1);
    assertThat(d.groups().get(0).id()).isEqualTo(GROUP_IRI);
    assertThat(d.groups().get(0).label()).isEqualTo("Identity");
    assertThat(d.groups().get(0).order()).isEqualTo(1.0);
  }

  @Test
  void submitBlockIsServerComputed() {
    var s = compileDocket().submit();
    assertThat(s.method()).isEqualTo("POST");
    assertThat(s.href()).isEqualTo("/v2/collections/{collectionAppId}/data-objects/from-template/tmpl-docket-general");
    assertThat(s.violationContract()).contains("violations[]");
  }

  @Test
  void shInBecomesEnumSelectWithOptions() {
    var spec = new ShapeSpec(
      "urn:test:shape:enum",
      null,
      false,
      List.of(
        new PropertyShapeSpec(
          ATTR + "status",
          null,
          null,
          null,
          List.of(
            new InMember("DRAFT", InMember.Kind.LITERAL, null),
            new InMember("READY", InMember.Kind.LITERAL, null)
          ),
          null,
          null,
          null
        )
      )
    );
    var d = compiler.compile(template, builder.toTurtle(spec));
    var f = d.fields().get(0);
    assertThat(f.editor()).isEqualTo(DASH + "EnumSelectEditor");
    assertThat(f.options()).containsExactly("DRAFT", "READY");
  }

  @Test
  void booleanDatatypeScoresBooleanSelect() {
    var spec = new ShapeSpec(
      "urn:test:shape:bool",
      null,
      false,
      List.of(new PropertyShapeSpec(ATTR + "flag", XSD + "boolean", null, null, null, null, null, null))
    );
    var d = compiler.compile(template, builder.toTurtle(spec));
    assertThat(d.fields().get(0).editor()).isEqualTo(DASH + "BooleanSelectEditor");
  }

  @Test
  void unhintedFieldFallsBackToAttributeKeyLabel() {
    var spec = new ShapeSpec(
      "urn:test:shape:plain",
      null,
      false,
      List.of(PropertyShapeSpec.of(ATTR + "ktr"))
    );
    var d = compiler.compile(template, builder.toTurtle(spec));
    var f = d.fields().get(0);
    assertThat(f.label()).isEqualTo("ktr");
    assertThat(f.required()).isNull();
    assertThat(f.editor()).isEqualTo(DASH + "TextFieldEditor");
  }

  @Test
  void unparseableTurtleIsRejected() {
    assertThatThrownBy(() -> compiler.compile(template, "this is not turtle @@@"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not parseable");
  }

  @Test
  void turtleWithoutNodeShapeIsRejected() {
    assertThatThrownBy(() -> compiler.compile(template, "@prefix ex: <http://example.org/> . ex:a ex:b ex:c ."))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("no sh:NodeShape");
  }

  @Test
  void compilationIsDeterministic() {
    String ttl = builder.toTurtle(docketGeneralShape());
    assertThat(compiler.compile(template, ttl)).isEqualTo(compiler.compile(template, ttl));
  }
}
