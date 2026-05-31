package de.dlr.shepard.v2.scenegraph.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * SCENEGRAPH-PERMS-1 — per-scene permission walk for {@code :DigitalTwinScene}.
 *
 * <p>SCENEGRAPH-REST-1 shipped 2026-05-29 gated on {@code @Authenticated} only —
 * every authenticated caller could see and mutate every scene. This service is
 * the permanent shape: a scene inherits permissions from its source URDF
 * FileReference's parent DataObject's parent Collection.
 *
 * <h2>Walk</h2>
 * <pre>
 *   :DigitalTwinScene (scalar sourceFileAppId)
 *     ↳ :FileReference (loaded via SingletonFileReferenceService.getByAppId)
 *         ↳ :DataObject (ref.getDataObject() — OGM relationship)
 *             ↳ :Collection (reused via PermissionsService.isAccessAllowedForDataObjectAppId)
 * </pre>
 *
 * <p>This is the same walk {@link de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService}
 * uses when minting a scene from a URDF FileReference. Reusing it keeps the
 * permission contract identical between create-time and edit-time.
 *
 * <h2>Hand-built scenes (no {@code sourceFileAppId})</h2>
 *
 * <p>The DT1-PHASE-0 scaffold allows a scene to be hand-built — POST a scene
 * with only a {@code name}, then add frames and joints. Such scenes carry no
 * Collection anchor; resolving them is impossible without falling back to a
 * default policy. This service applies the {@code C3 fail-closed} default:
 * <strong>only the {@code instance-admin} role can read or mutate a scene with
 * no source-file anchor.</strong> Operators who want to expose hand-built
 * scenes to a wider audience should re-mint them from a URDF FileReference
 * (which gives them a real Collection anchor) — tracked under the eventual
 * {@code ownerCollectionAppId} extension noted in the
 * {@code SCENEGRAPH-PERMS-1} backlog row.
 *
 * <h2>Edge cases (all fail-closed)</h2>
 *
 * <ul>
 *   <li>Scene not found → {@code false} (caller-side should 404 before asking).</li>
 *   <li>{@code sourceFileAppId} resolves but the FileReference is missing
 *       (delete-cascade race) → {@code false}.</li>
 *   <li>FileReference has no parent DataObject (orphaned) → {@code false}.</li>
 *   <li>{@code caller} is null/blank → {@code false}.</li>
 * </ul>
 *
 * <p>Every failure mode favours the permission gate over the user experience —
 * an admin can always look up why a scene isn't visible via the audit log.
 */
@ApplicationScoped
public class SceneGraphPermissionService {

  public static final String INSTANCE_ADMIN_ROLE = "instance-admin";

  @Inject
  SceneGraphService sceneGraphService;

  @Inject
  SingletonFileReferenceService singletonFileReferenceService;

  @Inject
  PermissionsService permissionsService;

  /**
   * Walks the scene → FileReference → DataObject → Collection chain and
   * returns whether {@code caller} has the requested {@link AccessType} on
   * the resolved parent Collection. See class Javadoc for the fail-closed
   * branches.
   *
   * <p>{@code isAdmin} is consulted as an override for hand-built scenes
   * (no {@code sourceFileAppId}) — only admins can reach them. For scenes
   * with a real source-file anchor, the Collection's permission node is
   * the SoT and admin role plays no special part.
   *
   * @param sceneAppId scene's appId.
   * @param accessType {@link AccessType#Read} or {@link AccessType#Write}.
   * @param caller     authenticated username.
   * @param isAdmin    whether the caller carries the {@code instance-admin}
   *                   role (consulted for hand-built scenes only).
   * @return {@code true} if access is allowed; {@code false} fail-closed
   *         otherwise.
   */
  public boolean isAllowed(String sceneAppId, AccessType accessType, String caller, boolean isAdmin) {
    if (sceneAppId == null || sceneAppId.isBlank()) return false;
    if (caller == null || caller.isBlank()) return false;

    DigitalTwinScene scene = sceneGraphService.findScene(sceneAppId);
    if (scene == null) return false;

    String sourceFileAppId = scene.getSourceFileAppId();
    if (sourceFileAppId == null || sourceFileAppId.isBlank()) {
      // Hand-built scene: no Collection anchor. Fail-closed per C3 — only
      // instance-admin can reach it. Logged at debug so an operator can
      // trace surprise denials without log spam.
      if (!isAdmin) {
        Log.debugf(
          "SCENEGRAPH-PERMS-1: denying %s on hand-built scene %s (no sourceFileAppId; caller=%s)",
          accessType, sceneAppId, caller
        );
      }
      return isAdmin;
    }

    FileReference ref;
    try {
      ref = singletonFileReferenceService.getByAppId(sourceFileAppId);
    } catch (RuntimeException e) {
      Log.debugf(e, "SCENEGRAPH-PERMS-1: FileReference resolve failed for scene %s", sceneAppId);
      return false;
    }
    if (ref == null) {
      // FileReference no longer exists (delete-cascade race or wrong reference
      // type — sourceFileAppId may point at a bundle the user manually patched).
      Log.debugf(
        "SCENEGRAPH-PERMS-1: scene %s sourceFileAppId %s did not resolve as a singleton FileReference",
        sceneAppId, sourceFileAppId
      );
      return false;
    }
    if (ref.getDataObject() == null || ref.getDataObject().getAppId() == null) {
      Log.debugf(
        "SCENEGRAPH-PERMS-1: scene %s has FileReference %s without parent DataObject (orphaned)",
        sceneAppId, sourceFileAppId
      );
      return false;
    }

    String parentDoAppId = ref.getDataObject().getAppId();
    return permissionsService.isAccessAllowedForDataObjectAppId(parentDoAppId, accessType, caller);
  }

  /**
   * Create-time variant: a scene does not exist yet, so the walk starts from
   * the caller-supplied {@code sourceFileAppId} directly. Returns whether the
   * caller has Write on the parent Collection of that FileReference. Used by
   * {@code POST /v2/scene-graphs} when the body carries a {@code sourceFileAppId}.
   *
   * @param sourceFileAppId the supplied URDF FileReference appId.
   * @param caller          authenticated username.
   * @return {@code true} if Write is allowed on the parent Collection;
   *         {@code false} fail-closed otherwise.
   */
  public boolean canCreateFromSourceFile(String sourceFileAppId, String caller) {
    if (sourceFileAppId == null || sourceFileAppId.isBlank()) return false;
    if (caller == null || caller.isBlank()) return false;
    FileReference ref;
    try {
      ref = singletonFileReferenceService.getByAppId(sourceFileAppId);
    } catch (RuntimeException e) {
      Log.debugf(e, "SCENEGRAPH-PERMS-1 (create): FileReference resolve failed for %s", sourceFileAppId);
      return false;
    }
    if (ref == null) return false;
    if (ref.getDataObject() == null || ref.getDataObject().getAppId() == null) return false;
    return permissionsService.isAccessAllowedForDataObjectAppId(
      ref.getDataObject().getAppId(), AccessType.Write, caller
    );
  }
}
