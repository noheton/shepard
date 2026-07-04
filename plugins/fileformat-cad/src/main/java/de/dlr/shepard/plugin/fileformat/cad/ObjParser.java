package de.dlr.shepard.plugin.fileformat.cad;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight OBJ mesh statistics extractor.
 *
 * <p>Counts vertex lines ({@code v }), face lines ({@code f }),
 * and extracts the first {@code mtllib} material library reference.
 * Full geometry is not loaded — phase 1 produces metadata only.
 */
public class ObjParser {

  private static final Pattern MTL_PAT = Pattern.compile("^mtllib\\s+(\\S+)", Pattern.MULTILINE);

  public boolean accepts(byte[] bytes, String filename) {
    if (filename == null) return false;
    String lower = filename.toLowerCase();
    return lower.endsWith(".obj");
  }

  public Map<String, String> parse(byte[] bytes) {
    Map<String, String> result = new LinkedHashMap<>();
    if (bytes == null || bytes.length == 0) return result;

    String text = new String(bytes, 0, Math.min(bytes.length, 2_097_152), StandardCharsets.ISO_8859_1);

    long vertices = countLines(text, "v ");
    long faces = countLines(text, "f ");

    if (vertices > 0) result.put(CadAnnotations.VERTEX_COUNT, String.valueOf(vertices));
    if (faces > 0) result.put(CadAnnotations.FACE_COUNT, String.valueOf(faces));

    Matcher m = MTL_PAT.matcher(text);
    if (m.find()) result.put(CadAnnotations.MTL_LIBRARY, m.group(1).trim());

    result.put(CadAnnotations.FORMAT, "obj");
    return result;
  }

  private long countLines(String text, String prefix) {
    long count = 0;
    int idx = 0;
    while ((idx = text.indexOf('\n', idx)) >= 0) {
      idx++;
      if (idx + prefix.length() <= text.length()
          && text.regionMatches(idx, prefix, 0, prefix.length())) {
        count++;
      }
    }
    return count;
  }
}
