package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.shapes.builder.FormHintSpec;
import de.dlr.shepard.v2.shapes.builder.GroupSpec;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.template.services.CellMappingExcelExporter;
import de.dlr.shepard.v2.template.services.FormDescriptorCompiler;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * {@code GET /v2/templates/{templateAppId}/export?dataObjectAppId=…} —
 * status-code contract + the doc 125 §6 round-trip proof: the focused
 * DataObject's attribute values land in the shape's mapped cells and are
 * read back out of the generated workbook with POI (BTKVS-C1-EXCEL-EXPORT).
 */
class TemplateExcelExportRestTest {

  static final String TMPL_APP_ID = "tmpl-docket-general";
  static final String DO_APP_ID = "019e7243-f995-7914-be80-000000000001";
  static final String ATTR = "urn:shepard:attribute:";
  static final String DASH = "http://datashapes.org/dash#";
  static final String LAUFZETTEL = "Laufzettel C-C bzw C-C-SiC";

  @Mock
  ShepardTemplateDAO templateDAO;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  TemplateExcelExportRest resource;

  final ShaclShapeBuilder builder = new ShaclShapeBuilder();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TemplateExcelExportRest();
    resource.templateDAO = templateDAO;
    resource.inheritanceResolver = new TemplateInheritanceResolver(templateDAO);
    resource.compiler = new FormDescriptorCompiler();
    resource.exporter = new CellMappingExcelExporter();
    resource.dataObjectDAO = dataObjectDAO;
    resource.permissionsService = permissionsService;
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
    when(permissionsService.isAccessAllowedForDataObjectAppId(any(), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
  }

  // ─── fixtures ────────────────────────────────────────────────────────

  String docketShapeTurtle(boolean withCellMappings) {
    var spec = new ShapeSpec(
      "urn:btkvs:shape:docket-general",
      null,
      false,
      List.of(
        new PropertyShapeSpec(
          ATTR + "docket_id",
          "http://www.w3.org/2001/XMLSchema#string",
          1,
          1,
          null,
          null,
          "^[A-Z][0-9]{3}$",
          new FormHintSpec(
            "Docket ID", null, 1.0, "urn:btkvs:group:identity", null,
            DASH + "TextFieldEditor", null, "I123", null,
            withCellMappings ? new FormHintSpec.CellMappingSpec(LAUFZETTEL, "K1") : null
          )
        ),
        new PropertyShapeSpec(
          ATTR + "project",
          "http://www.w3.org/2001/XMLSchema#string",
          0,
          1,
          null,
          null,
          null,
          new FormHintSpec(
            "Project", null, 2.0, "urn:btkvs:group:identity", null,
            DASH + "TextFieldEditor", null, null, null,
            withCellMappings ? new FormHintSpec.CellMappingSpec(null, "C4") : null
          )
        )
      ),
      List.of(new GroupSpec("urn:btkvs:group:identity", "Identity", 1.0)),
      "urn:shepard:instance:candidate"
    );
    return builder.toTurtle(spec);
  }

  ShepardTemplate templateWithShape(boolean withCellMappings) {
    String body;
    try {
      var node = new ObjectMapper().createObjectNode();
      node.set("structuredData", new ObjectMapper().createObjectNode());
      node.put("shapeGraph", docketShapeTurtle(withCellMappings));
      body = node.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    ShepardTemplate t = new ShepardTemplate("Docket — general section", "STRUCTURED_RECIPE", body);
    t.setAppId(TMPL_APP_ID);
    t.setVersion(1);
    return t;
  }

  DataObject docketDataObject() {
    DataObject d = new DataObject();
    d.setAppId(DO_APP_ID);
    Map<String, String> attributes = new HashMap<>();
    attributes.put("docket_id", "D123");
    attributes.put("project", "PLUTO");
    d.setAttributes(attributes);
    return d;
  }

  static XSSFWorkbook readBack(Response r) throws IOException {
    return new XSSFWorkbook(new ByteArrayInputStream((byte[]) r.getEntity()));
  }

  // ─── happy path: the round-trip proof ───────────────────────────────

  @Test
  void returns200AndMappedCellValuesReadBackFromTheWorkbook() throws IOException {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape(true)));
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(docketDataObject());

    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    assertEquals(CellMappingExcelExporter.XLSX_MEDIA_TYPE, r.getMediaType().toString());
    String disposition = r.getHeaderString("Content-Disposition");
    assertNotNull(disposition);
    assertTrue(disposition.contains("docket-general-section-" + DO_APP_ID + ".xlsx"), disposition);

    try (XSSFWorkbook wb = readBack(r)) {
      XSSFSheet sheet = wb.getSheet(LAUFZETTEL);
      assertNotNull(sheet, "the urn:btkvs:sheet name materialises as the worksheet");
      assertEquals("D123", sheet.getRow(0).getCell(10).getStringCellValue(),
        "Docket-ID attribute value read back from mapped cell K1");
      assertEquals("PLUTO", sheet.getRow(3).getCell(2).getStringCellValue(),
        "Project attribute value read back from mapped cell C4 (null sheet → first sheet)");
    }
  }

  // ─── status-code contract ────────────────────────────────────────────

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void returns404WhenTemplateMissing() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains(TMPL_APP_ID));
  }

  @Test
  void returns404WhenDataObjectMissing() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape(true)));
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains(DO_APP_ID));
  }

  @Test
  void returns404WhenDataObjectAppIdBlank() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape(true)));
    Response r = resource.exportExcel(TMPL_APP_ID, " ", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void returns404WhenDataObjectDeleted() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape(true)));
    DataObject deleted = docketDataObject();
    deleted.setDeleted(true);
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(deleted);
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void returns409WhenTemplateRetired() {
    ShepardTemplate t = templateWithShape(true);
    t.setRetired(true);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(t));
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(409, r.getStatus());
  }

  @Test
  void returns409WhenTemplateHasNoCellMappings() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape(false)));
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(409, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains("cell-mapping"));
  }

  @Test
  void returns403WhenCallerLacksRead() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape(true)));
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(docketDataObject());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq("alice")))
      .thenReturn(false);
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void returns422WhenTemplateKindIsNotADataKind() {
    ShepardTemplate t = new ShepardTemplate("Trace3D", "VIEW_RECIPE", "{\"view\":{}}");
    t.setAppId(TMPL_APP_ID);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(t));
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(422, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains("VIEW_RECIPE"));
  }

  @Test
  void returns422WhenNoShapeGraph() {
    ShepardTemplate t = new ShepardTemplate("Legacy", "DATAOBJECT_RECIPE", "{\"dataobjects\":[{}]}");
    t.setAppId(TMPL_APP_ID);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(t));
    Response r = resource.exportExcel(TMPL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(422, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains("shapeGraph"));
  }

  // ─── filename slug ───────────────────────────────────────────────────

  @Test
  void filenameForSlugsTheTemplateName() {
    ShepardTemplate t = new ShepardTemplate("Döcket — general §section", "STRUCTURED_RECIPE", "{}");
    DataObject d = new DataObject();
    d.setAppId(DO_APP_ID);
    String filename = TemplateExcelExportRest.filenameFor(t, d);
    assertEquals("d-cket-general-section-" + DO_APP_ID + ".xlsx", filename);

    ShepardTemplate unnamed = new ShepardTemplate(null, "STRUCTURED_RECIPE", "{}");
    assertEquals("export-" + DO_APP_ID + ".xlsx", TemplateExcelExportRest.filenameFor(unnamed, d));
  }
}
