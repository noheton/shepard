package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.OntologyAlignment;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TPL3a-lite — read-only DAO for {@link OntologyAlignment} nodes.
 *
 * <p>The registry is seeded by
 * {@code V67__TPL3_upper_ontology_alignment.cypher} and is read-only at
 * runtime; there is no service-layer write path. The single operation
 * exposed here ({@link #findAll()}) is used by
 * {@link de.dlr.shepard.v2.semantic.resources.OntologyAlignmentRest}.
 */
@RequestScoped
public class OntologyAlignmentDAO extends GenericDAO<OntologyAlignment> {

  /**
   * Return all alignment rows in the registry.  The set is small (≤ ~50
   * rows) so no pagination is applied.  Returns an empty list when no
   * nodes exist (fresh database before V67 has run).
   */
  public List<OntologyAlignment> findAll() {
    Collection<OntologyAlignment> result = session.loadAll(OntologyAlignment.class, DEPTH_ENTITY);
    if (result == null) return List.of();
    return new ArrayList<>(result);
  }

  @Override
  public Class<OntologyAlignment> getEntityType() {
    return OntologyAlignment.class;
  }
}
