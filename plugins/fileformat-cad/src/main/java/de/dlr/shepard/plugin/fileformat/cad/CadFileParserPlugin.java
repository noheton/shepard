package de.dlr.shepard.plugin.fileformat.cad;

import java.util.Map;

/**
 * Main {@link FileParserPlugin} implementation for CAD formats.
 *
 * <p>Dispatches to sub-parsers based on file magic bytes and filename extension:
 * <ul>
 *   <li>STEP ISO 10303-21 ({@code .step}, {@code .stp}, magic {@code ISO-10303-21}) → {@link StepP21Parser}</li>
 *   <li>Dassault 3DXML ({@code .3dxml}, ZIP magic PK\x03\x04) → {@link ThreeDxmlParser}</li>
 *   <li>JT ISO 14306 ({@code .jt}, magic {@code #!JT}) → magic detection only</li>
 *   <li>OBJ mesh ({@code .obj}) → {@link ObjParser}</li>
 * </ul>
 *
 * <p>All extracted keys are written via the {@link FileParserPlugin.AnnotationWriter}
 * anchored to whichever entity appId the {@link FileParserPlugin.ParseContext} provides
 * (fileReference first, parent DataObject as fallback).
 */
public class CadFileParserPlugin implements FileParserPlugin {

  private static final byte[] JT_MAGIC = {'#', '!', 'J', 'T'};

  private final StepP21Parser stepParser = new StepP21Parser();
  private final ThreeDxmlParser threeDxmlParser = new ThreeDxmlParser();
  private final ObjParser objParser = new ObjParser();

  @Override
  public boolean accepts(String mimeType, String filename) {
    if (filename == null) return false;
    String lower = filename.toLowerCase();
    return lower.endsWith(".step")
        || lower.endsWith(".stp")
        || lower.endsWith(".3dxml")
        || lower.endsWith(".jt")
        || lower.endsWith(".obj");
  }

  @Override
  public int parse(ParseContext ctx) {
    byte[] bytes = ctx.bytes();
    String filename = ctx.filename();
    String anchor = ctx.fileReferenceAppId()
        .or(ctx::parentDataObjectAppId)
        .orElse(null);
    if (anchor == null) return 0;

    Map<String, String> annotations;

    if (stepParser.accepts(bytes)) {
      annotations = stepParser.parse(bytes);
    } else if (isJt(bytes)) {
      annotations = Map.of(CadAnnotations.FORMAT, "jt");
    } else if (objParser.accepts(bytes, filename)) {
      annotations = objParser.parse(bytes);
    } else if (threeDxmlParser.accepts(bytes)) {
      annotations = threeDxmlParser.parse(bytes);
    } else {
      return 0;
    }

    FileParserPlugin.AnnotationWriter writer = ctx.annotations();
    annotations.forEach((k, v) -> writer.write(anchor, k, v));
    return annotations.size();
  }

  private boolean isJt(byte[] bytes) {
    if (bytes == null || bytes.length < JT_MAGIC.length) return false;
    for (int i = 0; i < JT_MAGIC.length; i++) {
      if (bytes[i] != JT_MAGIC[i]) return false;
    }
    return true;
  }
}
