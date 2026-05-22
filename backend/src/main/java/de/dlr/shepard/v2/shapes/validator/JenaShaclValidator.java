package de.dlr.shepard.v2.shapes.validator;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;

/**
 * PR-1 of the SHACL changeover (non-TS scope, brief 2026-05-22) —
 * in-process Jena SHACL validator.
 *
 * <p><b>Contract.</b> Take two Turtle strings (candidate data graph
 * and shape graph), parse them with Jena RIOT, run
 * {@code ShaclValidator.get().validate(shapes, dataGraph)}, return a
 * structured {@link Report} the REST layer can serialise to JSON.
 *
 * <p><b>What this does not do.</b> It does NOT read RDF out of n10s
 * (that's PR-2 substrate work — deferred), it does NOT enforce a
 * pre-registered shape catalog (that's Template B1 with
 * {@code kind=view}, already shipped), and it does NOT compute the
 * {@code mffd:auditHmac} chain (that's PR-3, separate service).
 *
 * <p><b>Why CDI-scoped.</b> The Jena {@code ShaclValidator} is
 * thread-safe and stateless; one application-scoped instance keeps
 * the implementation hot. Each {@link #validate} call constructs
 * fresh {@code Model}s — Jena Models are NOT thread-safe but they
 * never escape the call.
 *
 * <p><b>Failure modes.</b>
 * <ul>
 *   <li>Malformed Turtle in either input → {@link Report#parseError}
 *       set, {@code conforms=false}, no exception bubbles.</li>
 *   <li>Empty shape graph → {@code conforms=true}, zero findings
 *       (nothing to violate). Caller's job to decide whether that's
 *       a useful answer.</li>
 *   <li>Shape graph references vocabularies outside the data graph
 *       → Jena imports nothing; whatever's not present is "absent",
 *       which is the correct SHACL semantics for closed validation.</li>
 * </ul>
 *
 * @see <a href="https://jena.apache.org/documentation/shacl/">Jena SHACL</a>
 * @see <a href="../../../../../../aidocs/semantics/98-mffd-process-shapes.md">aidocs/98</a>
 */
@ApplicationScoped
public class JenaShaclValidator {

  /** Default RDF language for inputs — Turtle is what shape files ship in. */
  static final Lang DEFAULT_LANG = Lang.TURTLE;

  /**
   * Validate {@code dataTurtle} against {@code shapeTurtle}.
   *
   * @param dataTurtle  candidate data graph, Turtle-serialised, must not be {@code null}
   * @param shapeTurtle shape graph, Turtle-serialised, must not be {@code null}
   * @return a {@link Report} carrying the conformance verdict + per-finding details
   */
  public Report validate(String dataTurtle, String shapeTurtle) {
    if (dataTurtle == null || shapeTurtle == null) {
      return Report.parseError("dataTurtle and shapeTurtle must both be non-null");
    }
    Model dataModel;
    try {
      dataModel = parseTurtle(dataTurtle);
    } catch (RiotException ex) {
      return Report.parseError("data graph parse error: " + ex.getMessage());
    }
    Model shapeModel;
    try {
      shapeModel = parseTurtle(shapeTurtle);
    } catch (RiotException ex) {
      return Report.parseError("shape graph parse error: " + ex.getMessage());
    }

    Shapes shapes;
    try {
      shapes = Shapes.parse(shapeModel.getGraph());
    } catch (Exception ex) {
      // Shapes.parse throws RuntimeException for malformed shape
      // structures (not just syntactic Turtle errors).
      Log.warnf("JenaShaclValidator: shape graph rejected (%s).", ex.getClass().getSimpleName());
      return Report.parseError("shape graph rejected by SHACL parser: " + ex.getMessage());
    }

    ValidationReport report;
    try {
      report = ShaclValidator.get().validate(shapes, dataModel.getGraph());
    } catch (Exception ex) {
      Log.warnf("JenaShaclValidator: validation engine error (%s).", ex.getClass().getSimpleName());
      return Report.engineError("validation engine error: " + ex.getMessage());
    }

    return Report.from(report);
  }

  /** Package-private for test seam. */
  Model parseTurtle(String turtle) {
    Model m = ModelFactory.createDefaultModel();
    RDFParser.create()
      .source(new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)))
      .lang(DEFAULT_LANG)
      .parse(m);
    return m;
  }

  // ─── DTO ──────────────────────────────────────────────────────────

  /**
   * Engine-agnostic validation report. Mirrors the SHACL spec's
   * "validation report" shape but uses plain Java types so the REST
   * layer can serialise without dragging Jena classes onto the wire.
   *
   * <p>A finding's {@code focusNode} / {@code resultPath} are
   * stringified IRIs (or blank-node labels). {@code message} is the
   * SHACL {@code sh:resultMessage} text where the shape supplies
   * one; the empty string otherwise.
   */
  public record Report(
    boolean conforms,
    String parseError,
    String engineError,
    List<Finding> findings
  ) {
    /** Successful validation outcome (may still carry findings if {@code conforms=false}). */
    public static Report from(ValidationReport jena) {
      var findings = new ArrayList<Finding>();
      for (ReportEntry e : jena.getEntries()) {
        findings.add(
          new Finding(
            renderNode(e.focusNode()),
            renderPath(e),
            renderNode(e.value()),
            e.severity() == null ? "Violation" : e.severity().level().getLocalName(),
            e.message() == null ? "" : e.message()
          )
        );
      }
      return new Report(jena.conforms(), null, null, List.copyOf(findings));
    }

    /** Caller-side problem: malformed Turtle or shape structure. */
    public static Report parseError(String msg) {
      return new Report(false, msg, null, List.of());
    }

    /** Engine-side problem: Jena threw during validation. Rare. */
    public static Report engineError(String msg) {
      return new Report(false, null, msg, List.of());
    }

    private static String renderNode(org.apache.jena.graph.Node n) {
      if (n == null) return null;
      if (n.isURI()) return n.getURI();
      if (n.isBlank()) return "_:" + n.getBlankNodeLabel();
      if (n.isLiteral()) return n.getLiteralLexicalForm();
      return n.toString();
    }

    private static String renderPath(ReportEntry e) {
      // ReportEntry exposes the path as a Jena Path; stringifying
      // via toString() yields the SPARQL-like path notation that
      // round-trips for predicate-only paths (the common case in
      // our shapes) and degrades gracefully for inverse / sequence
      // paths. Good enough for the JSON wire shape; the test suite
      // can pin specific cases when the shapes grow more complex.
      try {
        if (e.resultPath() == null) return null;
        return e.resultPath().toString();
      } catch (Exception ex) {
        return null;
      }
    }
  }

  /**
   * One SHACL violation. All string fields nullable except
   * {@code severity}, which is one of
   * {@code "Violation" | "Warning" | "Info"}.
   */
  public record Finding(
    String focusNode,
    String resultPath,
    String value,
    String severity,
    String message
  ) {}
}
