package de.dlr.shepard.versioning;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ENT1a service — orchestrates create / list / get / patch-ACL /
 * delete for {@link EntityVersion} rows per {@code aidocs/16} ENT1a.
 *
 * <p>Permission gates live here (not in the REST layer) because the
 * gates are per-version ACL — the REST layer hands off the caller's
 * subject and lets the service walk the version's {@link Permissions}
 * graph to decide.
 *
 * <p>Label semantics (the "server suggests / user can override" rule
 * from the ENT1a scope decisions):
 * <ul>
 *   <li>Blank/null requested label → server suggests {@code "v" + newOrdinal}.</li>
 *   <li>User-supplied label → must match {@link #LABEL_PATTERN}
 *       (alphanumerics + dot + hyphen; ≤ 64 chars; not purely numeric).</li>
 *   <li>Collisions with the parent's existing labels surface as
 *       {@link EntityVersionException.Reason#LABEL_DUPLICATE} →
 *       REST 409 {@code versions.label.duplicate}.</li>
 * </ul>
 *
 * <p>ACL inheritance: a new version's {@link Permissions} is a deep
 * clone of the previous version's ACL (one ACL per version per ENT1a
 * §"Scope decisions"). When no previous version exists (shouldn't
 * happen post-V35 backfill but the path is defensive), a fresh
 * Private-typed Permissions row is created with the caller as owner.
 */
@RequestScoped
public class EntityVersionService {

  /**
   * Allowed shape for user-supplied version labels. Matches the
   * common shapes — server-suggested {@code v1} / {@code v23};
   * SemVer-like {@code 1.0.0}, {@code 1.0.0-rc.1}, {@code 2.4.1-alpha+exp.sha.5114f85};
   * release names {@code release-march-2026}. Rejects purely numeric
   * labels (avoids confusion with the {@code versionOrdinal} scalar).
   */
  static final Pattern LABEL_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.\\-+]{0,63}$");

  /** Cap on free-form notes; service rejects longer with LABEL_INVALID. */
  static final int MAX_NOTE_LENGTH = 2048;

  /** Supported parent kinds (singular form, matching {@link EntityVersion#getParentEntityKind()}). */
  static final Set<String> SUPPORTED_KINDS = Set.of("collection", "data-object");

  /**
   * URL-segment → singular-kind mapping for the REST surface
   * ({@code data-objects} → {@code data-object}; {@code collections}
   * → {@code collection}).
   */
  static String resolveKindFromSegment(String urlSegment) {
    if (urlSegment == null) return null;
    return switch (urlSegment) {
      case "data-objects" -> "data-object";
      case "collections" -> "collection";
      default -> null;
    };
  }

  @Inject
  EntityVersionDAO versionDAO;

  @Inject
  PermissionsDAO permissionsDAO;

  @Inject
  UserService userService;

  @Inject
  UserGroupService userGroupService;

  @Inject
  Event<VersionCreatedEvent> versionCreatedEvent;

  /**
   * Mint a new {@link EntityVersion} on the given parent. Auth is
   * delegated to the REST layer (Writer / Manager on the parent;
   * not on a version because none exists yet for the new ordinal).
   *
   * @param kind             singular parent kind ({@code "collection"}
   *                         or {@code "data-object"})
   * @param parentAppId      parent's appId
   * @param requestedLabel   user-supplied label, or blank/null to
   *                         accept the server suggestion
   * @param note             optional release-note (≤ 2 KB)
   * @param createdBySub     creator's subject identifier (PROV1
   *                         already captures the actor; this is the
   *                         denormalised fast-path)
   * @return the freshly-created (and committed) {@link EntityVersion}
   * @throws EntityVersionException with {@link EntityVersionException.Reason#LABEL_INVALID}
   *         when label / note shape is unacceptable; with
   *         {@link EntityVersionException.Reason#LABEL_DUPLICATE} when
   *         the user-supplied label collides with an existing version;
   *         with {@link EntityVersionException.Reason#KIND_UNSUPPORTED}
   *         when the kind isn't one of {@link #SUPPORTED_KINDS}.
   */
  public EntityVersion createVersion(
    String kind,
    String parentAppId,
    String requestedLabel,
    String note,
    String createdBySub
  ) {
    if (!SUPPORTED_KINDS.contains(kind)) {
      throw new EntityVersionException(
        EntityVersionException.Reason.KIND_UNSUPPORTED,
        "Unsupported parent kind '" + kind + "'. Supported: " + String.join(", ", SUPPORTED_KINDS) + "."
      );
    }
    if (parentAppId == null || parentAppId.isBlank()) {
      throw new IllegalArgumentException("parentAppId must not be null/blank");
    }
    if (note != null && note.length() > MAX_NOTE_LENGTH) {
      throw new EntityVersionException(
        EntityVersionException.Reason.LABEL_INVALID,
        "note exceeds maximum length of " + MAX_NOTE_LENGTH + " characters"
      );
    }

    int newOrdinal = versionDAO.findMaxOrdinalByParent(parentAppId) + 1;

    String label;
    if (requestedLabel == null || requestedLabel.isBlank()) {
      label = "v" + newOrdinal;
    } else {
      validateLabelShape(requestedLabel);
      if (versionDAO.existsLabelForParent(parentAppId, requestedLabel)) {
        throw new EntityVersionException(
          EntityVersionException.Reason.LABEL_DUPLICATE,
          "A version with label '" + requestedLabel + "' already exists on parent '" + parentAppId + "'."
        );
      }
      label = requestedLabel;
    }

    // Inherit ACL from the previous version when one exists; otherwise
    // mint a fresh Private-typed ACL with the caller as owner.
    Optional<EntityVersion> previous = versionDAO.findLatestByParent(parentAppId);
    Permissions previousAcl = previous.map(EntityVersion::getPermissions).orElse(null);
    Permissions newAcl = clonePermissions(previousAcl, createdBySub);
    Permissions savedAcl = permissionsDAO.createOrUpdate(newAcl);

    EntityVersion v = new EntityVersion();
    v.setVersionLabel(label);
    v.setVersionOrdinal(newOrdinal);
    v.setCreatedAt(System.currentTimeMillis());
    v.setCreatedBy(createdBySub);
    v.setParentEntityKind(kind);
    v.setParentEntityAppId(parentAppId);
    v.setNote(note);
    v.setPermissions(savedAcl);
    EntityVersion saved = versionDAO.save(v);

    versionDAO.attachToParent(saved, parentAppId);

    Log.infof(
      "ENT1a: created version label='%s' ordinal=%d on parent kind='%s' appId='%s' by='%s'",
      saved.getVersionLabel(),
      saved.getVersionOrdinal(),
      saved.getParentEntityKind(),
      saved.getParentEntityAppId(),
      saved.getCreatedBy()
    );
    fireCreatedEvent(saved);
    return saved;
  }

  /**
   * List every version of a parent visible to the caller (filtered
   * by per-version ACL Reader-or-stronger). Returns newest-first
   * (highest ordinal first).
   */
  public List<EntityVersion> listVersions(String kind, String parentAppId, String callerSub) {
    if (!SUPPORTED_KINDS.contains(kind)) {
      throw new EntityVersionException(
        EntityVersionException.Reason.KIND_UNSUPPORTED,
        "Unsupported parent kind '" + kind + "'."
      );
    }
    List<EntityVersion> all = versionDAO.findAllByParent(parentAppId);
    List<EntityVersion> visible = new ArrayList<>(all.size());
    for (EntityVersion v : all) {
      if (canRead(v, callerSub)) visible.add(v);
    }
    return visible;
  }

  /**
   * Fetch a single version by label. 404 when missing; 403 when the
   * caller lacks Read on the version's ACL.
   */
  public EntityVersion getVersion(String kind, String parentAppId, String label, String callerSub) {
    EntityVersion v = requireVersion(kind, parentAppId, label);
    if (!canRead(v, callerSub)) {
      throw new EntityVersionException(
        EntityVersionException.Reason.FORBIDDEN,
        "Caller lacks Read permission on this version."
      );
    }
    return v;
  }

  /**
   * Apply a {@link PermissionsIO} patch to a version's ACL. The
   * caller must be Manager on the version's ACL; ownership transfer
   * (when the patch carries a different owner) requires Owner.
   *
   * <p>Mirrors the shape of
   * {@code PermissionsService#updatePermissionsByNeo4jId} on the
   * legacy permissions surface so the wire shape stays familiar; the
   * structural difference is the row this patch lands on
   * (a per-version {@link Permissions} node, not the parent entity's).
   */
  public Permissions patchVersionPermissions(
    String kind,
    String parentAppId,
    String label,
    PermissionsIO patch,
    String callerSub
  ) {
    EntityVersion v = requireVersion(kind, parentAppId, label);
    if (!canManage(v, callerSub)) {
      throw new EntityVersionException(
        EntityVersionException.Reason.FORBIDDEN,
        "Caller lacks Manage permission on this version."
      );
    }
    Permissions current = v.getPermissions();
    if (current == null) {
      // Defensive: a version without an ACL shouldn't exist post-V35.
      // Mint a fresh one rather than fail.
      current = clonePermissions(null, callerSub);
      Permissions saved = permissionsDAO.createOrUpdate(current);
      versionDAO.setPermissions(v, saved);
      v.setPermissions(saved);
      current = saved;
    }

    if (patch == null) return current;

    // Owner transfer is gated on Owner rather than Manager.
    if (
      patch.getOwner() != null &&
      (current.getOwner() == null || !patch.getOwner().equals(current.getOwner().getUsername()))
    ) {
      if (current.getOwner() == null || !callerSub.equals(current.getOwner().getUsername())) {
        throw new EntityVersionException(
          EntityVersionException.Reason.FORBIDDEN,
          "Only the current owner can transfer ownership of a version's ACL."
        );
      }
      User newOwner = userService.getUserOptional(patch.getOwner()).orElse(null);
      if (newOwner != null) current.setOwner(newOwner);
    }

    current.setPermissionType(patch.getPermissionType() == null ? current.getPermissionType() : patch.getPermissionType());
    current.setReader(fetchUsers(patch.getReader()));
    current.setWriter(fetchUsers(patch.getWriter()));
    current.setManager(fetchUsers(patch.getManager()));
    current.setReaderGroups(fetchUserGroups(patch.getReaderGroupIds()));
    current.setWriterGroups(fetchUserGroups(patch.getWriterGroupIds()));

    Permissions saved = permissionsDAO.createOrUpdate(current);
    v.setPermissions(saved);
    Log.infof(
      "ENT1a: patched ACL of version='%s' on parent='%s' by='%s'",
      v.getVersionLabel(),
      v.getParentEntityAppId(),
      callerSub
    );
    return saved;
  }

  /**
   * Delete a version. Refuses to remove the last remaining version
   * (returns {@link EntityVersionException.Reason#CANNOT_DELETE_ONLY}
   * → REST 409); caller must be Manager on the version's ACL.
   */
  public void deleteVersion(String kind, String parentAppId, String label, String callerSub) {
    EntityVersion v = requireVersion(kind, parentAppId, label);
    if (!canManage(v, callerSub)) {
      throw new EntityVersionException(
        EntityVersionException.Reason.FORBIDDEN,
        "Caller lacks Manage permission on this version."
      );
    }
    List<EntityVersion> all = versionDAO.findAllByParent(parentAppId);
    if (all.size() <= 1) {
      throw new EntityVersionException(
        EntityVersionException.Reason.CANNOT_DELETE_ONLY,
        "Cannot delete the only remaining version of parent '" + parentAppId + "'. " +
        "Create another version first or delete the parent entity itself."
      );
    }
    versionDAO.delete(v);
    Log.infof(
      "ENT1a: deleted version='%s' (appId=%s) on parent='%s' by='%s'",
      v.getVersionLabel(),
      v.getAppId(),
      v.getParentEntityAppId(),
      callerSub
    );
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private EntityVersion requireVersion(String kind, String parentAppId, String label) {
    if (!SUPPORTED_KINDS.contains(kind)) {
      throw new EntityVersionException(
        EntityVersionException.Reason.KIND_UNSUPPORTED,
        "Unsupported parent kind '" + kind + "'."
      );
    }
    return versionDAO
      .findByParentAndLabel(parentAppId, label)
      .filter(v -> kind.equals(v.getParentEntityKind()))
      .orElseThrow(() ->
        new EntityVersionException(
          EntityVersionException.Reason.NOT_FOUND,
          "No version with label '" + label + "' on parent '" + parentAppId + "' of kind '" + kind + "'."
        )
      );
  }

  static void validateLabelShape(String label) {
    if (label == null || label.isBlank()) {
      throw new EntityVersionException(EntityVersionException.Reason.LABEL_INVALID, "label must not be blank");
    }
    String trimmed = label.trim();
    if (trimmed.length() > 64) {
      throw new EntityVersionException(
        EntityVersionException.Reason.LABEL_INVALID,
        "label exceeds maximum length of 64 characters"
      );
    }
    if (trimmed.matches("^[0-9]+$")) {
      throw new EntityVersionException(
        EntityVersionException.Reason.LABEL_INVALID,
        "purely-numeric labels are reserved for ordinals; please prefix with 'v' or use a SemVer-like shape"
      );
    }
    if (!LABEL_PATTERN.matcher(trimmed).matches()) {
      throw new EntityVersionException(
        EntityVersionException.Reason.LABEL_INVALID,
        "label '" + label + "' contains illegal characters; allowed: [a-zA-Z0-9.\\-+] starting with alphanumeric"
      );
    }
  }

  /**
   * Deep-clone a {@link Permissions} graph for use as a fresh
   * per-version ACL. The clone shares User / UserGroup vertices (they
   * are global identity primitives) but creates a fresh
   * {@code :Permissions} node so subsequent mutations don't bleed
   * across versions.
   *
   * <p>When {@code previous} is {@code null} (no prior version), the
   * clone defaults to a Private-typed ACL with {@code createdBySub}
   * as Owner — defensive path for entities pre-dating V35 backfill.
   */
  Permissions clonePermissions(Permissions previous, String createdBySub) {
    Permissions fresh = new Permissions();
    if (previous != null) {
      fresh.setOwner(previous.getOwner());
      fresh.setPermissionType(previous.getPermissionType() == null ? PermissionType.Private : previous.getPermissionType());
      fresh.setReader(previous.getReader() == null ? new ArrayList<>() : new ArrayList<>(previous.getReader()));
      fresh.setWriter(previous.getWriter() == null ? new ArrayList<>() : new ArrayList<>(previous.getWriter()));
      fresh.setManager(previous.getManager() == null ? new ArrayList<>() : new ArrayList<>(previous.getManager()));
      fresh.setReaderGroups(
        previous.getReaderGroups() == null ? new ArrayList<>() : new ArrayList<>(previous.getReaderGroups())
      );
      fresh.setWriterGroups(
        previous.getWriterGroups() == null ? new ArrayList<>() : new ArrayList<>(previous.getWriterGroups())
      );
      return fresh;
    }
    fresh.setPermissionType(PermissionType.Private);
    fresh.setReader(new ArrayList<>());
    fresh.setWriter(new ArrayList<>());
    fresh.setManager(new ArrayList<>());
    fresh.setReaderGroups(new ArrayList<>());
    fresh.setWriterGroups(new ArrayList<>());
    if (createdBySub != null && !createdBySub.isBlank()) {
      User owner = userService.getUserOptional(createdBySub).orElse(null);
      if (owner != null) fresh.setOwner(owner);
    }
    return fresh;
  }

  boolean canRead(EntityVersion v, String callerSub) {
    return rolesGrantAccess(v.getPermissions(), callerSub, AccessType.Read);
  }

  boolean canManage(EntityVersion v, String callerSub) {
    return rolesGrantAccess(v.getPermissions(), callerSub, AccessType.Manage);
  }

  /**
   * Permission predicate that walks the {@link Permissions} graph
   * directly — we deliberately don't reuse {@code PermissionsService}
   * because that one resolves against the parent entity's ACL (legacy
   * shape). ENT1a's ACL lives on the version, not the parent.
   */
  boolean rolesGrantAccess(Permissions perms, String callerSub, AccessType accessType) {
    if (callerSub == null || callerSub.isBlank()) return false;
    if (perms == null) {
      // C3 fail-closed: no ACL → no rights.
      return false;
    }
    boolean isOwner = perms.getOwner() != null && callerSub.equals(perms.getOwner().getUsername());
    boolean isManager =
      perms.getManager() != null && perms.getManager().stream().anyMatch(u -> callerSub.equals(u.getUsername()));
    boolean isWriter =
      (perms.getWriter() != null && perms.getWriter().stream().anyMatch(u -> callerSub.equals(u.getUsername()))) ||
      groupMembersContain(perms.getWriterGroups(), callerSub) ||
      PermissionType.Public.equals(perms.getPermissionType());
    boolean isReader =
      (perms.getReader() != null && perms.getReader().stream().anyMatch(u -> callerSub.equals(u.getUsername()))) ||
      groupMembersContain(perms.getReaderGroups(), callerSub) ||
      PermissionType.Public.equals(perms.getPermissionType()) ||
      PermissionType.PublicReadable.equals(perms.getPermissionType());

    if (isOwner) return true;
    return switch (accessType) {
      case Read -> isReader || isWriter || isManager;
      case Write -> isWriter || isManager;
      case Manage -> isManager;
      case None -> false;
    };
  }

  private boolean groupMembersContain(List<UserGroup> groups, String callerSub) {
    if (groups == null || groups.isEmpty()) return false;
    Set<String> usernames = new HashSet<>();
    for (UserGroup g : groups) {
      Optional<UserGroup> full = userGroupService.getUserGroupOptional(g.getId());
      if (full.isPresent() && full.get().getUsers() != null) {
        for (User u : full.get().getUsers()) {
          if (u != null && u.getUsername() != null) usernames.add(u.getUsername());
        }
      }
    }
    return usernames.contains(callerSub);
  }

  private List<User> fetchUsers(String[] usernames) {
    if (usernames == null) return new ArrayList<>();
    List<User> out = new ArrayList<>(usernames.length);
    for (String u : usernames) {
      if (u == null) continue;
      userService.getUserOptional(u).ifPresent(out::add);
    }
    return out;
  }

  private List<UserGroup> fetchUserGroups(long[] ids) {
    if (ids == null) return new ArrayList<>();
    List<UserGroup> out = new ArrayList<>(ids.length);
    for (long id : ids) {
      userGroupService.getUserGroupOptional(id).ifPresent(out::add);
    }
    return out;
  }

  private void fireCreatedEvent(EntityVersion saved) {
    if (versionCreatedEvent == null) return;
    try {
      versionCreatedEvent.fire(
        new VersionCreatedEvent(
          saved.getParentEntityKind(),
          saved.getParentEntityAppId(),
          saved.getAppId(),
          saved.getVersionLabel(),
          saved.getVersionOrdinal(),
          saved.getCreatedBy()
        )
      );
    } catch (RuntimeException e) {
      // Best-effort. The shepard write is the source of truth; downstream
      // observers reconcile out-of-band. Mirrors the A5b contract.
      Log.warnf(
        e,
        "VersionCreatedEvent observer failed for version=%s on parent=%s; ignoring",
        saved.getAppId(),
        saved.getParentEntityAppId()
      );
    }
  }
}
