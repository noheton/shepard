package de.dlr.shepard.v2.shapes.mffd;

import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderResponse.ChannelBindingProjection;
import de.dlr.shepard.spi.view.RenderResponse.ResolvedChannel;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import jakarta.enterprise.inject.spi.CDI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 2) — {@link ViewRecipeRenderer} that
 * walks the inbound {@code urn:shepard:mffd:material-batch} semantic-annotation
 * edges from the focus material-batch DataObject and returns each consuming
 * DataObject as a projected lineage node.
 *
 * <p>Given a {@code mffd:material-batch} DataObject appId as
 * {@link RenderRequest#focusShepardId()}, queries
 * {@link SemanticAnnotationDAO#findByPredicateAndValue} for all
 * {@code :SemanticAnnotation} nodes whose predicate is
 * {@link MffdMaterialBatchKind#PRED_MATERIAL_BATCH} and whose object value equals
 * the batch appId. Each matching annotation's {@code subjectAppId} is a DataObject
 * that consumed this batch (an AFP course, weld step, NDT measurement, etc.).
 *
 * <h2>Response shape</h2>
 *
 * <ul>
 *   <li>No consumers → single {@code MISSING} binding with {@code required=true}.</li>
 *   <li>N consumers → N {@code OK} bindings, each carrying a
 *       {@link ResolvedChannel} whose {@code channelRef} is the consumer
 *       DataObject's appId. Null {@code subjectAppId} entries are silently
 *       skipped.</li>
 * </ul>
 *
 * <h2>CDI lookup</h2>
 *
 * <p>This class is a plain ServiceLoader POJO — it is NOT a CDI bean. It performs
 * a lazy CDI lookup for {@link SemanticAnnotationDAO} inside {@link #render(RenderRequest)},
 * matching the {@code UrScriptTrajectoryTransformExecutor} pattern. Tests call the
 * package-private {@link #renderWithDao(RenderRequest, SemanticAnnotationDAO)} method
 * directly, bypassing CDI.
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/16} — MFFD-RENDER-MATERIAL-BATCH-TRACE slice 2 row</li>
 *   <li>{@link MffdMaterialBatchKind} — shape constants and slice 1 (view-shape seeding)</li>
 *   <li>{@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}</li>
 * </ul>
 *
 * @see ViewRecipeRenderer
 * @see MffdMaterialBatchKind
 */
public final class MaterialBatchTraceViewRenderer implements ViewRecipeRenderer {

  private static final String STATUS_OK = "OK";
  private static final String STATUS_MISSING = "MISSING";

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(MffdMaterialBatchKind.TRACE_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "MaterialBatchTraceViewRenderer";
  }

  @Override
  public RenderResponse render(RenderRequest req) {
    SemanticAnnotationDAO dao;
    try {
      dao = CDI.current().select(SemanticAnnotationDAO.class).get();
    } catch (RuntimeException ex) {
      throw new RenderException(
        "render.internal-error",
        "MaterialBatchTraceViewRenderer could not resolve SemanticAnnotationDAO via CDI: "
          + ex.getMessage(),
        ex
      );
    }
    return renderWithDao(req, dao);
  }

  /**
   * Package-private render entry point that accepts an injected DAO — used
   * directly by unit tests to bypass the CDI container lookup.
   *
   * @param req the dispatch request; must not be null
   * @param dao the DAO to query for material-batch annotations
   * @return the projected render response
   * @throws RenderException when {@code focusShepardId} is null/blank
   */
  RenderResponse renderWithDao(RenderRequest req, SemanticAnnotationDAO dao) {
    String batchAppId = req.focusShepardId();
    if (batchAppId == null || batchAppId.isBlank()) {
      throw new RenderException(
        "render.body.invalid",
        "MaterialBatchTraceViewRenderer requires focusShepardId (the material-batch DataObject appId)"
      );
    }

    List<SemanticAnnotation> annotations = dao.findByPredicateAndValue(
      MffdMaterialBatchKind.PRED_MATERIAL_BATCH,
      batchAppId,
      "DataObject",
      0,
      500
    );

    List<ChannelBindingProjection> bindings = new ArrayList<>();
    for (SemanticAnnotation annotation : annotations) {
      String subjectAppId = annotation.getSubjectAppId();
      if (subjectAppId == null) continue;
      bindings.add(new ChannelBindingProjection(
        MffdMaterialBatchKind.TRACE_ROLE_BATCH,
        MffdMaterialBatchKind.PRED_MATERIAL_BATCH,
        null,
        true,
        STATUS_OK,
        new ResolvedChannel(subjectAppId)
      ));
    }

    if (bindings.isEmpty()) {
      // No consuming DataObjects found — surface a MISSING status so the
      // caller knows the batch has no recorded consumers yet.
      bindings.add(new ChannelBindingProjection(
        MffdMaterialBatchKind.TRACE_ROLE_BATCH,
        MffdMaterialBatchKind.PRED_MATERIAL_BATCH,
        null,
        true,
        STATUS_MISSING,
        null
      ));
    }

    return new RenderResponse(
      req.templateAppId(),
      batchAppId,
      MffdMaterialBatchKind.TRACE_RENDERER,
      bindings
    );
  }
}
