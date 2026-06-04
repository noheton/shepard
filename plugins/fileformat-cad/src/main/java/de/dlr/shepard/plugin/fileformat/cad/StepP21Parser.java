package de.dlr.shepard.plugin.fileformat.cad;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java parser for STEP ISO 10303-21 physical files (.step, .stp).
 *
 * <p>Scope (phase 1 — metadata only):
 * <ul>
 *   <li>Validates the {@code ISO-10303-21;} magic line (with optional {@code ;} on next line).</li>
 *   <li>Parses all HEADER section entities: {@code FILE_DESCRIPTION}, {@code FILE_NAME},
 *       {@code FILE_SCHEMA}, {@code FILE_POPULATION}.</li>
 *   <li>Scans the DATA section (up to 512 KB) for first occurrences of
 *       {@code PRODUCT}, {@code MATERIAL}, and a selection of AP242 composite
 *       entities ({@code COMPOSITE_CURVE_SEGMENT}, {@code REINFORCEMENT_ORIENTATION_SELECT})
 *       to extract ply count and fibre-angle hints.</li>
 * </ul>
 *
 * <p>Phase 2 (CAD-STEP-GEOMETRY-1) will add full geometry traversal.
 */
public class StepP21Parser {

  private static final byte[] MAGIC = "ISO-10303-21".getBytes(StandardCharsets.US_ASCII);

  private static final Pattern ENTITY_PAT =
      Pattern.compile("([A-Z_][A-Z0-9_]*)\\s*\\(([^;]*)\\)\\s*;",
          Pattern.DOTALL);

  private static final Pattern SCHEMA_PAT =
      Pattern.compile("FILE_SCHEMA\\s*\\(\\s*\\(\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);

  private static final Pattern FILE_NAME_PAT =
      Pattern.compile("FILE_NAME\\s*\\(([^;]+)\\)\\s*;", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private static final Pattern FILE_DESC_PAT =
      Pattern.compile("FILE_DESCRIPTION\\s*\\(([^;]+)\\)\\s*;", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private static final Pattern PRODUCT_PAT =
      Pattern.compile("#\\d+\\s*=\\s*PRODUCT\\s*\\(\\s*'([^']*)'\\s*,\\s*'([^']*)'",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern MATERIAL_PAT =
      Pattern.compile("#\\d+\\s*=\\s*MATERIAL\\s*\\(\\s*'([^']*)'",
          Pattern.CASE_INSENSITIVE);

  /** Ply-count heuristic: count COMPOSITE_* entity occurrences in DATA section. */
  private static final Pattern COMPOSITE_PAT =
      Pattern.compile("=\\s*COMPOSITE_[A-Z_]+\\s*\\(", Pattern.CASE_INSENSITIVE);

  /** Fibre angle heuristic: extract numeric values near REINFORCEMENT keyword. */
  private static final Pattern FIBRE_ANGLE_PAT =
      Pattern.compile("REINFORCEMENT[^;]{0,200}?([-]?\\d+(?:\\.\\d+)?)",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  // ── public API ───────────────────────────────────────────────────────────

  /** Returns true if the byte array starts with the ISO-10303-21 magic string. */
  public boolean accepts(byte[] bytes) {
    if (bytes == null || bytes.length < MAGIC.length) return false;
    for (int i = 0; i < MAGIC.length; i++) {
      if (bytes[i] != MAGIC[i]) return false;
    }
    return true;
  }

  /**
   * Parse the STEP file bytes and return extracted metadata as a flat key→value map.
   * Keys correspond to {@link CadAnnotations} constants (without the IRI prefix, for
   * internal convenience — callers should use the constants directly).
   */
  public Map<String, String> parse(byte[] bytes) {
    Map<String, String> result = new LinkedHashMap<>();
    if (!accepts(bytes)) return result;

    String text = new String(bytes, StandardCharsets.ISO_8859_1);

    int headerEnd = text.indexOf("ENDSEC;");
    String header = headerEnd > 0 ? text.substring(0, headerEnd) : text.substring(0, Math.min(text.length(), 4096));

    parseSchema(header).ifPresent(s -> result.put(CadAnnotations.STEP_SCHEMA, s));
    parseFileName(header, result);
    parseFileDescription(header).ifPresent(d -> result.put(CadAnnotations.DESCRIPTION, d));

    int dataStart = text.indexOf("DATA;");
    if (dataStart >= 0) {
      int dataEnd = text.indexOf("ENDSEC;", dataStart);
      int scanLimit = dataEnd > 0 ? dataEnd : Math.min(text.length(), dataStart + 524_288);
      String data = text.substring(dataStart, scanLimit);
      parseDataSection(data, result);
    }

    result.put(CadAnnotations.FORMAT, "step");
    return result;
  }

  // ── private helpers ──────────────────────────────────────────────────────

  private Optional<String> parseSchema(String header) {
    Matcher m = SCHEMA_PAT.matcher(header);
    return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
  }

  private void parseFileName(String header, Map<String, String> out) {
    Matcher m = FILE_NAME_PAT.matcher(header);
    if (!m.find()) return;
    String args = m.group(1);
    // FILE_NAME arguments: (name, time_stamp, author, organization, preprocessor, originating_system, authorization)
    List<String> tokens = splitArgs(args);
    if (tokens.size() >= 1) {
      String name = stripQuotes(tokens.get(0));
      if (!name.isEmpty()) out.put(CadAnnotations.DESCRIPTION, name);
    }
    if (tokens.size() >= 2) {
      String ts = stripQuotes(tokens.get(1));
      if (!ts.isEmpty()) out.put(CadAnnotations.CREATED_AT, ts);
    }
    if (tokens.size() >= 4) {
      String org = stripListQuotes(tokens.get(3));
      if (!org.isEmpty()) out.put(CadAnnotations.ORGANISATION, org);
    }
    if (tokens.size() >= 5) {
      String app = stripQuotes(tokens.get(4));
      if (!app.isEmpty()) out.put(CadAnnotations.APPLICATION, app);
    }
  }

  private String stripListQuotes(String s) {
    s = s.trim();
    if (s.startsWith("(")) s = s.substring(1);
    if (s.endsWith(")")) s = s.substring(0, s.length() - 1);
    return stripQuotes(s.trim());
  }

  private Optional<String> parseFileDescription(String header) {
    Matcher m = FILE_DESC_PAT.matcher(header);
    if (!m.find()) return Optional.empty();
    List<String> tokens = splitArgs(m.group(1));
    if (tokens.isEmpty()) return Optional.empty();
    String raw = tokens.get(0).trim();
    if (raw.startsWith("(")) {
      raw = raw.substring(1, raw.length() - (raw.endsWith(")") ? 1 : 0)).trim();
    }
    String desc = stripQuotes(raw.replaceFirst("^\\(", "").replaceFirst("\\)$", "").trim());
    return desc.isEmpty() ? Optional.empty() : Optional.of(desc);
  }

  private void parseDataSection(String data, Map<String, String> out) {
    Matcher productMatcher = PRODUCT_PAT.matcher(data);
    if (productMatcher.find()) {
      String name = productMatcher.group(1).trim();
      if (!name.isEmpty()) out.put(CadAnnotations.PRODUCT_NAME, name);
    }

    Matcher materialMatcher = MATERIAL_PAT.matcher(data);
    if (materialMatcher.find()) {
      String mat = materialMatcher.group(1).trim();
      if (!mat.isEmpty()) out.put(CadAnnotations.MATERIAL, mat);
    }

    int compositeCount = 0;
    Matcher cm = COMPOSITE_PAT.matcher(data);
    while (cm.find()) compositeCount++;
    if (compositeCount > 0) out.put(CadAnnotations.PLY_COUNT, String.valueOf(compositeCount));

    List<String> angles = new ArrayList<>();
    Matcher am = FIBRE_ANGLE_PAT.matcher(data);
    while (am.find() && angles.size() < 10) {
      String angle = am.group(1);
      if (!angles.contains(angle)) angles.add(angle);
    }
    if (!angles.isEmpty()) out.put(CadAnnotations.FIBRE_ANGLES, String.join(",", angles));
  }

  /** Top-level arg splitter for STEP entity argument lists. */
  private List<String> splitArgs(String args) {
    List<String> result = new ArrayList<>();
    int parenDepth = 0;
    boolean inQuote = false;
    int start = 0;
    for (int i = 0; i < args.length(); i++) {
      char c = args.charAt(i);
      if (inQuote) {
        if (c == '\'') inQuote = false;
      } else if (c == '\'') {
        inQuote = true;
      } else if (c == '(') {
        parenDepth++;
      } else if (c == ')') {
        parenDepth--;
      } else if (c == ',' && parenDepth == 0) {
        result.add(args.substring(start, i).trim());
        start = i + 1;
      }
    }
    if (start < args.length()) result.add(args.substring(start).trim());
    return result;
  }

  private String stripQuotes(String s) {
    s = s.trim();
    if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }
}
