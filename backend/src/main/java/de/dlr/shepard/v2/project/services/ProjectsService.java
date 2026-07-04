package de.dlr.shepard.v2.project.services;

import de.dlr.shepard.v2.project.daos.ProjectsDAO;
import de.dlr.shepard.v2.project.io.ProjectByAnnotationIO;
import de.dlr.shepard.v2.project.io.ProjectByAnnotationItemIO;
import de.dlr.shepard.v2.project.io.ProjectIO;
import de.dlr.shepard.v2.project.io.SubCollectionItemIO;
import de.dlr.shepard.v2.project.io.SubCollectionsIO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * PROJ-REST-1 + PROJ-REST-2 — application service that fronts {@link ProjectsDAO}.
 *
 * <p>The thin shape is intentional: the DAO does the OGM session work, this
 * service composes the wire envelopes and applies the Project-existence gate
 * (any caller asking for a non-Project Collection appId gets a uniform null,
 * which the REST layer translates to 404).
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3}.
 */
@ApplicationScoped
public class ProjectsService {

  @Inject
  ProjectsDAO projectsDAO;

  /** Returns true when the Collection at {@code appId} is a Project. */
  public boolean isProject(String collectionAppId) {
    return projectsDAO.isProject(collectionAppId);
  }

  /** Returns true when a (non-deleted) Collection exists at {@code appId}. */
  public boolean collectionExists(String collectionAppId) {
    return projectsDAO.collectionExists(collectionAppId);
  }

  /**
   * Get the Project envelope for {@code projectAppId}, or null when the appId
   * does not resolve to a Project (caller maps null → 404).
   */
  public ProjectIO getProject(String projectAppId) {
    return projectsDAO.findProject(projectAppId);
  }

  /**
   * Sub-Collection list for a Project. Returns null when the appId is not a
   * Project (caller maps null → 404); returns an empty
   * {@link SubCollectionsIO#getSubCollections()} when the Project has no
   * children.
   */
  public SubCollectionsIO getSubCollections(String projectAppId) {
    if (!projectsDAO.isProject(projectAppId)) return null;
    SubCollectionsIO io = new SubCollectionsIO();
    io.setProjectAppId(projectAppId);
    io.setProgrammes(projectsDAO.findProgrammes(projectAppId));
    List<SubCollectionItemIO> children = projectsDAO.findSubCollections(projectAppId);
    io.setSubCollections(children);
    return io;
  }

  /**
   * By-annotation roll-up across the Project's sub-Collections.
   * Returns null when the appId is not a Project (caller maps null → 404).
   */
  public ProjectByAnnotationIO queryByAnnotation(
      String projectAppId,
      String predicate,
      String value,
      boolean includeAnnotations,
      int page,
      int pageSize) {
    if (!projectsDAO.isProject(projectAppId)) return null;

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(1, Math.min(pageSize, 500));

    ProjectByAnnotationIO io = new ProjectByAnnotationIO();
    io.setProjectAppId(projectAppId);
    io.setPredicate(predicate);
    io.setValue(value);
    io.setPage(safePage);
    io.setPageSize(safeSize);
    io.setTotalCount(projectsDAO.countByAnnotation(projectAppId, predicate, value));
    List<ProjectByAnnotationItemIO> page1 = projectsDAO.pageByAnnotation(
      projectAppId, predicate, value, includeAnnotations, safePage, safeSize);
    io.setResults(page1);
    return io;
  }

  /** List of every Collection appId that carries urn:shepard:project = "true". */
  public List<String> listProjectAppIds() {
    return projectsDAO.findAllProjectAppIds();
  }
}
