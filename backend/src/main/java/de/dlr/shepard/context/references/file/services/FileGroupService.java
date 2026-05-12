package de.dlr.shepard.context.references.file.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.FileGroupDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.CreateFileGroupIO;
import de.dlr.shepard.data.file.entities.ShepardFile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD for {@link FileGroup} sub-nodes attached to a
 * {@link FileBundleReference} (FR1a, see {@code aidocs/53 §1.4}).
 *
 * <p>Permissions are enforced at the REST layer
 * ({@code FileBundleReferenceRest}) against the parent DataObject —
 * this service trusts its callers.
 */
@RequestScoped
public class FileGroupService {

  @Inject
  FileGroupDAO fileGroupDAO;

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  /**
   * @param bundleAppId parent bundle's appId.
   * @return all groups under the bundle, ordered by ascending {@code index}.
   */
  public List<FileGroup> listGroups(String bundleAppId) {
    return fileGroupDAO.findByBundleAppId(bundleAppId);
  }

  /**
   * @param appId the group's own appId.
   * @return the group, or {@code null} when no match.
   */
  public FileGroup getByAppId(String appId) {
    return fileGroupDAO.findByAppId(appId);
  }

  /**
   * Create a new {@link FileGroup} under the given bundle. The group's
   * appId is auto-minted by {@link de.dlr.shepard.common.neo4j.daos.GenericDAO};
   * its {@code index} is auto-assigned as {@code max(existing) + 1}
   * unless the caller supplied one explicitly.
   *
   * @param bundleAppId the parent bundle's appId.
   * @param request the create request body (name + optional fields).
   * @return the persisted group.
   * @throws NotFoundException when no bundle with that appId exists.
   * @throws BadRequestException when {@code name} is null or blank.
   */
  public FileGroup createGroup(String bundleAppId, CreateFileGroupIO request) {
    if (request == null || request.getName() == null || request.getName().isBlank()) {
      throw new BadRequestException("FileGroup name must not be null or blank");
    }
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) {
      throw new NotFoundException("No FileBundleReference with appId " + bundleAppId);
    }

    User user = userService.getCurrentUser();

    FileGroup group = new FileGroup();
    group.setName(request.getName());
    group.setDescription(request.getDescription());
    int targetIndex = request.getIndex() != null
      ? request.getIndex()
      : (fileGroupDAO.findMaxIndexUnderBundle(bundleAppId) + 1);
    group.setIndex(targetIndex);
    group.setStartedAt(request.getStartedAt());
    group.setEndedAt(request.getEndedAt());
    group.setAttributes(request.getAttributes() != null ? new HashMap<>(request.getAttributes()) : new HashMap<>());
    group.setCreatedAt(dateHelper.getDate());
    group.setCreatedBy(user);

    // Persist the group first so it has an id + appId.
    FileGroup saved = fileGroupDAO.createOrUpdate(group);

    // Attach to the bundle.
    bundle.addGroup(saved);
    fileBundleReferenceDAO.createOrUpdate(bundle);

    Log.debugf("FR1a: created FileGroup appId=%s under bundle appId=%s at index=%d",
      saved.getAppId(), bundleAppId, targetIndex);
    return saved;
  }

  /**
   * Apply an RFC 7396 merge-patch to a {@link FileGroup}. Patchable
   * fields: {@code name}, {@code description}, {@code attributes},
   * {@code startedAt}, {@code endedAt}, {@code index}.
   *
   * @param appId the group's appId.
   * @param patch a {@code Map} representing the merge-patch body.
   *   Keys absent from the map preserve the existing value; keys
   *   present with {@code null} value clear the field (except
   *   {@code name}, which must remain non-blank).
   * @return the patched group.
   * @throws NotFoundException when no group with that appId exists.
   * @throws BadRequestException when {@code name} would become null/blank.
   */
  public FileGroup patchGroup(String appId, Map<String, Object> patch) {
    FileGroup group = fileGroupDAO.findByAppId(appId);
    if (group == null) {
      throw new NotFoundException("No FileGroup with appId " + appId);
    }
    if (patch == null) {
      throw new BadRequestException("PATCH body must be a JSON object");
    }

    if (patch.containsKey("name")) {
      Object v = patch.get("name");
      if (v == null || (v instanceof String s && s.isBlank())) {
        throw new BadRequestException("name must not be null or blank");
      }
      group.setName(v.toString());
    }
    if (patch.containsKey("description")) {
      Object v = patch.get("description");
      group.setDescription(v == null ? null : v.toString());
    }
    if (patch.containsKey("index")) {
      Object v = patch.get("index");
      group.setIndex(v == null ? null : ((Number) v).intValue());
    }
    if (patch.containsKey("startedAt")) {
      Object v = patch.get("startedAt");
      group.setStartedAt(v == null ? null : parseDate(v));
    }
    if (patch.containsKey("endedAt")) {
      Object v = patch.get("endedAt");
      group.setEndedAt(v == null ? null : parseDate(v));
    }
    if (patch.containsKey("attributes")) {
      Object v = patch.get("attributes");
      if (v == null) {
        group.setAttributes(new HashMap<>());
      } else if (v instanceof Map<?, ?> m) {
        Map<String, String> attrs = new HashMap<>();
        for (var e : m.entrySet()) {
          if (e.getValue() != null) attrs.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        group.setAttributes(attrs);
      }
    }

    group.setUpdatedAt(dateHelper.getDate());
    group.setUpdatedBy(userService.getCurrentUser());
    return fileGroupDAO.createOrUpdate(group);
  }

  /**
   * Delete a group.
   *
   * @param appId the group's appId.
   * @param force when {@code true}, deletes the group even if it
   *   contains files (the files themselves are removed as well).
   *   When {@code false}, refuses with {@link BadRequestException}
   *   if the group contains files.
   * @throws NotFoundException when no group with that appId exists.
   * @throws BadRequestException when the group has files and
   *   {@code force=false}, or when the group is the last remaining
   *   group of its bundle (would orphan all files).
   */
  public void deleteGroup(String appId, boolean force) {
    FileGroup group = fileGroupDAO.findByAppId(appId);
    if (group == null) {
      throw new NotFoundException("No FileGroup with appId " + appId);
    }

    String bundleAppId = findBundleAppIdForGroup(appId);
    if (bundleAppId != null) {
      List<FileGroup> siblings = fileGroupDAO.findByBundleAppId(bundleAppId);
      if (siblings.size() <= 1) {
        throw new BadRequestException(
          "Refusing to delete the last remaining FileGroup of FileBundleReference appId=" +
          bundleAppId +
          " (would orphan its files)."
        );
      }
    }

    if (group.getFiles() != null && !group.getFiles().isEmpty() && !force) {
      throw new BadRequestException(
        "FileGroup appId=" +
        appId +
        " has " +
        group.getFiles().size() +
        " file(s); pass ?force=true to delete the group and its files."
      );
    }

    // Mark deleted (soft delete — matches the rest of the codebase).
    group.setDeleted(true);
    group.setUpdatedAt(dateHelper.getDate());
    group.setUpdatedBy(userService.getCurrentUser());
    fileGroupDAO.createOrUpdate(group);
  }

  /**
   * Resolve the parent bundle's appId for a given group. Returns
   * {@code null} when the group has no parent (which would be a graph
   * inconsistency — every group is attached to exactly one bundle by
   * construction).
   */
  public String findBundleAppIdForGroup(String groupAppId) {
    return fileGroupDAO.findBundleAppIdForGroup(groupAppId);
  }

  /**
   * Attach a freshly-uploaded {@link ShepardFile} to a group AND to
   * the parent bundle (the bundle keeps a compatibility-shadow
   * {@code HAS_PAYLOAD} edge to every file so the upstream API's flat
   * read still works).
   *
   * @param groupAppId the group's appId.
   * @param file the file (already persisted in Mongo / GridFS).
   * @return the updated group.
   */
  public FileGroup attachFile(String groupAppId, ShepardFile file) {
    FileGroup group = fileGroupDAO.findByAppId(groupAppId);
    if (group == null) {
      throw new NotFoundException("No FileGroup with appId " + groupAppId);
    }
    String bundleAppId = findBundleAppIdForGroup(groupAppId);
    FileBundleReference bundle = bundleAppId != null ? fileBundleReferenceDAO.findByAppId(bundleAppId) : null;

    group.addFile(file);
    fileGroupDAO.createOrUpdate(group);

    if (bundle != null) {
      bundle.addFile(file);
      fileBundleReferenceDAO.createOrUpdate(bundle);
    }
    return group;
  }

  private static java.util.Date parseDate(Object v) {
    if (v instanceof java.util.Date d) return d;
    if (v instanceof Number n) return new java.util.Date(n.longValue());
    if (v instanceof String s) {
      try {
        return java.util.Date.from(java.time.Instant.parse(s));
      } catch (Exception ignored) {
        // fall through to BadRequest
      }
    }
    throw new BadRequestException("Cannot parse date value: " + v);
  }
}
