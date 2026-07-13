package de.dlr.shepard.v2.git.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PLUGIN-REF-HANDLER-GIT — {@link ReferenceKindHandler} for {@code kind=git}.
 *
 * <p>Discovered via CDI {@code @Any Instance<ReferenceKindHandler>} by the
 * {@code ReferencesV2Service} dispatcher. Delegates to the existing
 * {@link GitReferenceDAO}.
 *
 * <p>Payload key set: {@code repoUrl, ref, path, mode, sha, resolvedSha,
 * resolvedAt}.
 *
 * <p>Mutable fields via PATCH: {@code name, repoUrl, ref, path, mode}.
 * Complex mode transitions (PINNED_SNAPSHOT SHA resolution) are deferred
 * to action endpoints in {@code GitReferenceActionsRest}; the handler only
 * applies the simple field set.
 */
@RequestScoped
public class GitReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  GitReferenceDAO gitReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Override
  public String kind() {
    return "git";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof GitReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return gitReferenceDAO.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    GitReference ref = (GitReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.put("repoUrl", ref.getRepoUrl());
    io.put("ref", ref.getRef());
    io.put("path", ref.getPath());
    io.put("mode", ref.getMode() == null ? null : ref.getMode().name());
    io.put("sha", ref.getSha());
    io.put("resolvedSha", ref.getResolvedSha());
    io.put("resolvedAt", toIso(ref.getResolvedAtMillis()));
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) throw new BadRequestException("create body is required for kind=git");
    String repoUrl = asString(body.get("repoUrl"));
    if (repoUrl == null || repoUrl.isBlank()) {
      throw new BadRequestException("repoUrl is required and must be non-blank");
    }
    DataObject parent = resolveParent(dataObjectAppId);

    GitReference gr = new GitReference(repoUrl, asString(body.get("ref")), asString(body.get("path")));
    GitReferenceMode mode = parseMode(asString(body.get("mode")));
    gr.setMode(mode);
    // TRACKED_ARTIFACT requires ref + path.
    if (mode == GitReferenceMode.TRACKED_ARTIFACT) {
      if (gr.getRef() == null || gr.getRef().isBlank() ||
          gr.getPath() == null || gr.getPath().isBlank()) {
        throw new BadRequestException("TRACKED_ARTIFACT mode requires non-blank `ref` and `path`");
      }
    }
    // PINNED_SNAPSHOT SHA resolution requires a git adapter + PAT — defer to the
    // action endpoints in GitReferenceActionsRest; the unified create path supports only
    // LOOSE_LINK and TRACKED_ARTIFACT modes.
    if (mode == GitReferenceMode.PINNED_SNAPSHOT) {
      throw new BadRequestException(
        "PINNED_SNAPSHOT mode requires SHA resolution via a git adapter — " +
        "use POST /v2/references/{appId}/check-update after creating the reference"
      );
    }
    if (body.containsKey("name")) {
      String name = asString(body.get("name"));
      if (name != null && !name.isBlank()) gr.setName(name);
    }
    gr.setDataObject(parent);
    GitReference saved = gitReferenceDAO.createOrUpdate(gr);
    return toIO(saved);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    GitReference ref = gitReferenceDAO.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No GitReference with appId " + appId);
    boolean changed = false;
    if (patch == null || patch.isEmpty()) return toIO(ref);

    if (patch.containsKey("name")) {
      Object v = patch.get("name");
      if (!(v instanceof String s) || s.isBlank()) {
        throw new BadRequestException("'name' must be a non-blank string");
      }
      if (!s.equals(ref.getName())) {
        ref.setName(s);
        changed = true;
      }
    }
    if (patch.containsKey("repoUrl")) {
      String v = asString(patch.get("repoUrl"));
      if (v == null || v.isBlank()) throw new BadRequestException("repoUrl must not be null or blank");
      if (!v.equals(ref.getRepoUrl())) { ref.setRepoUrl(v); changed = true; }
    }
    if (patch.containsKey("ref")) {
      String v = asString(patch.get("ref"));
      if (!java.util.Objects.equals(v, ref.getRef())) { ref.setRef(v); changed = true; }
    }
    if (patch.containsKey("path")) {
      String v = asString(patch.get("path"));
      if (!java.util.Objects.equals(v, ref.getPath())) { ref.setPath(v); changed = true; }
    }
    if (patch.containsKey("mode")) {
      GitReferenceMode mode = parseMode(asString(patch.get("mode")));
      if (mode != ref.getMode()) { ref.setMode(mode); changed = true; }
    }
    if (changed) {
      User user = userService.getCurrentUser();
      ref.setUpdatedAt(dateHelper.getDate());
      ref.setUpdatedBy(user);
      ref = gitReferenceDAO.createOrUpdate(ref);
    }
    return toIO(ref);
  }

  @Override
  public void delete(String appId) {
    GitReference ref = gitReferenceDAO.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No GitReference with appId " + appId);
    gitReferenceDAO.deleteByNeo4jId(ref.getId());
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<GitReference> refs = gitReferenceDAO.findByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (GitReference ref : refs) {
      if (ref != null) out.add(toIO(ref));
    }
    return out;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private DataObject resolveParent(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    DataObject parent = dataObjectDAO.findByAppId(dataObjectAppId);
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    return parent;
  }

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }

  private static GitReferenceMode parseMode(String value) {
    if (value == null) return GitReferenceMode.LOOSE_LINK;
    try {
      return GitReferenceMode.valueOf(value);
    } catch (IllegalArgumentException iae) {
      throw new BadRequestException(
        "mode must be one of LOOSE_LINK, TRACKED_ARTIFACT, PINNED_SNAPSHOT"
      );
    }
  }

  private static String toIso(Long ms) {
    if (ms == null) return null;
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms));
  }
}
