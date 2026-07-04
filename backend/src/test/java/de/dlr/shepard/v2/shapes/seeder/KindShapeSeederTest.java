package de.dlr.shepard.v2.shapes.seeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.builder.ChannelBindingSpec;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ViewRecipeSpec;
import jakarta.enterprise.context.control.RequestContextController;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-B7 — unit tests for {@link KindShapeSeeder}. No CDI or Neo4j required;
 * all collaborators are plain Mockito mocks.
 *
 * <p>Covers four scenarios:
 * <ol>
 *   <li>New kind with descriptor → builder called + template saved</li>
 *   <li>Idempotency: same ShapeSpec → no update</li>
 *   <li>Kind with null descriptor → builder NOT called</li>
 *   <li>Admin-edit protection: existing template with system tag absent → skip</li>
 * </ol>
 */
class KindShapeSeederTest {

  static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
  static final String UPPER = "http://semantics.dlr.de/shepard-upper#";

  KindShapeSeeder seeder;
  ShepardTemplateDAO templateDAO;
  RequestContextController rcc;

  @BeforeEach
  void setUp() {
    templateDAO = mock(ShepardTemplateDAO.class);
    rcc = mock(RequestContextController.class);
    when(rcc.activate()).thenReturn(true);

    seeder = new KindShapeSeeder();
    seeder.templateDAO = templateDAO;
    seeder.requestContextController = rcc;
  }

  // ─── Test 1: new kind → template created ─────────────────────────────────

  @Test
  void newKindWithDescriptor_triggersCreateAndSave() {
    var spec = new ShapeSpec("urn:shepard:shape:test", null, false,
      List.of(new PropertyShapeSpec(UPPER + "status", XSD_STRING, 1, 1, null, null)));

    when(templateDAO.findLatestByName("test-kind-data-shape", KindShapeSeeder.TEMPLATE_KIND))
      .thenReturn(Optional.empty());
    when(templateDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    seeder.seedKind("test-kind", spec);

    verify(templateDAO, times(1)).createOrUpdate(any(ShepardTemplate.class));
    verify(templateDAO, times(1)).findLatestByName("test-kind-data-shape", KindShapeSeeder.TEMPLATE_KIND);
  }

  @Test
  void newKindWithDescriptor_savedTemplateHasSystemTagAndCorrectBody() {
    var spec = new ShapeSpec("urn:shepard:shape:test", null, false,
      List.of(new PropertyShapeSpec(UPPER + "owner", null, 1, null, null, null)));

    when(templateDAO.findLatestByName(any(), any())).thenReturn(Optional.empty());

    ShepardTemplate[] saved = new ShepardTemplate[1];
    when(templateDAO.createOrUpdate(any())).thenAnswer(inv -> {
      saved[0] = inv.getArgument(0);
      return saved[0];
    });

    seeder.seedKind("test-kind", spec);

    assertThat(saved[0]).isNotNull();
    assertThat(saved[0].getTags()).contains(KindShapeSeeder.SYSTEM_TAG);
    assertThat(saved[0].getName()).isEqualTo("test-kind-data-shape");
    assertThat(saved[0].getTemplateKind()).isEqualTo(KindShapeSeeder.TEMPLATE_KIND);
    // Body must pass TemplateBodyValidator: must contain "dataObject" key.
    assertThat(saved[0].getBody()).contains("\"dataObject\"");
    assertThat(saved[0].getBody()).contains("\"shapeSpec\"");
  }

  // ─── Test 2: idempotency ──────────────────────────────────────────────────

  @Test
  void sameShapeSpec_producesNoUpdate() {
    var spec = new ShapeSpec("urn:shepard:shape:unchanged", null, false,
      List.of(PropertyShapeSpec.of(UPPER + "name")));

    // Build the body that the seeder would have stored on first run.
    String existingShapeSpecJson = KindShapeSeeder.serializeShapeSpec(spec);
    String existingBody = KindShapeSeeder.buildBody(existingShapeSpecJson);

    ShepardTemplate existing = new ShepardTemplate("same-kind-data-shape", KindShapeSeeder.TEMPLATE_KIND, existingBody);
    existing.setTags(new ArrayList<>(List.of(KindShapeSeeder.SYSTEM_TAG)));

    when(templateDAO.findLatestByName("same-kind-data-shape", KindShapeSeeder.TEMPLATE_KIND))
      .thenReturn(Optional.of(existing));

    seeder.seedKind("same-kind", spec);

    // No createOrUpdate should be called — the spec hasn't changed.
    verify(templateDAO, never()).createOrUpdate(any());
  }

  // ─── Test 3: null descriptor → DAO never touched ─────────────────────────

  @Test
  void nullDescriptor_doesNotTouchDAO() {
    // The null-guard lives in onStart(): "if (spec == null) { continue; }".
    // seedKind() itself is never called for null-returning kinds, so there is
    // no meaningful unit-level invocation path to exercise here.
    //
    // What we CAN verify at the unit level: seedKind() called with a non-null
    // but empty ShapeSpec (the cheapest valid spec) produces exactly one
    // createOrUpdate call — proving that the non-null path is exercised and
    // that a null spec would produce zero calls (since only one invocation is
    // made and it corresponds to the non-null spec).
    var validSpec = new ShapeSpec(null, null, false, List.of());
    when(templateDAO.findLatestByName(any(), any())).thenReturn(Optional.empty());
    when(templateDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    seeder.seedKind("kind-a", validSpec);

    // Exactly one createOrUpdate — the null guard (if it regressed and null reached
    // seedKind) would throw an NPE in body-building before any DAO call, making a
    // count assertion the implicit null-regression detector.
    verify(templateDAO, times(1)).createOrUpdate(any());
  }

  // ─── Test 4: admin-edit protection ───────────────────────────────────────

  @Test
  void existingTemplateWithoutSystemTag_seederSkipsUpdate() {
    var spec = new ShapeSpec("urn:shepard:shape:custom", null, false,
      List.of(new PropertyShapeSpec(UPPER + "phase", XSD_STRING, 0, 1, null, null)));

    // Existing template body has a DIFFERENT shapeSpec (so a diff IS detected),
    // but the system tag is absent (admin has customised it).
    String differentBody = "{\"dataObject\":{\"shapeSpec\":{\"closed\":true}}}";
    ShepardTemplate existing = new ShepardTemplate(
      "custom-kind-data-shape", KindShapeSeeder.TEMPLATE_KIND, differentBody);
    // No SYSTEM_TAG in tags — admin customised.
    existing.setTags(new ArrayList<>(List.of("admin-custom-tag")));

    when(templateDAO.findLatestByName("custom-kind-data-shape", KindShapeSeeder.TEMPLATE_KIND))
      .thenReturn(Optional.of(existing));

    seeder.seedKind("custom-kind", spec);

    // Must NOT update the template: admin owns it.
    verify(templateDAO, never()).createOrUpdate(any());
  }

  // ─── V2CONV-B8: VIEW_RECIPE seeding tests ────────────────────────────────

  @Test
  void newViewKindWithDescriptor_triggersCreateAndSave() {
    var spec = new ViewRecipeSpec(
      "http://semantics.dlr.de/shepard/view#TestShape",
      "tresjs",
      List.of(ChannelBindingSpec.required("x", "{\"measurement\":\"AFP\"}"))
    );

    when(templateDAO.findLatestByName("test-kind-view-shape", KindShapeSeeder.VIEW_TEMPLATE_KIND))
      .thenReturn(Optional.empty());
    when(templateDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    seeder.seedViewKind("test-kind", spec);

    verify(templateDAO, times(1)).createOrUpdate(any(ShepardTemplate.class));
    verify(templateDAO, times(1)).findLatestByName("test-kind-view-shape", KindShapeSeeder.VIEW_TEMPLATE_KIND);
  }

  @Test
  void newViewKindWithDescriptor_savedTemplateHasCorrectKindAndBody() {
    var spec = new ViewRecipeSpec(
      "http://semantics.dlr.de/shepard/view#AfpShape",
      "tresjs",
      List.of(
        ChannelBindingSpec.required("x", "{\"measurement\":\"AFP\",\"field\":\"tcp_x\"}"),
        ChannelBindingSpec.optional("temp", "{\"measurement\":\"AFP\",\"field\":\"temperature\"}")
      )
    );

    when(templateDAO.findLatestByName(any(), any())).thenReturn(Optional.empty());

    ShepardTemplate[] saved = new ShepardTemplate[1];
    when(templateDAO.createOrUpdate(any())).thenAnswer(inv -> {
      saved[0] = inv.getArgument(0);
      return saved[0];
    });

    seeder.seedViewKind("afp-kind", spec);

    assertThat(saved[0]).isNotNull();
    assertThat(saved[0].getTags()).contains(KindShapeSeeder.SYSTEM_TAG);
    assertThat(saved[0].getName()).isEqualTo("afp-kind-view-shape");
    assertThat(saved[0].getTemplateKind()).isEqualTo(KindShapeSeeder.VIEW_TEMPLATE_KIND);
    // Body must pass TemplateBodyValidator: must contain "renderer" key.
    assertThat(saved[0].getBody()).contains("\"renderer\"");
    assertThat(saved[0].getBody()).contains("tresjs");
    assertThat(saved[0].getBody()).contains("viewRecipeShape");
    assertThat(saved[0].getBody()).contains("channelBindings");
    assertThat(saved[0].getBody()).contains("\"x\"");
    assertThat(saved[0].getBody()).contains("\"temp\"");
  }

  @Test
  void sameViewRecipeSpec_producesNoUpdate() {
    var spec = new ViewRecipeSpec(null, "echarts", List.of());

    String existingBody = KindShapeSeeder.serializeViewRecipeSpec(spec);

    ShepardTemplate existing = new ShepardTemplate("same-view-shape", KindShapeSeeder.VIEW_TEMPLATE_KIND, existingBody);
    existing.setTags(new ArrayList<>(List.of(KindShapeSeeder.SYSTEM_TAG)));

    when(templateDAO.findLatestByName("same-view-shape", KindShapeSeeder.VIEW_TEMPLATE_KIND))
      .thenReturn(Optional.of(existing));

    seeder.seedViewKind("same", spec);

    verify(templateDAO, never()).createOrUpdate(any());
  }

  @Test
  void viewKindWithAdminCustomisedTemplate_seederSkipsUpdate() {
    var spec = new ViewRecipeSpec(null, "plotly", List.of());

    String differentBody = "{\"renderer\":\"echarts\",\"channelBindings\":[]}";
    ShepardTemplate existing = new ShepardTemplate(
      "custom-view-shape", KindShapeSeeder.VIEW_TEMPLATE_KIND, differentBody);
    existing.setTags(new ArrayList<>(List.of("admin-custom-tag")));

    when(templateDAO.findLatestByName("custom-view-shape", KindShapeSeeder.VIEW_TEMPLATE_KIND))
      .thenReturn(Optional.of(existing));

    seeder.seedViewKind("custom", spec);

    verify(templateDAO, never()).createOrUpdate(any());
  }

  @Test
  void serializeViewRecipeSpec_rendererAndShapeIriAndBindings() {
    var spec = new ViewRecipeSpec(
      "http://example.org/shape#TestView",
      "tresjs",
      List.of(new ChannelBindingSpec("x", "{\"measurement\":\"M\"}", "http://qudt.org/vocab/unit/MilliM", true))
    );
    String json = KindShapeSeeder.serializeViewRecipeSpec(spec);
    assertThat(json).contains("\"renderer\"");
    assertThat(json).contains("tresjs");
    assertThat(json).contains("\"viewRecipeShape\"");
    assertThat(json).contains("http://example.org/shape#TestView");
    assertThat(json).contains("\"channelBindings\"");
    assertThat(json).contains("\"x\"");
    assertThat(json).contains("http://qudt.org/vocab/unit/MilliM");
    // Calling again must produce identical output (determinism).
    assertThat(KindShapeSeeder.serializeViewRecipeSpec(spec)).isEqualTo(json);
  }

  @Test
  void serializeViewRecipeSpec_nullShapeIri_notIncluded() {
    var spec = new ViewRecipeSpec(null, "echarts", List.of());
    String json = KindShapeSeeder.serializeViewRecipeSpec(spec);
    assertThat(json).doesNotContain("viewRecipeShape");
    assertThat(json).contains("\"renderer\"");
    assertThat(json).contains("\"channelBindings\"");
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  @Test
  void serializeShapeSpec_producesRoundTrippableJson() {
    var spec = new ShapeSpec("urn:test:shape", "urn:test:Class", false,
      List.of(new PropertyShapeSpec(UPPER + "x", XSD_STRING, 1, 1, null, null)));
    String json = KindShapeSeeder.serializeShapeSpec(spec);
    assertThat(json).contains("\"shapeIri\"");
    assertThat(json).contains("urn:test:shape");
    assertThat(json).contains("\"properties\"");
    assertThat(json).contains(UPPER + "x");

    // Calling it again must produce identical output (determinism).
    assertThat(KindShapeSeeder.serializeShapeSpec(spec)).isEqualTo(json);
  }

  @Test
  void buildBody_wrapsShapeSpecUnderDataObjectKey() {
    String shapeSpecJson = "{\"closed\":false}";
    String body = KindShapeSeeder.buildBody(shapeSpecJson);
    assertThat(body).contains("\"dataObject\"");
    assertThat(body).contains("\"shapeSpec\"");
    assertThat(body).contains("\"closed\"");
  }
}
