package de.dlr.shepard.plugins.vistrace3d.scenegraph;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * V2CONV-B4 — self-contained URDF kinematic-tree parser for the
 * {@link SceneGraphPlayTransformExecutor}.
 *
 * <p>The bespoke {@code /v2/scene-graphs/*} subsystem dissolved into the
 * generic MAPPING_RECIPE mechanism (aidocs/platform/191 decision #2): the URDF
 * FileReference is now the single source of truth for the kinematic tree, and
 * the tree is <em>parsed on demand</em> here rather than materialised into a
 * stored {@code :DigitalTwinScene} / {@code :CoordinateFrame} / {@code :Joint}
 * graph.
 *
 * <p>This is a pure, dependency-free parse kernel — it ports the parse logic
 * that used to live in {@code ScenegraphFromUrdfService.parseUrdf} (now
 * deleted) into the plugin so the executor can build a play envelope without
 * the deleted scene-graph entities. URDF is plain XML (no xacro); the
 * {@link DocumentBuilderFactory} is configured with the OWASP secure-processing
 * defaults (no external entities, no DTD loading).
 */
public final class UrdfKinematics {

  private UrdfKinematics() {}

  /** A kinematic link parsed from a {@code <link>} element. */
  public record UrdfLink(String name) {}

  /** A kinematic joint parsed from a {@code <joint>} element. */
  public record UrdfJoint(
    String name,
    String type,
    String parentLink,
    String childLink,
    double originX,
    double originY,
    double originZ,
    double originRoll,
    double originPitch,
    double originYaw,
    double axisX,
    double axisY,
    double axisZ,
    Double limitLower,
    Double limitUpper
  ) {}

  /** The parsed kinematic model: robot name + links + joints + resolved root. */
  public record UrdfModel(String robotName, List<UrdfLink> links, List<UrdfJoint> joints) {
    /** The root link — the one link with no incoming joint. */
    public String rootLink() {
      Set<String> children = new HashSet<>();
      for (UrdfJoint j : joints) children.add(j.childLink());
      for (UrdfLink l : links) {
        if (!children.contains(l.name())) return l.name();
      }
      // No root (cycle, or every link is a child) — fall back to the first link.
      return links.isEmpty() ? null : links.get(0).name();
    }
  }

  /** Thrown when the stream is not valid URDF (missing {@code <robot>} root, etc.). */
  public static class UrdfParseException extends RuntimeException {
    public UrdfParseException(String message) {
      super(message);
    }
  }

  /**
   * Parse a URDF stream into a {@link UrdfModel}.
   *
   * @param is the URDF XML byte stream; the caller owns closing it
   * @return the parsed kinematic model (never null; may have empty joints)
   * @throws UrdfParseException when the stream is not valid URDF XML or lacks a
   *         {@code <robot>} root with at least one {@code <link>}
   */
  public static UrdfModel parse(InputStream is) {
    Document doc;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      DocumentBuilder db = dbf.newDocumentBuilder();
      doc = db.parse(is);
    } catch (ParserConfigurationException | SAXException xe) {
      throw new UrdfParseException("invalid URDF XML: " + xe.getMessage());
    } catch (IOException ioe) {
      throw new UrdfParseException("failed to read URDF stream: " + ioe.getMessage());
    }

    Element root = doc.getDocumentElement();
    if (root == null || !"robot".equals(root.getNodeName())) {
      throw new UrdfParseException("URDF root element must be <robot>");
    }

    String robotName = root.getAttribute("name");
    List<UrdfLink> links = new ArrayList<>();
    NodeList linkEls = root.getElementsByTagName("link");
    for (int i = 0; i < linkEls.getLength(); i++) {
      Element el = (Element) linkEls.item(i);
      if (el.getParentNode() != root) continue; // only direct kinematic links
      String nm = el.getAttribute("name");
      if (nm != null && !nm.isBlank()) links.add(new UrdfLink(nm));
    }

    List<UrdfJoint> joints = new ArrayList<>();
    NodeList jointEls = root.getElementsByTagName("joint");
    for (int i = 0; i < jointEls.getLength(); i++) {
      Element el = (Element) jointEls.item(i);
      if (el.getParentNode() != root) continue;
      Element parent = firstChildElement(el, "parent");
      Element child = firstChildElement(el, "child");
      if (parent == null || child == null) continue;

      String type = el.getAttribute("type");
      if (type == null || type.isBlank()) type = "fixed";

      double[] xyz = { 0, 0, 0 };
      double[] rpy = { 0, 0, 0 };
      Element origin = firstChildElement(el, "origin");
      if (origin != null) {
        xyz = parseTriple(origin.getAttribute("xyz"));
        rpy = parseTriple(origin.getAttribute("rpy"));
      }
      double[] axis = { 0, 0, 1 };
      Element axisEl = firstChildElement(el, "axis");
      if (axisEl != null) axis = parseTriple(axisEl.getAttribute("xyz"));

      Double lower = null;
      Double upper = null;
      Element limit = firstChildElement(el, "limit");
      if (limit != null) {
        lower = parseDoubleOrNull(limit.getAttribute("lower"));
        upper = parseDoubleOrNull(limit.getAttribute("upper"));
      }

      joints.add(new UrdfJoint(
        el.getAttribute("name"), type,
        parent.getAttribute("link"), child.getAttribute("link"),
        xyz[0], xyz[1], xyz[2], rpy[0], rpy[1], rpy[2],
        axis[0], axis[1], axis[2], lower, upper
      ));
    }

    if (links.isEmpty()) {
      throw new UrdfParseException("URDF must contain a <robot> root with at least one <link>");
    }
    return new UrdfModel(robotName, links, joints);
  }

  private static Element firstChildElement(Element parent, String tag) {
    NodeList kids = parent.getChildNodes();
    for (int i = 0; i < kids.getLength(); i++) {
      Node n = kids.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
        return (Element) n;
      }
    }
    return null;
  }

  private static double[] parseTriple(String s) {
    if (s == null || s.isBlank()) return new double[] { 0, 0, 0 };
    String[] parts = s.trim().split("\\s+");
    if (parts.length != 3) return new double[] { 0, 0, 0 };
    try {
      return new double[] {
        Double.parseDouble(parts[0]),
        Double.parseDouble(parts[1]),
        Double.parseDouble(parts[2]),
      };
    } catch (NumberFormatException nfe) {
      return new double[] { 0, 0, 0 };
    }
  }

  private static Double parseDoubleOrNull(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException nfe) {
      return null;
    }
  }
}
