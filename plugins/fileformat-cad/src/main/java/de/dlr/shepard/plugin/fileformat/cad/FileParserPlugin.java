package de.dlr.shepard.plugin.fileformat.cad;

import java.util.Optional;

/**
 * Local SPI shim for the {@code FileParserPlugin} interface sketched in
 * aidocs/integrations/110 §3.
 *
 * <p>The canonical interface lives in the backend module
 * ({@code de.dlr.shepard.spi.fileparser.FileParserPlugin}). This local surrogate
 * uses the same method signatures so replacing it with an {@code @Override} binding
 * against the real SPI is a single import-rewrite.
 */
public interface FileParserPlugin {

  boolean accepts(String mimeType, String filename);

  int parse(ParseContext ctx);

  interface ParseContext {
    byte[] bytes();
    String filename();
    Optional<String> parentDataObjectAppId();
    Optional<String> fileReferenceAppId();
    AnnotationWriter annotations();
  }

  @FunctionalInterface
  interface AnnotationWriter {
    void write(String subjectAppId, String predicate, String value);
  }
}
