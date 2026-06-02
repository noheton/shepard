package de.dlr.shepard.v2.project.services;

import de.dlr.shepard.v2.project.daos.ProjectsDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * PROJ-SEMA-WRITE-GATE-1 — runtime executable mirror of the SHACL constraints
 * declared in {@code backend/src/main/resources/shapes/project-shape.ttl}.
 *
 * <p>Called from {@code SemanticAnnotationV2Rest.create()} BEFORE the
 * annotation is persisted. Returns a non-null violation message when the write
 * would violate one of the §2 constraints from
 * {@code aidocs/integrations/121-project-and-subcollections.md}; the REST
 * layer translates the violation to a 422 RFC 7807 response.
 *
 * <p>Cheap path: only the three Project predicates trigger any work; every
 * other predicate falls through with {@code null} in O(1).
 */
@ApplicationScoped
public class ProjectAnnotationConstraints {

  public static final String PRED_PROJECT   = "urn:shepard:project";
  public static final String PRED_PART_OF   = "urn:shepard:partOf";
  public static final String PRED_PROGRAMME = "urn:shepard:programme";

  @Inject
  ProjectsDAO projectsDAO;

  /**
   * Check the SHACL constraints for a Project-related annotation write.
   *
   * @param subjectAppId  appId of the subject Collection (or other entity)
   * @param subjectKind   subjectKind label ("Collection", "DataObject", …)
   * @param predicateIri  the predicate IRI being written
   * @param objectLiteral the literal value (XOR with objectIri)
   * @param objectIri     the IRI value (XOR with objectLiteral)
   * @return null on success; a human-readable violation message on failure
   */
  public String check(
      String subjectAppId,
      String subjectKind,
      String predicateIri,
      String objectLiteral,
      String objectIri) {
    if (predicateIri == null) return null;
    switch (predicateIri) {
      case PRED_PROJECT   -> { return checkProject(objectLiteral, objectIri, subjectKind); }
      case PRED_PART_OF   -> { return checkPartOf(subjectAppId, subjectKind, objectLiteral, objectIri); }
      case PRED_PROGRAMME -> { return checkProgramme(subjectAppId, subjectKind, objectLiteral, objectIri); }
      default             -> { return null; }
    }
  }

  // ─── per-predicate gates ─────────────────────────────────────────────────

  /** urn:shepard:project — only on Collections, only with literal "true". */
  private String checkProject(String objectLiteral, String objectIri, String subjectKind) {
    if (subjectKind != null && !subjectKind.isBlank()
        && !"Collection".equalsIgnoreCase(subjectKind)) {
      return "urn:shepard:project can only be set on a Collection";
    }
    if (objectIri != null && !objectIri.isBlank()) {
      return "urn:shepard:project value must be the literal string \"true\" — not an IRI";
    }
    if (objectLiteral == null || !"true".equals(objectLiteral.trim())) {
      return "urn:shepard:project value must be the literal string \"true\"";
    }
    return null;
  }

  /**
   * urn:shepard:partOf — only on Collections; value is a literal Collection
   * appId; the target Collection must itself be a Project; partOf cannot
   * self-reference.
   */
  private String checkPartOf(String subjectAppId, String subjectKind,
      String objectLiteral, String objectIri) {
    if (subjectKind != null && !subjectKind.isBlank()
        && !"Collection".equalsIgnoreCase(subjectKind)) {
      return "urn:shepard:partOf can only be set on a Collection";
    }
    if (objectIri != null && !objectIri.isBlank()) {
      return "urn:shepard:partOf value must be the parent Collection appId as a literal string — not an IRI";
    }
    if (objectLiteral == null || objectLiteral.isBlank()) {
      return "urn:shepard:partOf value must be the parent Collection appId";
    }
    String parentAppId = objectLiteral.trim();
    if (parentAppId.equals(subjectAppId)) {
      return "urn:shepard:partOf cannot point at itself";
    }
    if (!projectsDAO.isProject(parentAppId)) {
      return "urn:shepard:partOf target '" + parentAppId
        + "' is not a Project (does not carry urn:shepard:project = \"true\")";
    }
    return null;
  }

  /** urn:shepard:programme — only on Collections that ARE Projects. */
  private String checkProgramme(String subjectAppId, String subjectKind,
      String objectLiteral, String objectIri) {
    if (subjectKind != null && !subjectKind.isBlank()
        && !"Collection".equalsIgnoreCase(subjectKind)) {
      return "urn:shepard:programme can only be set on a Collection";
    }
    if (objectIri != null && !objectIri.isBlank()) {
      return "urn:shepard:programme value must be a free-text literal — not an IRI";
    }
    if (objectLiteral == null || objectLiteral.isBlank()) {
      return "urn:shepard:programme value must be a non-blank programme name";
    }
    if (!projectsDAO.isProject(subjectAppId)) {
      return "urn:shepard:programme is only valid on a Project Collection "
        + "(set urn:shepard:project = \"true\" first)";
    }
    return null;
  }
}
