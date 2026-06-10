package de.dlr.shepard.v2.shapes.mffd;

import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.shapes.builder.ChannelBindingSpec;
import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ViewRecipeSpec;
import java.util.List;

/**
 * MFFD-TPL-MATERIAL-BATCH — DATAOBJECT_RECIPE shape for {@code mffd:material-batch}.
 *
 * <p>Seeds via {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} (B7 pattern) a
 * system-tagged {@code DATAOBJECT_RECIPE} template named {@code mffd-material-batch-data-shape}.
 * The template captures the keystone provenance chain: every AFP course, weld step, and
 * NDT measurement can link back to the material batch it consumed via the
 * {@code urn:shepard:mffd:material-batch} IRI predicate, enabling "show me all process
 * steps from batch X" Cypher / SPARQL queries without ad-hoc free-text search.
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}.
 */
public final class MffdMaterialBatchKind implements PayloadKind {

  static final String SHAPE_IRI = "urn:shepard:shape:mffd-material-batch";

  /**
   * MFFD-RENDER-MATERIAL-BATCH-TRACE — IRI of the MAPPING_RECIPE view-shape that
   * renders the lineage graph of every process step (AFP course, weld step, NDT
   * measurement, plybook) consuming a given material batch. Dispatched through
   * {@link de.dlr.shepard.spi.view.ViewRecipeRendererRegistry}; reuses the AAE3
   * lineage visual.
   */
  static final String TRACE_SHAPE_IRI =
      "urn:shepard:shape:mffd-material-batch-trace#MaterialBatchTraceShape";

  /** Renderer hint consumed by {@code POST /v2/shapes/render} — the lineage graph visual (AAE3). */
  static final String TRACE_RENDERER = "lineage";

  /**
   * Binding role carrying the material-batch DataObject IRI whose consuming
   * process-step lineage is to be traced. The selector is the
   * {@code urn:shepard:mffd:material-batch} predicate followed (inbound) from
   * every consuming step back to the batch.
   */
  static final String TRACE_ROLE_BATCH = "materialBatch";

  static final String PRED_BATCH_ID            = "urn:shepard:mffd:batch-id";
  static final String PRED_MATERIAL_CLASS      = "urn:shepard:mffd:material-class";
  static final String PRED_SUPPLIER            = "urn:shepard:mffd:supplier";
  static final String PRED_MANUFACTURED_DATE   = "urn:shepard:mffd:manufactured-date";
  static final String PRED_EXPIRY_DATE         = "urn:shepard:mffd:expiry-date";
  static final String PRED_STORAGE_CONDITION   = "urn:shepard:mffd:storage-condition";
  static final String PRED_CERTIFICATE_REF     = "urn:shepard:mffd:certificate-ref";

  static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
  static final String XSD_DATE   = "http://www.w3.org/2001/XMLSchema#date";

  @Override
  public String name() {
    return "mffd-material-batch";
  }

  @Override
  public List<String> entityPackages() {
    return List.of();
  }

  /**
   * MFFD-TPL-MATERIAL-BATCH — SHACL NodeShape for material-batch DataObjects.
   * Open shape ({@code sh:closed false}); all properties optional
   * ({@code sh:minCount 0}) so existing DataObjects that lack a field remain valid.
   */
  @Override
  public ShapeSpec shapeDescriptor() {
    return new ShapeSpec(
      SHAPE_IRI,
      null,
      false,
      List.of(
        // Manufacturer's batch identifier (e.g. Toray lot number, SAERTEX roll ID).
        new PropertyShapeSpec(PRED_BATCH_ID, XSD_STRING, 0, 1, null, null),
        // Material family — controlled vocabulary.
        new PropertyShapeSpec(
          PRED_MATERIAL_CLASS, XSD_STRING, 0, 1,
          List.of(
            InMember.literal("LMPAEK"),
            InMember.literal("SAERTEX-NCF"),
            InMember.literal("EPOXY-PREPREG"),
            InMember.literal("GLASS-FIBER-NCF"),
            InMember.literal("CARBON-FIBER-TAPE"),
            InMember.literal("OTHER")
          ),
          null
        ),
        // Supplier name (free text — suppliers vary per institute procurement).
        new PropertyShapeSpec(PRED_SUPPLIER, XSD_STRING, 0, 1, null, null),
        // ISO 8601 date of manufacture (xsd:date, e.g. "2025-03-15").
        new PropertyShapeSpec(PRED_MANUFACTURED_DATE, XSD_DATE, 0, 1, null, null),
        // Shelf-life expiry date (xsd:date).
        new PropertyShapeSpec(PRED_EXPIRY_DATE, XSD_DATE, 0, 1, null, null),
        // Storage condition key (e.g. "frozen-below-minus18C", "dry-room").
        new PropertyShapeSpec(PRED_STORAGE_CONDITION, XSD_STRING, 0, 1, null, null),
        // IRI of a FileReference DataObject holding the material certificate PDF.
        // IRI-valued (no sh:datatype); resolved via /v2/files/{appId}/content.
        new PropertyShapeSpec(PRED_CERTIFICATE_REF, null, 0, 1, null, null)
      )
    );
  }

  /**
   * MFFD-RENDER-MATERIAL-BATCH-TRACE — VIEW_RECIPE view-shape for the
   * material-batch lineage trace.
   *
   * <p>Seeded by {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} as a
   * system-tagged {@code VIEW_RECIPE} template named
   * {@code mffd-material-batch-view-shape}. Given a {@code mffd:material-batch}
   * DataObject IRI bound to the {@value #TRACE_ROLE_BATCH} role, the registered
   * lineage renderer walks the inbound {@code urn:shepard:mffd:material-batch}
   * edges to surface every consuming AFP course, weld step, and NDT measurement —
   * answering "show me everything from batch X" as a graph rather than a flat list.
   *
   * <p>The binding is required so the renderer surfaces a MISSING status when no
   * batch IRI is supplied. The renderer hint ({@value #TRACE_RENDERER}) reuses the
   * AAE3 lineage visual; the shape IRI ({@value #TRACE_SHAPE_IRI}) is the dispatch
   * key for the {@code ViewRecipeRendererRegistry}.
   */
  @Override
  public ViewRecipeSpec viewShapeDescriptor() {
    return new ViewRecipeSpec(
      TRACE_SHAPE_IRI,
      TRACE_RENDERER,
      List.of(ChannelBindingSpec.required(TRACE_ROLE_BATCH, PRED_MATERIAL_BATCH))
    );
  }

  /**
   * Predicate followed (inbound) from each consuming process step back to the
   * material batch it consumed. The same IRI every AFP course / weld step / NDT
   * measurement template carries (see {@code MffdAfpCourseKind},
   * {@code MffdWeldStepKind}).
   */
  static final String PRED_MATERIAL_BATCH = "urn:shepard:mffd:material-batch";
}
