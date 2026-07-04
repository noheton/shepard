package de.dlr.shepard.v2.shapes.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.shapes.io.ShapesApplicableResponseIO;
import de.dlr.shepard.v2.shapes.repositories.FocusEntityRepository;
import de.dlr.shepard.v2.shapes.repositories.FocusEntityRepository.FocusEntity;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * {@code GET /v2/shapes/applicable?focusAppId=…} — status-code contract +
 * VIEW/FORM determination (SHAPES-APPLICABLE-FORMS, doc 125 §5.3 / D4;
 * consumed by the FORM-UX-ACTIONBUTTON {@code ActionMenuButton}).
 */
class ShapesApplicableRestTest {

  static final String FOCUS = "019e0000-0000-7000-8000-000000000001";
  static final String VIEW_TMPL = "tmpl-view-trace3d";
  static final String FORM_TMPL = "tmpl-form-docket";
  static final String COLLECTION = "019e0000-0000-7000-8000-00000000c011";

  @Mock
  FocusEntityRepository focusRepository;

  @Mock
  ShepardTemplateDAO templateDAO;

  ShapesApplicableRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ShapesApplicableRest();
    resource.focusRepository = focusRepository;
    resource.templateDAO = templateDAO;
    resource.inheritanceResolver = new TemplateInheritanceResolver(templateDAO);
  }

  // ─── fixtures ────────────────────────────────────────────────────────────

  ShepardTemplate viewRecipe() {
    ShepardTemplate t = new ShepardTemplate(
      "3D trace — gimbal path",
      "VIEW_RECIPE",
      "{\"viewRecipeShape\":\"urn:shepard:shape:trace3d\",\"renderer\":\"tresjs\",\"channelBindings\":[]}"
    );
    t.setAppId(VIEW_TMPL);
    return t;
  }

  ShepardTemplate formRecipe() {
    ShepardTemplate t = new ShepardTemplate(
      "Record a Pyrolysis step",
      "STRUCTURED_RECIPE",
      "{\"structuredData\":{},\"shapeGraph\":\"@prefix sh: <http://www.w3.org/ns/shacl#> .\"}"
    );
    t.setAppId(FORM_TMPL);
    return t;
  }

  ShepardTemplate formRecipeWithoutShape() {
    ShepardTemplate t = new ShepardTemplate("Legacy attribute bag", "DATAOBJECT_RECIPE", "{\"dataobjects\":[{}]}");
    t.setAppId("tmpl-legacy-no-shape");
    return t;
  }

  ShapesApplicableResponseIO body(Response r) {
    return (ShapesApplicableResponseIO) r.getEntity();
  }

  // ─── contract: 400 / 404 ────────────────────────────────────────────────

  @Test
  void returns400WhenFocusAppIdMissing() {
    Response r = resource.listApplicable(null);
    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());

    Response blank = resource.listApplicable("  ");
    assertEquals(400, blank.getStatus());
  }

  @Test
  void returns404ProblemJsonWhenFocusUnknown() {
    when(focusRepository.findByAppId(FOCUS)).thenReturn(Optional.empty());
    Response r = resource.listApplicable(FOCUS);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains(FOCUS));
  }

  // ─── determination: view-only / form-only / both / empty ───────────────

  @Test
  void viewOnly_attachedViewRecipeYieldsOneViewItem() {
    when(focusRepository.findByAppId(FOCUS)).thenReturn(
      Optional.of(new FocusEntity("DataObject", VIEW_TMPL, null))
    );
    when(templateDAO.findByAppId(VIEW_TMPL)).thenReturn(Optional.of(viewRecipe()));
    when(templateDAO.list(null, false)).thenReturn(List.of(viewRecipe()));

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    ShapesApplicableResponseIO out = body(r);
    assertEquals(FOCUS, out.focusAppId());
    // The VIEW_RECIPE in the global list is NOT a form candidate — exactly one item.
    assertEquals(1, out.items().size());
    var item = out.items().get(0);
    assertEquals("VIEW", item.mode());
    assertEquals(VIEW_TMPL, item.templateAppId());
    assertEquals("3D trace — gimbal path", item.title());
    assertEquals("urn:shepard:shape:trace3d", item.shapeIri());
    assertEquals("/v2/shapes/render", item.renderHref());
    assertNull(item.formHref());
  }

  @Test
  void formOnly_dataKindTemplateWithShapeGraphYieldsOneFormItem() {
    when(focusRepository.findByAppId(FOCUS)).thenReturn(Optional.of(new FocusEntity("DataObject", null, null)));
    // One real form candidate + one data-kind template without a shapeGraph (skipped).
    when(templateDAO.list(null, false)).thenReturn(List.of(formRecipe(), formRecipeWithoutShape()));

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    ShapesApplicableResponseIO out = body(r);
    assertEquals(1, out.items().size());
    var item = out.items().get(0);
    assertEquals("FORM", item.mode());
    assertEquals(FORM_TMPL, item.templateAppId());
    assertEquals("Record a Pyrolysis step", item.title());
    assertEquals("/v2/templates/" + FORM_TMPL + "/form", item.formHref());
    assertNull(item.renderHref());
  }

  @Test
  void bothModes_attachedViewPlusAllowListedForm() {
    when(focusRepository.findByAppId(FOCUS)).thenReturn(
      Optional.of(new FocusEntity("DataObject", VIEW_TMPL, COLLECTION))
    );
    when(templateDAO.findByAppId(VIEW_TMPL)).thenReturn(Optional.of(viewRecipe()));
    // Collection allow-list is non-empty → it scopes the FORM candidates.
    when(templateDAO.listAllowedForCollection(COLLECTION)).thenReturn(List.of(formRecipe()));

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    ShapesApplicableResponseIO out = body(r);
    assertEquals(2, out.items().size());
    assertEquals("VIEW", out.items().get(0).mode());
    assertEquals("FORM", out.items().get(1).mode());
  }

  @Test
  void emptyItemsIsValid_neverAnError() {
    when(focusRepository.findByAppId(FOCUS)).thenReturn(Optional.of(new FocusEntity("FileReference", null, null)));
    when(templateDAO.list(null, false)).thenReturn(List.of());

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    assertTrue(body(r).items().isEmpty());
  }

  // ─── gates: retired / wrong kind ────────────────────────────────────────

  @Test
  void retiredAttachedTemplateYieldsNoViewItem() {
    ShepardTemplate retired = viewRecipe();
    retired.setRetired(true);
    when(focusRepository.findByAppId(FOCUS)).thenReturn(
      Optional.of(new FocusEntity("DataObject", VIEW_TMPL, null))
    );
    when(templateDAO.findByAppId(VIEW_TMPL)).thenReturn(Optional.of(retired));
    when(templateDAO.list(null, false)).thenReturn(List.of());

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    assertTrue(body(r).items().isEmpty());
  }

  @Test
  void attachedDataKindTemplateIsNotAViewButIsAForm() {
    // Attached template is a STRUCTURED_RECIPE → no VIEW entry (render would 422),
    // but the same template is a FORM candidate via the global list.
    when(focusRepository.findByAppId(FOCUS)).thenReturn(
      Optional.of(new FocusEntity("DataObject", FORM_TMPL, null))
    );
    when(templateDAO.findByAppId(FORM_TMPL)).thenReturn(Optional.of(formRecipe()));
    when(templateDAO.list(null, false)).thenReturn(List.of(formRecipe()));

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    ShapesApplicableResponseIO out = body(r);
    assertEquals(1, out.items().size());
    assertEquals("FORM", out.items().get(0).mode());
  }

  // ─── fail-soft ──────────────────────────────────────────────────────────

  @Test
  void determinationFailureDegradesToEmptyLeg_never500() {
    when(focusRepository.findByAppId(FOCUS)).thenReturn(
      Optional.of(new FocusEntity("DataObject", VIEW_TMPL, null))
    );
    when(templateDAO.findByAppId(any())).thenThrow(new IllegalStateException("neo4j down"));
    when(templateDAO.list(any(), anyBoolean())).thenThrow(new IllegalStateException("neo4j down"));

    Response r = resource.listApplicable(FOCUS);
    assertEquals(200, r.getStatus());
    assertTrue(body(r).items().isEmpty());
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  @Test
  void hasShapeGraph_handlesAbsentAndMalformedBodies() {
    assertFalse(ShapesApplicableRest.hasShapeGraph(null));
    assertFalse(ShapesApplicableRest.hasShapeGraph(""));
    assertFalse(ShapesApplicableRest.hasShapeGraph("not json"));
    assertFalse(ShapesApplicableRest.hasShapeGraph("{\"shapeGraph\":\"\"}"));
    assertTrue(ShapesApplicableRest.hasShapeGraph("{\"shapeGraph\":\"@prefix sh: <x> .\"}"));
  }

  // ─── APISIMP-SHAPES-FOCUS-APPID-REQUIRED regression ──────────────────────

  @Test
  void listApplicable_focusAppIdParam_isMarkedRequired() throws NoSuchMethodException {
    java.lang.reflect.Method method = ShapesApplicableRest.class.getMethod(
        "listApplicable", String.class);
    java.lang.reflect.Parameter param = method.getParameters()[0];
    var qp = param.getAnnotation(jakarta.ws.rs.QueryParam.class);
    assertNotNull(qp, "first param must carry @QueryParam");
    assertEquals("focusAppId", qp.value());
    var oapiParam = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(oapiParam, "focusAppId must carry @Parameter annotation");
    assertTrue(oapiParam.required(), "@Parameter.required must be true for focusAppId");
  }
}
