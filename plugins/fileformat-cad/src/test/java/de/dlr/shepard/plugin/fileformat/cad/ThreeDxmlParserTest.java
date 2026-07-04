package de.dlr.shepard.plugin.fileformat.cad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ThreeDxmlParserTest {

  private final ThreeDxmlParser parser = new ThreeDxmlParser();

  private static final String MANIFEST_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<Manifest version=\"4.3\" defaultFileRef=\"UPPER_SHELL/ProductStructure.xml\">\n"
          + "  <AuthorAndDateCreated>Florian Krebs, 2024-03-01T09:00:00</AuthorAndDateCreated>\n"
          + "</Manifest>";

  private static final String PRODUCT_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<Model_3dxml>\n"
          + "  <ProductStructure>\n"
          + "    <Reference3D id=\"1\" name=\"UPPER_SHELL_ASSEMBLY\" />\n"
          + "    <Instance3D id=\"2\" />\n"
          + "    <Instance3D id=\"3\" />\n"
          + "  </ProductStructure>\n"
          + "</Model_3dxml>";

  private byte[] build3dxml(String manifest, String product) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry mf = new ZipEntry("Manifest.xml");
      zos.putNextEntry(mf);
      zos.write(manifest.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
      ZipEntry prod = new ZipEntry("ProductStructure.xml");
      zos.putNextEntry(prod);
      zos.write(product.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return baos.toByteArray();
  }

  @Test
  void accepts_zipMagic() throws IOException {
    byte[] bytes = build3dxml(MANIFEST_XML, PRODUCT_XML);
    assertThat(parser.accepts(bytes)).isTrue();
  }

  @Test
  void rejects_nonZip() {
    assertThat(parser.accepts("ISO-10303-21;".getBytes())).isFalse();
    assertThat(parser.accepts(new byte[]{1, 2, 3})).isFalse();
    assertThat(parser.accepts(null)).isFalse();
  }

  @Test
  void parse_extractsManifestAndProduct() throws IOException {
    byte[] bytes = build3dxml(MANIFEST_XML, PRODUCT_XML);
    Map<String, String> result = parser.parse(bytes);

    assertThat(result.get(CadAnnotations.APPLICATION)).startsWith("Dassault 3DXML");
    assertThat(result.get(CadAnnotations.AUTHOR)).isEqualTo("Florian Krebs");
    assertThat(result.get(CadAnnotations.CREATED_AT)).isEqualTo("2024-03-01T09:00:00");
    assertThat(result.get(CadAnnotations.PRODUCT_NAME)).isEqualTo("UPPER_SHELL_ASSEMBLY");
    assertThat(result.get(CadAnnotations.CATIA_INSTANCE_COUNT)).isEqualTo("2");
    assertThat(result.get(CadAnnotations.FORMAT)).isEqualTo("3dxml");
  }

  @Test
  void parse_returnsFormatEvenForEmptyZip() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) { /* empty */ }
    Map<String, String> result = parser.parse(baos.toByteArray());
    assertThat(result.get(CadAnnotations.FORMAT)).isEqualTo("3dxml");
  }
}
