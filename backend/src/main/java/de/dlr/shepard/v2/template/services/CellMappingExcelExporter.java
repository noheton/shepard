package de.dlr.shepard.v2.template.services;

import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.CellMappingIO;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.FieldIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * BTKVS-C1-EXCEL-EXPORT — shape-driven Excel export per doc 125 §6/D5.
 *
 * <p>The same {@code urn:btkvs:cell-mapping} / {@code urn:btkvs:sheet}
 * annotations that ride a data-kind template's {@code shapeGraph} (authored
 * via the {@code ShaclShapeBuilder} DSL's {@code FormHintSpec.cellMapping})
 * drive this writer: for every compiled form field that carries a cell
 * mapping AND whose {@code attributeKey} resolves to a value in the focused
 * DataObject's attributes, the value is written to the mapped A1-style cell
 * on the mapped worksheet. This is the export half of the Excel ↔ JSON
 * round-trip that retires the BT-KVS group's xlwings
 * {@code docket_to_excel.py} (requires-a-running-Excel) tooling; the import
 * half is BTKVS-C2.
 *
 * <p><b>Contract.</b>
 * <ul>
 *   <li>Fields without a cell mapping are skipped silently (doc 125 §6 —
 *       not every form field has a spreadsheet home).</li>
 *   <li>Fields whose attribute key has no value on the DataObject leave
 *       their cell empty (an absent answer is an empty cell, not "null").</li>
 *   <li>A {@code null} sheet name means "the workbook's first sheet" —
 *       created as {@value #DEFAULT_SHEET} when no mapped field named a
 *       sheet before it. For single-sheet dockets (the Laufzettel case)
 *       this lands unqualified mappings on the named sheet.</li>
 *   <li>Malformed cell references are a shape-authoring bug: logged at
 *       WARN and skipped, never a 500 — the rest of the export stays
 *       usable (fail-soft).</li>
 *   <li>Values are written as string cells verbatim — the attributes map
 *       is {@code Map<String, String>} and round-trip fidelity beats
 *       locale-sensitive numeric coercion (the import leg re-validates
 *       against the same shape anyway).</li>
 * </ul>
 *
 * <p><b>Written deviation from §6:</b> this slice generates a fresh
 * workbook rather than overlaying the operator's {@code Empty.xlsx}
 * template (that asset is an operator upload, read-only, never in the
 * repo). Styling/layout overlay joins the BTKVS-C2 import slice, which
 * needs the template workbook on the instance anyway.
 *
 * <p>Stateless and thread-safe: one hot CDI instance; fresh workbooks per
 * call that never escape it.
 */
@ApplicationScoped
public class CellMappingExcelExporter {

  /** The xlsx media type the export endpoint produces. */
  public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  /** Sheet name used when no mapped field names a sheet. */
  public static final String DEFAULT_SHEET = "Sheet1";

  /** True when at least one field carries a usable cell mapping. */
  public boolean hasCellMappings(List<FieldIO> fields) {
    if (fields == null) return false;
    return fields.stream().anyMatch(f -> mappedCell(f) != null);
  }

  /**
   * Write the mapped attribute values into a fresh xlsx workbook.
   *
   * @param fields     the compiled form fields (descriptor order)
   * @param attributes the focused DataObject's attributes; may be null
   * @return the serialized workbook bytes
   */
  public byte[] export(List<FieldIO> fields, Map<String, String> attributes) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (fields != null) {
        for (FieldIO field : fields) {
          writeField(workbook, field, attributes);
        }
      }
      if (workbook.getNumberOfSheets() == 0) {
        workbook.createSheet(DEFAULT_SHEET);
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("could not serialize export workbook", e);
    }
  }

  // ─── helpers ───────────────────────────────────────────────────────

  private void writeField(XSSFWorkbook workbook, FieldIO field, Map<String, String> attributes) {
    String cellRef = mappedCell(field);
    if (cellRef == null) {
      return; // no cell mapping — skipped silently per doc 125 §6
    }
    String key = field.attributeKey();
    if (key == null) {
      return; // non-attribute path — nothing to read from the DataObject
    }
    String value = attributes == null ? null : attributes.get(key);
    if (value == null) {
      return; // absent answer = empty cell
    }
    CellReference ref;
    try {
      ref = new CellReference(cellRef);
      if (ref.getRow() < 0 || ref.getCol() < 0) {
        throw new IllegalArgumentException("not an A1-style cell reference: " + cellRef);
      }
    } catch (IllegalArgumentException ex) {
      Log.warnf(
        "CellMappingExcelExporter: skipping field %s — malformed urn:btkvs:cell-mapping %s (%s)",
        field.path(),
        cellRef,
        ex.getMessage()
      );
      return;
    }
    Sheet sheet = sheetFor(workbook, field.cellMapping().sheet());
    Row row = sheet.getRow(ref.getRow());
    if (row == null) {
      row = sheet.createRow(ref.getRow());
    }
    Cell cell = row.getCell(ref.getCol());
    if (cell == null) {
      cell = row.createCell(ref.getCol());
    }
    cell.setCellValue(value);
  }

  /**
   * Null sheet name → the workbook's first sheet (created as
   * {@value #DEFAULT_SHEET} when none exists). Named sheets pass through
   * {@link WorkbookUtil#createSafeSheetName(String)} so a shape-authored
   * name with Excel-illegal characters degrades instead of throwing.
   */
  private static Sheet sheetFor(XSSFWorkbook workbook, String sheetName) {
    if (sheetName == null || sheetName.isBlank()) {
      return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : workbook.createSheet(DEFAULT_SHEET);
    }
    String safe = WorkbookUtil.createSafeSheetName(sheetName);
    Sheet existing = workbook.getSheet(safe);
    return existing != null ? existing : workbook.createSheet(safe);
  }

  private static String mappedCell(FieldIO field) {
    CellMappingIO mapping = field == null ? null : field.cellMapping();
    if (mapping == null || mapping.cell() == null || mapping.cell().isBlank()) {
      return null;
    }
    return mapping.cell();
  }
}
