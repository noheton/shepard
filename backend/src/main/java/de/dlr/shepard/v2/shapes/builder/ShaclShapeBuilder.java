package de.dlr.shepard.v2.shapes.builder;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;

/**
 * V2CONV-B1 — the SHACL shape-builder. A <b>deterministic compiler</b> from a
 * predicate-IRI JSON DSL ({@link ShapeSpec}/{@link PropertyShapeSpec}) to
 * canonical SHACL Turtle. The inverse of
 * {@code de.dlr.shepard.v2.shapes.validator.JenaShaclValidator}: that class
 * consumes a shape graph; this one produces one.
 *
 * <p>See {@code aidocs/platform/191-v2-surface-convergence.md §3} — "templates
 * ARE shapes". A {@code ShepardTemplate.body} carries the JSON DSL; this
 * compiler turns it into the canonical {@code shapeGraph} that drives
 * validation, create-form generation, rendering, and the agent contract.
 *
 * <p><b>Determinism.</b> Output is byte-identical for identical input:
 * <ul>
 *   <li>No blank nodes — the {@code sh:NodeShape} and every {@code sh:property}
 *       shape get stable, derived IRIs ({@code <shapeIri>}, {@code
 *       <shapeIri>/property/{n}}), so a round-trip is diffable.</li>
 *   <li>Property shapes are sorted by {@code sh:path} IRI (then by datatype)
 *       before numbering, so reordering the input list cannot change the
 *       output.</li>
 *   <li>{@code sh:in} lists preserve author order (membership is
 *       order-significant to a human reader; SHACL treats it as a set, so this
 *       is a presentation choice).</li>
 *   <li>Fixed prefix block, fixed triple ordering, two-space indentation.</li>
 * </ul>
 *
 * <p><b>Stateless / thread-safe.</b> No instance state; {@code @ApplicationScoped}
 * mirrors the validator so CDI can inject one hot instance.
 *
 * <p><b>sh:in literal-vs-IRI encoding (DSL decision, flagged for the operator).</b>
 * Each {@code in} entry that parses as an absolute IRI ({@code scheme:...}) is
 * emitted as an IRI; everything else is emitted as a literal. When the property
 * declares a {@code datatype}, literal entries are typed with it; otherwise they
 * default to {@code xsd:string}. This is the reasonable call for the common case
 * (status enums are strings, controlled-term lists are IRIs); a future explicit
 * {@code {value,type}} object form can override it if needed.
 */
@ApplicationScoped
public class ShaclShapeBuilder {

  static final String SH = "http://www.w3.org/ns/shacl#";
  static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  static final String XSD = "http://www.w3.org/2001/XMLSchema#";
  static final String XSD_STRING = XSD + "string";

  /** Default IRI minted for an anonymous shape so output stays blank-node-free. */
  static final String DEFAULT_SHAPE_IRI = "urn:shepard:shape:anonymous";

  /**
   * Compile a {@link ShapeSpec} to canonical SHACL Turtle.
   *
   * @param spec the shape specification; must not be {@code null} and must
   *             carry a non-blank {@code path} on every property
   * @return canonical, deterministic Turtle for the {@code sh:NodeShape}
   * @throws IllegalArgumentException if {@code spec} is null or a property has
   *         a null/blank {@code path}
   */
  public String toTurtle(ShapeSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    String shapeIri = (spec.shapeIri() == null || spec.shapeIri().isBlank())
      ? DEFAULT_SHAPE_IRI
      : spec.shapeIri().strip();

    List<PropertyShapeSpec> props = sortedProperties(spec.properties());

    StringBuilder sb = new StringBuilder();
    sb.append("@prefix sh: <").append(SH).append("> .\n");
    sb.append("@prefix rdf: <").append(RDF).append("> .\n");
    sb.append("@prefix xsd: <").append(XSD).append("> .\n");
    sb.append("\n");

    // Node shape.
    sb.append("<").append(shapeIri).append("> a sh:NodeShape");
    if (spec.targetClass() != null && !spec.targetClass().isBlank()) {
      sb.append(" ;\n  sh:targetClass <").append(spec.targetClass().strip()).append(">");
    }
    if (spec.closed()) {
      sb.append(" ;\n  sh:closed true");
    }
    for (int i = 0; i < props.size(); i++) {
      sb.append(" ;\n  sh:property <").append(propertyIri(shapeIri, i)).append(">");
    }
    sb.append(" .\n");

    // Property shapes, each a named resource (no blank nodes).
    for (int i = 0; i < props.size(); i++) {
      sb.append("\n");
      appendPropertyShape(sb, propertyIri(shapeIri, i), props.get(i));
    }

    return sb.toString();
  }

  /**
   * Compile a {@link ShapeSpec} to a Jena {@link Model}. The model is parsed
   * from {@link #toTurtle(ShapeSpec)}, so the two views are guaranteed
   * consistent.
   *
   * @param spec the shape specification
   * @return a Jena model carrying the {@code sh:NodeShape} triples
   */
  public Model toModel(ShapeSpec spec) {
    Model m = ModelFactory.createDefaultModel();
    RDFParser.create()
      .source(new ByteArrayInputStream(toTurtle(spec).getBytes(StandardCharsets.UTF_8)))
      .lang(Lang.TURTLE)
      .parse(m);
    return m;
  }

  // ─── helpers ───────────────────────────────────────────────────────

  private static List<PropertyShapeSpec> sortedProperties(List<PropertyShapeSpec> raw) {
    var out = new ArrayList<PropertyShapeSpec>();
    if (raw != null) {
      for (PropertyShapeSpec p : raw) {
        if (p == null) {
          throw new IllegalArgumentException("property spec must not be null");
        }
        if (p.path() == null || p.path().isBlank()) {
          throw new IllegalArgumentException("every property must carry a non-blank sh:path IRI");
        }
        out.add(p);
      }
    }
    out.sort(
      Comparator.comparing((PropertyShapeSpec p) -> p.path().strip())
        .thenComparing(p -> p.datatype() == null ? "" : p.datatype())
    );
    return out;
  }

  private static String propertyIri(String shapeIri, int index) {
    return shapeIri + "/property/" + index;
  }

  private void appendPropertyShape(StringBuilder sb, String iri, PropertyShapeSpec p) {
    sb.append("<").append(iri).append("> a sh:PropertyShape");
    sb.append(" ;\n  sh:path <").append(p.path().strip()).append(">");
    if (p.datatype() != null && !p.datatype().isBlank()) {
      sb.append(" ;\n  sh:datatype <").append(p.datatype().strip()).append(">");
    }
    if (p.minCount() != null) {
      sb.append(" ;\n  sh:minCount ").append(p.minCount());
    }
    if (p.maxCount() != null) {
      sb.append(" ;\n  sh:maxCount ").append(p.maxCount());
    }
    if (p.node() != null && !p.node().isBlank()) {
      sb.append(" ;\n  sh:node <").append(p.node().strip()).append(">");
    }
    if (p.in() != null && !p.in().isEmpty()) {
      sb.append(" ;\n  sh:in ").append(renderInList(p.in(), p.datatype()));
    }
    sb.append(" .\n");
  }

  /** Render {@code sh:in} as an RDF collection: {@code ( a b c )}. */
  private String renderInList(List<String> values, String datatype) {
    var sb = new StringBuilder("( ");
    for (String v : values) {
      sb.append(renderInMember(v, datatype)).append(" ");
    }
    sb.append(")");
    return sb.toString();
  }

  private String renderInMember(String value, String datatype) {
    if (value == null) {
      // Null member → empty typed string; keeps the list well-formed.
      return "\"\"^^<" + XSD_STRING + ">";
    }
    if (isAbsoluteIri(value)) {
      return "<" + value + ">";
    }
    String dt = (datatype == null || datatype.isBlank()) ? XSD_STRING : datatype.strip();
    return "\"" + escapeLiteral(value) + "\"^^<" + dt + ">";
  }

  /**
   * An {@code sh:in} entry is treated as an IRI when it looks like an absolute
   * IRI: a scheme ({@code [a-zA-Z][a-zA-Z0-9+.-]*}) followed by {@code :} and at
   * least one more character, and containing no whitespace.
   */
  static boolean isAbsoluteIri(String s) {
    if (s == null || s.isBlank() || s.chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    int colon = s.indexOf(':');
    if (colon < 1 || colon == s.length() - 1) {
      return false;
    }
    if (!Character.isLetter(s.charAt(0))) {
      return false;
    }
    for (int i = 0; i < colon; i++) {
      char c = s.charAt(i);
      if (!(Character.isLetterOrDigit(c) || c == '+' || c == '.' || c == '-')) {
        return false;
      }
    }
    return true;
  }

  private static String escapeLiteral(String s) {
    return s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t");
  }
}
