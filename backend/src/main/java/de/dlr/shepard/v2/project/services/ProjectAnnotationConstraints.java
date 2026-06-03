package de.dlr.shepard.v2.project.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.project.daos.ProjectsDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * PROJ-SEMA-WRITE-GATE-1 — runtime executable mirror of the SHACL constraints
 * declared in {@code backend/src/main/resources/shapes/project-shape.ttl}.
 *
 * <p>Called from {@code SemanticAnnotationV2Rest.create()} BEFORE the
 * annotation is persisted. {@link #check} returns a non-null violation
 * message when the write would violate one of the §2 constraints from
 * {@code aidocs/integrations/121-project-and-subcollections.md}; the REST
 * layer translates the violation to a 422 RFC 7807 response.
 *
 * <p>Cheap path: only the three Project predicates trigger any work; every
 * other predicate falls through with {@code null} in O(1).
 *
 * <h2>PROJ-SEMA-DUAL-OWNERSHIP-1 — parent-Write gate (2026-06-03)</h2>
 *
 * <p>{@link #checkParentWritePermission} additionally enforces the §2
 * "Owner-write only" rule for {@code urn:shepard:partOf} writes:
 * mutating a partOf annotation requires the {@code instance-admin} role OR
 * Write permission on BOTH the parent Project (target of partOf) and the
 * child Collection (subject — already gated by
 * {@code SemanticAnnotationV2Rest.checkWriteAccessForSubject}). This method
 * adds the parent-side check. Returns a non-null deny message that the REST
 * layer translates to a 403 response.
 *
 * <p>{@code urn:shepard:project} and {@code urn:shepard:programme} only act
 * on the subject Collection (which is the Project itself); they have no
 * separate "parent" target, so the subject-Write gate already in the REST
 * layer is sufficient.
 */
@ApplicationScoped
public class ProjectAnnotationConstraints {

  public static final String PRED_PROJECT   = "urn:shepard:project";
  public static final String PRED_PART_OF   = "urn:shepard:partOf";
  public static final String PRED_PROGRAMME = "urn:shepard:programme";

  @Inject
  ProjectsDAO projectsDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

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

  /**
   * PROJ-SEMA-DUAL-OWNERSHIP-1 — verify the caller can Write the partOf
   * parent Collection.
   *
   * <p>Only the {@code urn:shepard:partOf} predicate has a distinct
   * "parent" target; the other two predicates either declare the subject
   * itself as a Project ({@code urn:shepard:project}) or describe Project
   * metadata on the subject ({@code urn:shepard:programme}), so this
   * method is a no-op for them.
   *
   * <p>Instance-admins bypass the check (per the §2 "instance-admin role
   * OR ownership of both" rule).
   *
   * <p>The subject-side Write is presumed to already have been checked by
   * the REST layer via {@code checkWriteAccessForSubject} before this
   * method is invoked.
   *
   * @param predicateIri    the predicate being written
   * @param objectLiteral   the literal value (for partOf this is the parent
   *                        Collection appId)
   * @param caller          the caller's username
   * @param isInstanceAdmin whether the caller holds the
   *                        {@code instance-admin} role
   * @return null on success; a human-readable deny message on failure
   */
  public String checkParentWritePermission(
      String predicateIri,
      String objectLiteral,
      String caller,
      boolean isInstanceAdmin) {
    // Only partOf has a separate parent target to gate.
    if (!PRED_PART_OF.equals(predicateIri)) return null;
    // Instance-admin bypass.
    if (isInstanceAdmin) return null;
    // Missing target — let {@link #check} surface the SHACL violation.
    if (objectLiteral == null || objectLiteral.isBlank()) return null;
    String parentAppId = objectLiteral.trim();
    long parentOgmId;
    try {
      parentOgmId = entityIdResolver.resolveLong(parentAppId);
    } catch (NotFoundException nfe) {
      // The SHACL gate's isProject() check has already rejected unknown
      // targets — but if we reach here through some race, deny rather
      // than 500.
      return "Caller '" + caller + "' cannot Write the parent Project '"
        + parentAppId + "' (target not resolvable).";
    }
    boolean allowed = permissionsService.isAccessTypeAllowedForUser(
      parentOgmId, AccessType.Write, caller, 0L);
    if (!allowed) {
      return "Caller '" + caller + "' lacks Write permission on the parent "
        + "Project '" + parentAppId + "' — urn:shepard:partOf requires Write "
        + "on BOTH the parent Project and the child Collection "
        + "(PROJ-SEMA-DUAL-OWNERSHIP-1).";
    }
    return null;
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
