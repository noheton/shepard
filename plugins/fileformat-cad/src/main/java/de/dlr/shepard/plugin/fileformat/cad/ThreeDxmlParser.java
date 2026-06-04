package de.dlr.shepard.plugin.fileformat.cad;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parser for Dassault Systèmes 3DXML format (.3dxml).
 *
 * <p>3DXML is a ZIP archive containing:
 * <ul>
 *   <li>{@code Manifest.xml} — root descriptor with schema version + default representation.</li>
 *   <li>{@code *.3DRep} or {@code *.xml} — geometry/assembly entries.</li>
 *   <li>{@code ProductStructure.xml} — product hierarchy (when present).</li>
 * </ul>
 *
 * <p>Phase 1 extracts: schema version, default representation name,
 * author/date from {@code <AuthorAndDateCreated>}, and instance count from
 * the {@code <ReferenceRep>} and {@code <Reference3D>} entries.
 */
public class ThreeDxmlParser {

  public boolean accepts(byte[] bytes) {
    // Accept any ZIP variant: PK signature (0x50 0x4B) covers local file header
    // (PK\x03\x04), central directory (PK\x01\x02), and empty ZIP (PK\x05\x06)
    return bytes != null && bytes.length >= 2 && bytes[0] == 0x50 && bytes[1] == 0x4B;
  }

  public Map<String, String> parse(byte[] bytes) {
    Map<String, String> result = new LinkedHashMap<>();
    if (!accepts(bytes)) return result;

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      String manifestXml = null;
      String productXml = null;

      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();
        if (name.equalsIgnoreCase("Manifest.xml") && manifestXml == null) {
          manifestXml = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } else if ((name.contains("ProductStructure") || name.contains("3DRep")) && productXml == null) {
          productXml = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        zis.closeEntry();
      }

      if (manifestXml != null) parseManifest(manifestXml, result);
      if (productXml != null) parseProduct(productXml, result);

    } catch (IOException e) {
      // Partial parse is acceptable; return what we have
    }

    result.put(CadAnnotations.FORMAT, "3dxml");
    return result;
  }

  // ── private helpers ──────────────────────────────────────────────────────

  private void parseManifest(String xml, Map<String, String> out) {
    Document doc = parseXml(xml);
    if (doc == null) return;

    Element root = doc.getDocumentElement();

    String version = root.getAttribute("version");
    if (!version.isEmpty()) out.put(CadAnnotations.APPLICATION, "Dassault 3DXML v" + version);



    NodeList meta = doc.getElementsByTagName("AuthorAndDateCreated");
    if (meta.getLength() > 0) {
      String text = meta.item(0).getTextContent().trim();
      String[] parts = text.split(",", 2);
      if (parts.length == 2) {
        out.put(CadAnnotations.AUTHOR, parts[0].trim());
        out.put(CadAnnotations.CREATED_AT, parts[1].trim());
      } else if (!text.isEmpty()) {
        out.put(CadAnnotations.AUTHOR, text);
      }
    }
  }

  private void parseProduct(String xml, Map<String, String> out) {
    Document doc = parseXml(xml);
    if (doc == null) return;

    NodeList refs = doc.getElementsByTagName("Reference3D");
    if (refs.getLength() > 0 && out.get(CadAnnotations.PRODUCT_NAME) == null) {
      Element ref = (Element) refs.item(0);
      String name = ref.getAttribute("name");
      if (!name.isEmpty()) out.put(CadAnnotations.PRODUCT_NAME, name);
    }

    int instanceCount = doc.getElementsByTagName("Instance3D").getLength();
    if (instanceCount > 0) {
      out.put(CadAnnotations.CATIA_INSTANCE_COUNT, String.valueOf(instanceCount));
    }
  }

  private Document parseXml(String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      InputStream in = new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return factory.newDocumentBuilder().parse(in);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      return null;
    }
  }
}
