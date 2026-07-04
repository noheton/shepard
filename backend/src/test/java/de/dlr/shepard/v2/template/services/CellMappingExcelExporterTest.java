package de.dlr.shepard.v2.template.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.CellMappingIO;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.FieldIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * BTKVS-C1-EXCEL-EXPORT — the cell-mapping workbook writer. Every assertion
 * reads the generated xlsx back through POI (the round-trip proof of doc 125
 * §6: mapped value in → same value out of the mapped cell).
 */
class CellMappingExcelExporterTest {

  static final String ATTR = "urn:shepard:attribute:";
  static final String LAUFZETTEL = "Laufzettel C-C bzw C-C-SiC";

  final CellMappingExcelExporter exporter = new CellMappingExcelExporter();

  static FieldIO field(String key, CellMappingIO mapping) {
    return new FieldIO(
      ATTR + key, key, key, null, null, null,
      "http://www.w3.org/2001/XMLSchema#string",
      null, null, "http://datashapes.org/dash#TextFieldEditor",
      null, null, null, null, null, mapping
    );
  }

  static XSSFWorkbook readBack(byte[] bytes) throws IOException {
    return new XSSFWorkbook(new ByteArrayInputStream(bytes));
  }

  static String cellValue(XSSFSheet sheet, int rowIdx, int colIdx) {
    Row row = sheet.getRow(rowIdx);
    if (row == null) return null;
    Cell cell = row.getCell(colIdx);
    return cell == null ? null : cell.getStringCellValue();
  }

  @Test
  void mappedAttributeValuesLandInTheMappedCells() throws IOException {
    List<FieldIO> fields = List.of(
      // the docket :general seed mappings — Docket-ID → K1 @ Laufzettel, Project → C4 (no sheet)
      field("docket_id", new CellMappingIO(LAUFZETTEL, "K1")),
      field("project", new CellMappingIO(null, "C4")),
      // a form field without a spreadsheet home — skipped silently (doc 125 §6)
      field("notes", null)
    );
    Map<String, String> attributes = Map.of("docket_id", "D123", "project", "PLUTO", "notes", "irrelevant");

    byte[] bytes = exporter.export(fields, attributes);

    try (XSSFWorkbook wb = readBack(bytes)) {
      assertEquals(1, wb.getNumberOfSheets(), "null sheet name lands on the first (Laufzettel) sheet");
      XSSFSheet sheet = wb.getSheet(LAUFZETTEL);
      assertNotNull(sheet, "sheet is created under the mapped name");
      assertEquals("D123", cellValue(sheet, 0, 10), "Docket-ID is written to K1 (row 0, col 10)");
      assertEquals("PLUTO", cellValue(sheet, 3, 2), "Project is written to C4 (row 3, col 2)");
    }
  }

  @Test
  void absentAttributeValueLeavesTheCellEmpty() throws IOException {
    List<FieldIO> fields = List.of(
      field("docket_id", new CellMappingIO(LAUFZETTEL, "K1")),
      field("project", new CellMappingIO(LAUFZETTEL, "C4"))
    );

    // project has a value, docket_id does not — its mapped cell stays empty
    byte[] bytes = exporter.export(fields, Map.of("project", "PLUTO"));

    try (XSSFWorkbook wb = readBack(bytes)) {
      XSSFSheet sheet = wb.getSheet(LAUFZETTEL);
      assertNotNull(sheet);
      assertNull(cellValue(sheet, 0, 10), "no instance value → K1 stays empty");
      assertEquals("PLUTO", cellValue(sheet, 3, 2), "the valued sibling still exports");
    }
  }

  @Test
  void malformedCellReferenceIsSkippedNotThrown() throws IOException {
    List<FieldIO> fields = List.of(
      field("docket_id", new CellMappingIO(LAUFZETTEL, "!!not-a-ref!!")),
      field("project", new CellMappingIO(LAUFZETTEL, "C4"))
    );

    byte[] bytes = exporter.export(fields, Map.of("docket_id", "D123", "project", "PLUTO"));

    try (XSSFWorkbook wb = readBack(bytes)) {
      XSSFSheet sheet = wb.getSheet(LAUFZETTEL);
      assertNotNull(sheet);
      assertEquals("PLUTO", cellValue(sheet, 3, 2), "the well-formed mapping still exports");
    }
  }

  @Test
  void nullInputsProduceAValidEmptyWorkbook() throws IOException {
    byte[] bytes = exporter.export(null, null);
    try (XSSFWorkbook wb = readBack(bytes)) {
      assertEquals(1, wb.getNumberOfSheets());
      assertEquals(CellMappingExcelExporter.DEFAULT_SHEET, wb.getSheetAt(0).getSheetName());
    }
  }

  @Test
  void hasCellMappingsDetectsUsableMappings() {
    assertFalse(exporter.hasCellMappings(null));
    assertFalse(exporter.hasCellMappings(List.of(field("notes", null))));
    assertFalse(exporter.hasCellMappings(List.of(field("notes", new CellMappingIO(LAUFZETTEL, " ")))));
    assertTrue(exporter.hasCellMappings(List.of(field("docket_id", new CellMappingIO(null, "K1")))));
  }
}
