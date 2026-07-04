package de.dlr.shepard.v2.shapes.builder;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShaclShapeBuilderTest {

  static final String SHEPARD = "http://semantics.dlr.de/shepard#";
  static final String UPPER = "http://semantics.dlr.de/shepard-upper#";
  static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
  static final String XSD_INT = "http://www.w3.org/2001/XMLSchema#integer";

  ShaclShapeBuilder builder;
  JenaShaclValidator validator;

  @BeforeEach
  void setUp() {
    builder = new ShaclShapeBuilder();
    validator = new JenaShaclValidator();
  }

  // ─── Round-trip / golden ─────────────────────────────────────────────

  @Test
  void roundTripPreservesTargetClassAndAllPropertyConstraints() {
    var spec = new ShapeSpec(
      SHEPARD + "DataObjectShape",
      SHEPARD + "DataObject",
      false,
      List.of(
        new PropertyShapeSpec(
          UPPER + "status", XSD_STRING, 1, 1, List.of(InMember.literal("DRAFT"), InMember.literal("READY")), null
        ),
        new PropertyShapeSpec(
          UPPER + "owner", null, 1, null, null, SHEPARD + "PersonShape"
        )
      )
    );

    Model m = builder.toModel(spec);

    Resource nodeShape = m.getResource(SHEPARD + "DataObjectShape");
    assertThat(nodeShape.hasProperty(RDF.type, SHACLM.NodeShape)).isTrue();
    assertThat(nodeShape.getPropertyResourceValue(SHACLM.targetClass).getURI())
      .isEqualTo(SHEPARD + "DataObject");
    // open shape → no sh:closed triple
    assertThat(nodeShape.hasProperty(SHACLM.closed)).isFalse();

    // status property: path + datatype + min/max + in
    Resource statusShape = findPropertyByPath(m, nodeShape, UPPER + "status");
    assertThat(statusShape.hasProperty(RDF.type, SHACLM.PropertyShape)).isTrue();
    assertThat(statusShape.getPropertyResourceValue(SHACLM.datatype).getURI())
      .isEqualTo(XSD_STRING);
    assertThat(statusShape.getProperty(SHACLM.minCount).getInt()).isEqualTo(1);
    assertThat(statusShape.getProperty(SHACLM.maxCount).getInt()).isEqualTo(1);
    var inMembers = readRdfList(statusShape.getPropertyResourceValue(SHACLM.in));
    assertThat(inMembers).containsExactly("DRAFT", "READY");

    // owner property: path + min + node (nested shape), no datatype
    Resource ownerShape = findPropertyByPath(m, nodeShape, UPPER + "owner");
    assertThat(ownerShape.getProperty(SHACLM.minCount).getInt()).isEqualTo(1);
    assertThat(ownerShape.hasProperty(SHACLM.maxCount)).isFalse();
    assertThat(ownerShape.hasProperty(SHACLM.datatype)).isFalse();
    assertThat(ownerShape.getPropertyResourceValue(SHACLM.node).getURI())
      .isEqualTo(SHEPARD + "PersonShape");
  }

  @Test
  void closedShapeEmitsClosedTrue() {
    var spec = new ShapeSpec(SHEPARD + "ClosedShape", null, true, List.of());
    Model m = builder.toModel(spec);
    Resource s = m.getResource(SHEPARD + "ClosedShape");
    assertThat(s.getProperty(SHACLM.closed).getBoolean()).isTrue();
  }

  @Test
  void anonymousShapeGetsDeterministicIriNoBlankNodes() {
    var spec = new ShapeSpec(null, SHEPARD + "Thing", false,
      List.of(PropertyShapeSpec.of(UPPER + "name")));
    String turtle = builder.toTurtle(spec);
    assertThat(turtle).contains(ShaclShapeBuilder.DEFAULT_SHAPE_IRI);
    Model m = builder.toModel(spec);
    assertThat(m.listSubjects().filterKeep(RDFNode::isAnon).hasNext())
      .as("no blank nodes in the output")
      .isFalse();
  }

  @Test
  void inListWithAbsoluteIrisEmitsIriMembers() {
    var spec = new ShapeSpec(SHEPARD + "S", null, false,
      List.of(new PropertyShapeSpec(
        UPPER + "phase", null, null, null,
        List.of(InMember.iri("http://example.org/A"), InMember.iri("http://example.org/B")), null)));
    Model m = builder.toModel(spec);
    Resource ps = findPropertyByPath(m, m.getResource(SHEPARD + "S"), UPPER + "phase");
    var members = readRdfListAsResources(ps.getPropertyResourceValue(SHACLM.in));
    assertThat(members).containsExactly("http://example.org/A", "http://example.org/B");
  }

  @Test
  void inMemberKindIsExplicit_notHeuristic() {
    // The SAME colon-bearing string is a literal or an IRI purely by declared kind.
    var asLiteral = builder.toModel(new ShapeSpec(SHEPARD + "L", null, false,
      List.of(new PropertyShapeSpec(UPPER + "p", null, null, null,
        List.of(InMember.literal("urn:looks:like:iri")), null))));
    var litMembers = readRdfList(
      findPropertyByPath(asLiteral, asLiteral.getResource(SHEPARD + "L"), UPPER + "p")
        .getPropertyResourceValue(SHACLM.in));
    assertThat(litMembers).containsExactly("urn:looks:like:iri"); // a literal lexical form

    var asIri = builder.toModel(new ShapeSpec(SHEPARD + "I", null, false,
      List.of(new PropertyShapeSpec(UPPER + "p", null, null, null,
        List.of(InMember.iri("urn:looks:like:iri")), null))));
    var iriMembers = readRdfListAsResources(
      findPropertyByPath(asIri, asIri.getResource(SHEPARD + "I"), UPPER + "p")
        .getPropertyResourceValue(SHACLM.in));
    assertThat(iriMembers).containsExactly("urn:looks:like:iri"); // an IRI resource
  }

  // ─── Determinism ─────────────────────────────────────────────────────

  @Test
  void sameInputProducesByteIdenticalOutput() {
    var spec = new ShapeSpec(SHEPARD + "S", SHEPARD + "T", false,
      List.of(
        new PropertyShapeSpec(UPPER + "b", XSD_STRING, 0, 1, null, null),
        new PropertyShapeSpec(UPPER + "a", XSD_INT, 1, 1, null, null)));

    String first = builder.toTurtle(spec);
    String second = builder.toTurtle(spec);
    assertThat(second).isEqualTo(first);
  }

  @Test
  void reorderedPropertiesProduceIdenticalOutput() {
    var p1 = new PropertyShapeSpec(UPPER + "alpha", XSD_STRING, 1, 1, null, null);
    var p2 = new PropertyShapeSpec(UPPER + "beta", XSD_INT, 0, 1, null, null);
    var p3 = new PropertyShapeSpec(UPPER + "gamma", null, null, null, null, null);

    String forward = builder.toTurtle(
      new ShapeSpec(SHEPARD + "S", null, false, List.of(p1, p2, p3)));
    String reversed = builder.toTurtle(
      new ShapeSpec(SHEPARD + "S", null, false, List.of(p3, p2, p1)));

    assertThat(reversed)
      .as("property ordering is canonicalised by sh:path, so order-in cannot leak out")
      .isEqualTo(forward);
  }

  @Test
  void propertyCountMatchesInput() {
    var spec = new ShapeSpec(SHEPARD + "S", null, false,
      List.of(
        PropertyShapeSpec.of(UPPER + "a"),
        PropertyShapeSpec.of(UPPER + "b"),
        PropertyShapeSpec.of(UPPER + "c")));
    Model m = builder.toModel(spec);
    long count = m.getResource(SHEPARD + "S").listProperties(SHACLM.property).toList().size();
    assertThat(count).isEqualTo(3);
  }

  // ─── Closing the loop with the validator ─────────────────────────────

  @Test
  void compiledShapeValidatesConformantData() {
    var spec = new ShapeSpec(SHEPARD + "PersonShape", SHEPARD + "Person", false,
      List.of(new PropertyShapeSpec(
        UPPER + "status", XSD_STRING, 1, 1, List.of(InMember.literal("DRAFT"), InMember.literal("READY")), null)));
    String shapeTurtle = builder.toTurtle(spec);

    String goodData =
      "@prefix s: <" + SHEPARD + "> .\n" +
      "@prefix u: <" + UPPER + "> .\n" +
      "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
      "s:Alice a s:Person ; u:status \"READY\"^^xsd:string .";

    var report = validator.validate(goodData, shapeTurtle);
    assertThat(report.parseError()).isNull();
    assertThat(report.conforms()).isTrue();
  }

  @Test
  void compiledShapeRejectsViolatingData() {
    var spec = new ShapeSpec(SHEPARD + "PersonShape", SHEPARD + "Person", false,
      List.of(new PropertyShapeSpec(
        UPPER + "status", XSD_STRING, 1, 1, List.of(InMember.literal("DRAFT"), InMember.literal("READY")), null)));
    String shapeTurtle = builder.toTurtle(spec);

    // status "OBSOLETE" is not in the sh:in enumeration → violation.
    String badData =
      "@prefix s: <" + SHEPARD + "> .\n" +
      "@prefix u: <" + UPPER + "> .\n" +
      "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
      "s:Bob a s:Person ; u:status \"OBSOLETE\"^^xsd:string .";

    var report = validator.validate(badData, shapeTurtle);
    assertThat(report.parseError()).isNull();
    assertThat(report.conforms()).isFalse();
    assertThat(report.findings()).isNotEmpty();
  }

  @Test
  void compiledShapeFlagsMissingRequiredProperty() {
    var spec = new ShapeSpec(SHEPARD + "PersonShape", SHEPARD + "Person", false,
      List.of(new PropertyShapeSpec(UPPER + "status", XSD_STRING, 1, 1, null, null)));
    String shapeTurtle = builder.toTurtle(spec);

    String missing = "@prefix s: <" + SHEPARD + "> . s:Carol a s:Person .";

    var report = validator.validate(missing, shapeTurtle);
    assertThat(report.conforms()).isFalse();
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private static Resource findPropertyByPath(Model m, Resource nodeShape, String pathIri) {
    var it = nodeShape.listProperties(SHACLM.property);
    while (it.hasNext()) {
      Resource ps = it.nextStatement().getResource();
      Resource path = ps.getPropertyResourceValue(SHACLM.path);
      if (path != null && pathIri.equals(path.getURI())) {
        return ps;
      }
    }
    throw new AssertionError("no property shape with sh:path " + pathIri);
  }

  /** Read an RDF list of literals into their lexical forms. */
  private static List<String> readRdfList(Resource head) {
    var out = new java.util.ArrayList<String>();
    Resource cur = head;
    while (cur != null && !cur.equals(RDF.nil)) {
      RDFNode first = cur.getProperty(rdfFirst()).getObject();
      out.add(first.asLiteral().getLexicalForm());
      cur = cur.getPropertyResourceValue(rdfRest());
    }
    return out;
  }

  private static List<String> readRdfListAsResources(Resource head) {
    var out = new java.util.ArrayList<String>();
    Resource cur = head;
    while (cur != null && !cur.equals(RDF.nil)) {
      RDFNode first = cur.getProperty(rdfFirst()).getObject();
      out.add(first.asResource().getURI());
      cur = cur.getPropertyResourceValue(rdfRest());
    }
    return out;
  }

  private static Property rdfFirst() {
    return RDF.first;
  }

  private static Property rdfRest() {
    return RDF.rest;
  }
}
