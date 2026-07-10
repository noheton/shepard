package de.dlr.shepard.v2.references.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO.UrdfCandidate;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.references.io.AccessibleUrdfIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * URDF-FILEREF-PICKER-SEARCHABLE — backs the instance-wide accessible-URDF
 * list ({@code GET /v2/references/urdf}) that populates the "Visualize in 3D →
 * URDF" searchable picker.
 *
 * <p>The {@code GET /v2/references?kind=file} DataObject-scoped list can only
 * ever show URDFs attached to the current DataObject — but the "Visualize in 3D"
 * dialog opens from a <em>timeseries</em> whose DataObject usually carries no
 * URDF (the real robot model lives in a different collection). This service is
 * the missing seam: it lists every URDF singleton the caller may read across the
 * whole instance, so the picker is genuinely populated and the user never has to
 * paste a UUID (see the "the user never types an ID" rule in CLAUDE.md).
 *
 * <p>Flow: fetch the instance-wide URDF candidates (DAO, unfiltered) → narrow to
 * the collections the caller may {@link AccessType#Read} via
 * {@link PermissionsService#filterAllowedDataObjectAppIds} → apply the optional
 * {@code q} name filter (case-insensitive substring) → page. The result
 * {@code total} reflects the count <em>after</em> permission + {@code q}
 * filtering (what the user can actually see), so the frontend can key off it.
 *
 * <p><strong>Fail-soft:</strong> any read failure (Neo4j down, permission
 * lookup error) yields an empty page, never a 500 — a broken picker degrades to
 * the "advanced: paste appId" fallback rather than taking down the dialog.
 */
@RequestScoped
public class AccessibleUrdfService {

  /** Hard ceiling on candidates evaluated for permission — URDFs are sparse instance-wide. */
  private static final int MAX_CANDIDATES = 5_000;

  @Inject
  SingletonFileReferenceDAO singletonFileReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  /**
   * List the URDF singletons the caller may read, instance-wide, filtered by an
   * optional name query and paged.
   *
   * @param caller   the authenticated username (permission subject). When null/blank
   *                 the result is empty (nothing is readable without a subject).
   * @param q        optional case-insensitive substring filter on the reference name;
   *                 null/blank means "no filter".
   * @param page     zero-based page index (negatives clamped to 0).
   * @param pageSize items per page (clamped to [1, 200]).
   * @return a paged envelope of {@link AccessibleUrdfIO}; never null, never throws.
   */
  public PagedResponseIO<AccessibleUrdfIO> listAccessible(String caller, String q, int page, int pageSize) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(200, Math.max(1, pageSize));
    if (caller == null || caller.isBlank()) {
      return new PagedResponseIO<>(List.of(), 0, safePage, safeSize);
    }
    try {
      List<UrdfCandidate> candidates = singletonFileReferenceDAO.findAllUrdfCandidates();
      if (candidates.isEmpty()) {
        return new PagedResponseIO<>(List.of(), 0, safePage, safeSize);
      }
      // Defense-in-depth: the DAO already filters to URDFs in Cypher, but re-apply
      // the predicate here so a future DAO refactor can't silently leak non-URDFs.
      List<UrdfCandidate> urdfs = candidates.stream()
        .limit(MAX_CANDIDATES)
        .filter(c -> isUrdfCandidate(c.name(), c.fileKind()))
        .filter(c -> matchesQuery(c.name(), q))
        .toList();
      if (urdfs.isEmpty()) {
        return new PagedResponseIO<>(List.of(), 0, safePage, safeSize);
      }

      Set<String> allowedDoAppIds = permissionsService.filterAllowedDataObjectAppIds(
        urdfs.stream().map(UrdfCandidate::dataObjectAppId).toList(),
        AccessType.Read,
        caller
      );

      List<AccessibleUrdfIO> visible = urdfs.stream()
        .filter(c -> allowedDoAppIds.contains(c.dataObjectAppId()))
        .map(c -> new AccessibleUrdfIO(
          c.refAppId(), c.name(), c.dataObjectAppId(), c.collectionAppId(), c.collectionName()))
        .toList();

      int total = visible.size();
      int from = Math.min((int) Math.min((long) safePage * safeSize, Integer.MAX_VALUE), total);
      int to = Math.min(from + safeSize, total);
      List<AccessibleUrdfIO> pageItems = visible.subList(from, to);
      return new PagedResponseIO<>(List.copyOf(pageItems), total, safePage, safeSize);
    } catch (RuntimeException ex) {
      Log.warnf(ex, "URDF-FILEREF-PICKER-SEARCHABLE: accessible-URDF list failed for caller=%s — returning empty", caller);
      return new PagedResponseIO<>(List.of(), 0, safePage, safeSize);
    }
  }

  /**
   * A reference is a URDF candidate when its name ends {@code .urdf}
   * (case-insensitive) or its {@code fileKind} is {@code urdf}. Static + pure so
   * the exclusion contract is unit-testable without a DB.
   */
  public static boolean isUrdfCandidate(String name, String fileKind) {
    if (fileKind != null && "urdf".equalsIgnoreCase(fileKind.trim())) return true;
    return name != null && name.toLowerCase(Locale.ROOT).endsWith(".urdf");
  }

  /** Case-insensitive substring match; a blank/null query matches everything. */
  static boolean matchesQuery(String name, String q) {
    if (q == null || q.isBlank()) return true;
    if (name == null) return false;
    return name.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT).trim());
  }
}
