package de.dlr.shepard.v2.template.services;

import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.CellMappingIO;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.FieldIO;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.GroupIO;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO.SubmitIO;
import de.dlr.shepard.v2.template.resources.TemplateInstantiationRest;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * FORM-DESCRIPTOR-1 — compiles a flattened {@code shapeGraph} (canonical SHACL
 * Turtle, typically {@link ShaclShapeBuilder}-emitted) into the
 * {@link TemplateFormDescriptorIO} wire shape of doc 125 §5.1.
 *
 * <p><b>Deterministic.</b> Same template + same shapeGraph → byte-identical
 * descriptor: node shapes and property shapes are visited in sorted-IRI order,
 * fields are then ordered by {@code sh:order} (unordered fields last,
 * alphabetical by label — DASH's documented rule), groups by {@code sh:order}
 * then IRI.
 *
 * <p><b>DASH default-editor scoring.</b> When a property shape carries no
 * explicit {@code dash:editor}, the compiler applies the constraint-derived
 * defaults (doc 125 §4.2 Layer 2): {@code sh:in} → EnumSelect,
 * {@code xsd:boolean} → BooleanSelect, {@code xsd:date}/{@code xsd:dateTime} →
 * Date(Time)Picker, {@code dash:singleLine false} → TextArea, else TextField —
 * so an unannotated shape still renders a sensible form.
 *
 * <p><b>Stateless / thread-safe.</b> Mirrors {@code JenaShaclValidator}: one
 * hot CDI instance; fresh Jena Models per call that never escape it.
 */
@ApplicationScoped
public class FormDescriptorCompiler {

  static final String SH = "http://www.w3.org/ns/shacl#";
  static final String XSD = "http://www.w3.org/2001/XMLSchema#";

  // DASH editor IRIs used by the default scoring.
  static final String EDITOR_TEXT_FIELD = ShaclShapeBuilder.DASH + "TextFieldEditor";
  static final String EDITOR_TEXT_AREA = ShaclShapeBuilder.DASH + "TextAreaEditor";
  static final String EDITOR_ENUM_SELECT = ShaclShapeBuilder.DASH + "EnumSelectEditor";
  static final String EDITOR_BOOLEAN_SELECT = ShaclShapeBuilder.DASH + "BooleanSelectEditor";
  static final String EDITOR_DATE_PICKER = ShaclShapeBuilder.DASH + "DatePickerEditor";
  static final String EDITOR_DATE_TIME_PICKER = ShaclShapeBuilder.DASH + "DateTimePickerEditor";

  /** The data kinds whose shapes are form-renderable (doc 125 D1). */
  public static final Set<String> FORM_RENDERABLE_KINDS = Set.of(
    "DATAOBJECT_RECIPE",
    "COLLECTION_RECIPE",
    "STRUCTURED_RECIPE"
  );

  /**
   * Compile the descriptor.
   *
   * @param template   the resolved (non-retired) template — supplies appId,
   *                   kind, and title
   * @param shapeGraph the inheritance-flattened SHACL Turtle; must be non-blank
   * @return the descriptor
   * @throws IllegalArgumentException when the Turtle does not parse or carries
   *         no {@code sh:NodeShape} — the REST layer maps this to a 422
   */
  public TemplateFormDescriptorIO compile(ShepardTemplate template, String shapeGraph) {
    Model model = parse(shapeGraph);

    Property shProperty = model.createProperty(SH + "property");
    Resource shNodeShape = model.createResource(SH + "NodeShape");
    Resource shPropertyGroup = model.createResource(SH + "PropertyGroup");

    List<Resource> nodeShapes = model
      .listResourcesWithProperty(RDF.type, shNodeShape)
      .toList()
      .stream()
      .filter(Resource::isURIResource)
      .sorted(Comparator.comparing(Resource::getURI))
      .toList();
    if (nodeShapes.isEmpty()) {
      throw new IllegalArgumentException("shapeGraph carries no sh:NodeShape — nothing to render as a form");
    }
    String shapeIri = nodeShapes.get(0).getURI();

    // Property shapes from ALL node shapes (inheritance concatenates parent +
    // child shapes into one graph), visited deterministically.
    List<FieldIO> fields = new ArrayList<>();
    for (Resource nodeShape : nodeShapes) {
      List<Resource> propShapes = nodeShape
        .listProperties(shProperty)
        .toList()
        .stream()
        .map(Statement::getObject)
        .filter(RDFNode::isResource)
        .map(RDFNode::asResource)
        .sorted(Comparator.comparing(r -> r.isURIResource() ? r.getURI() : r.getId().getLabelString()))
        .toList();
      for (Resource ps : propShapes) {
        FieldIO field = compileField(model, ps);
        if (field != null) {
          fields.add(field);
        }
      }
    }
    fields.sort(
      Comparator.comparing((FieldIO f) -> f.order() == null)
        .thenComparing(f -> f.order() == null ? 0d : f.order())
        .thenComparing(f -> f.label() == null ? "" : f.label())
    );

    // Groups: every sh:PropertyGroup, ordered by sh:order then IRI.
    List<GroupIO> groups = model
      .listResourcesWithProperty(RDF.type, shPropertyGroup)
      .toList()
      .stream()
      .filter(Resource::isURIResource)
      .map(g -> new GroupIO(g.getURI(), str(g, RDFS.label.getURI()), dbl(g, SH + "order")))
      .sorted(
        Comparator.comparing((GroupIO g) -> g.order() == null)
          .thenComparing(g -> g.order() == null ? 0d : g.order())
          .thenComparing(GroupIO::id)
      )
      .toList();

    return new TemplateFormDescriptorIO(
      template.getAppId(),
      template.getTemplateKind(),
      template.getName(),
      shapeIri,
      groups,
      fields,
      submitFor(template)
    );
  }

  // ─── helpers ───────────────────────────────────────────────────────

  private FieldIO compileField(Model model, Resource ps) {
    String path = uri(ps, SH + "path");
    if (path == null) {
      return null; // a property shape without sh:path is not renderable
    }
    String datatype = uri(ps, SH + "datatype");
    Integer minCount = intOf(ps, SH + "minCount");
    Boolean required = (minCount != null && minCount >= 1) ? Boolean.TRUE : null;
    String pattern = str(ps, SH + "pattern");
    String name = str(ps, SH + "name");
    String description = str(ps, SH + "description");
    Double order = dbl(ps, SH + "order");
    String group = uri(ps, SH + "group");
    String defaultValue = str(ps, SH + "defaultValue");
    String explicitEditor = uri(ps, ShaclShapeBuilder.DASH + "editor");
    Boolean singleLine = bool(ps, ShaclShapeBuilder.DASH + "singleLine");
    String placeholder = str(ps, ShaclShapeBuilder.FORM_PLACEHOLDER);
    String visibleWhen = str(ps, ShaclShapeBuilder.FORM_VISIBLE_WHEN);
    List<String> options = inList(model, ps);

    String editor = explicitEditor != null
      ? explicitEditor
      : defaultEditor(datatype, options, singleLine);

    String cell = str(ps, ShaclShapeBuilder.BTKVS_CELL_MAPPING);
    String sheet = str(ps, ShaclShapeBuilder.BTKVS_SHEET);
    CellMappingIO cellMapping = cell == null ? null : new CellMappingIO(sheet, cell);

    String attributeKey = path.startsWith(TemplateInstantiationRest.ATTR_NS)
      ? path.substring(TemplateInstantiationRest.ATTR_NS.length())
      : null;
    String label = name != null ? name : (attributeKey != null ? attributeKey : localName(path));

    return new FieldIO(
      path,
      attributeKey,
      label,
      description,
      group,
      order,
      datatype,
      required,
      pattern,
      editor,
      singleLine,
      placeholder,
      defaultValue,
      options,
      visibleWhen,
      cellMapping
    );
  }

  /**
   * DASH constraint-scoring default editor (doc 125 §4.2 Layer 2) — applied
   * only when no explicit {@code dash:editor} is present.
   */
  static String defaultEditor(String datatype, List<String> options, Boolean singleLine) {
    if (options != null && !options.isEmpty()) {
      return EDITOR_ENUM_SELECT;
    }
    if ((XSD + "boolean").equals(datatype)) {
      return EDITOR_BOOLEAN_SELECT;
    }
    if ((XSD + "date").equals(datatype)) {
      return EDITOR_DATE_PICKER;
    }
    if ((XSD + "dateTime").equals(datatype)) {
      return EDITOR_DATE_TIME_PICKER;
    }
    if (Boolean.FALSE.equals(singleLine)) {
      return EDITOR_TEXT_AREA;
    }
    return EDITOR_TEXT_FIELD;
  }

  /**
   * Server-computed submit block: data-kind templates submit through the
   * existing V2CONV-B2-validated instantiation endpoint. The client fills
   * {@code {collectionAppId}} from its navigation context — it never chooses
   * an endpoint (doc 125 §5.1).
   */
  private static SubmitIO submitFor(ShepardTemplate template) {
    return new SubmitIO(
      "POST",
      "/v2/collections/{collectionAppId}/data-objects/from-template/" + template.getAppId(),
      "problem+json violations[] keyed by field path"
    );
  }

  private Model parse(String turtle) {
    try {
      Model m = ModelFactory.createDefaultModel();
      RDFParser.create()
        .source(new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)))
        .lang(Lang.TURTLE)
        .parse(m);
      return m;
    } catch (RiotException ex) {
      throw new IllegalArgumentException("shapeGraph is not parseable Turtle: " + ex.getMessage(), ex);
    }
  }

  private static String str(Resource r, String predicate) {
    Statement s = r.getProperty(r.getModel().createProperty(predicate));
    if (s == null || !s.getObject().isLiteral()) return null;
    return s.getObject().asLiteral().getLexicalForm();
  }

  private static String uri(Resource r, String predicate) {
    Statement s = r.getProperty(r.getModel().createProperty(predicate));
    if (s == null || !s.getObject().isURIResource()) return null;
    return s.getObject().asResource().getURI();
  }

  private static Integer intOf(Resource r, String predicate) {
    Statement s = r.getProperty(r.getModel().createProperty(predicate));
    if (s == null || !s.getObject().isLiteral()) return null;
    try {
      return s.getObject().asLiteral().getInt();
    } catch (Exception e) {
      return null;
    }
  }

  private static Double dbl(Resource r, String predicate) {
    Statement s = r.getProperty(r.getModel().createProperty(predicate));
    if (s == null || !s.getObject().isLiteral()) return null;
    try {
      return s.getObject().asLiteral().getDouble();
    } catch (Exception e) {
      return null;
    }
  }

  private static Boolean bool(Resource r, String predicate) {
    Statement s = r.getProperty(r.getModel().createProperty(predicate));
    if (s == null || !s.getObject().isLiteral()) return null;
    try {
      return s.getObject().asLiteral().getBoolean();
    } catch (Exception e) {
      return null;
    }
  }

  /** Lexical forms (literals) / IRIs (resources) of the {@code sh:in} list. */
  private static List<String> inList(Model model, Resource ps) {
    Statement s = ps.getProperty(model.createProperty(SH + "in"));
    if (s == null || !s.getObject().canAs(RDFList.class)) return null;
    List<String> out = new ArrayList<>();
    for (RDFNode n : s.getObject().as(RDFList.class).asJavaList()) {
      if (n.isLiteral()) {
        out.add(((Literal) n).getLexicalForm());
      } else if (n.isURIResource()) {
        out.add(n.asResource().getURI());
      }
    }
    return out.isEmpty() ? null : out;
  }

  private static String localName(String iri) {
    int idx = Math.max(Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/')), iri.lastIndexOf(':'));
    return idx >= 0 && idx < iri.length() - 1 ? iri.substring(idx + 1) : iri;
  }
}
