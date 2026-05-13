package de.dlr.shepard.plugins.unhide.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedEntryIO;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UH1a — feed-assembly service.
 *
 * <p>Owns the Phase 1 logic of "list every Collection on the
 * instance (visible to the harvest caller), project each onto the
 * schema.org + metadata4ing JSON-LD frame, return the cursor-paged
 * page."
 *
 * <p>Phase 1 scope per the slice ticket:
 *
 * <ul>
 *   <li>Collections only (DataObjects come later — once KIP1a /
 *       publication wiring lands a DataObject can earn its own PID).</li>
 *   <li>No publish-toggle filter yet (CP1a's {@code publishToHelmholtzKG}
 *       is the design's per-Collection opt-in; it's not yet wired
 *       through — UH1d shipping the per-Collection toggle UI is the
 *       milestone after this one). For now every Collection visible
 *       to the caller appears in the feed; the master toggle on
 *       {@code :UnhideConfig.enabled} is the only gate.</li>
 *   <li>No m4i {@code hasProcessingStep} bodies — UH1b plugs those
 *       in when PROV1h content-neg ships.</li>
 *   <li>Pagination via {@code ?page=N&page-size=N} (page-size capped
 *       at {@link #MAX_PAGE_SIZE}). Cursor-based pagination per
 *       {@code aidocs/13 §2.6} is the upgrade once we have a stable
 *       sort-key — Phase 1 keeps it simple.</li>
 * </ul>
 */
@ApplicationScoped
public class UnhideFeedService {

  /** Hard cap on the cursor-page-size param (DoS guard). */
  public static final int MAX_PAGE_SIZE = 1000;

  /** Default + minimum page size when the caller omits the parameter. */
  public static final int DEFAULT_PAGE_SIZE = 100;

  @Inject
  CollectionDAO collectionDAO;

  /**
   * Build a paged feed page from the visible Collections. Caller is
   * responsible for verifying the master toggle is on; this method
   * assumes {@code config.isEnabled()} and just renders.
   *
   * @param config the {@link UnhideConfig} singleton (for the
   *     contact-email surfaced in the meta block).
   * @param baseUrl the absolute URL prefix (scheme + host) used to
   *     build per-entry {@code @id} URIs (e.g.
   *     {@code https://shepard.example.dlr.de}). Trailing slash
   *     tolerated.
   * @param page zero-based page index. {@code <0} clamps to 0.
   * @param pageSize requested page size; clamped to
   *     {@code [1, MAX_PAGE_SIZE]}; {@code <=0} substitutes the
   *     default.
   * @return ready-to-serialise feed page.
   */
  public FeedIO buildFeed(UnhideConfig config, String baseUrl, int page, int pageSize) {
    int clampedPage = Math.max(0, page);
    int clampedSize = clampPageSize(pageSize);
    String rootBase = stripTrailingSlash(baseUrl);

    // Phase 1: load all collections, project to the visible window.
    // The list is bounded by the install's Collection count; future
    // slices (UH1c) will push pagination into the Cypher query
    // proper once the publish-toggle filter lands.
    List<Collection> all = new ArrayList<>(collectionDAO.findAll());
    all.removeIf(c -> c == null || c.isDeleted());
    // Stable sort: oldest createdAt first so pagination is consistent
    // call-to-call. Falls back to appId for collections with the same
    // timestamp.
    all.sort((a, b) -> {
      Date ad = a.getCreatedAt();
      Date bd = b.getCreatedAt();
      int byTime = (ad == null ? 0L : ad.getTime()) < (bd == null ? 0L : bd.getTime())
        ? -1
        : (ad == null ? 0L : ad.getTime()) > (bd == null ? 0L : bd.getTime()) ? 1 : 0;
      if (byTime != 0) return byTime;
      String aa = a.getAppId();
      String bb = b.getAppId();
      if (aa == null && bb == null) return 0;
      if (aa == null) return -1;
      if (bb == null) return 1;
      return aa.compareTo(bb);
    });

    int total = all.size();
    int from = Math.min(clampedPage * clampedSize, total);
    int to = Math.min(from + clampedSize, total);
    List<Collection> window = all.subList(from, to);

    List<FeedEntryIO> entries = new ArrayList<>(window.size());
    for (Collection c : window) {
      entries.add(toFeedEntry(c, rootBase));
    }

    Map<String, Object> meta = new HashMap<>();
    meta.put("page", clampedPage);
    meta.put("pageSize", clampedSize);
    meta.put("totalEntries", total);
    meta.put("totalPages", (int) Math.ceil((double) total / clampedSize));
    meta.put("generatedAt", new Date());
    if (config.getContactEmail() != null && !config.getContactEmail().isBlank()) {
      meta.put("contactEmail", config.getContactEmail());
    }

    return new FeedIO(FeedIO.defaultContext(), entries, meta);
  }

  /**
   * Project a single {@link Collection} onto the schema.org +
   * metadata4ing JSON-LD entry. Visible for tests.
   */
  FeedEntryIO toFeedEntry(Collection c, String baseUrl) {
    String id = baseUrl + "/v2/collections/" + (c.getAppId() == null ? "" : c.getAppId());
    Object creator = creatorOf(c.getCreatedBy());
    return new FeedEntryIO(
      id,
      List.of("schema:Dataset", "m4i:Dataset"),
      c.getName(),
      c.getDescription(),
      c.getCreatedAt(),
      c.getUpdatedAt(),
      null, // license — Collection schema doesn't carry one yet; UH1b adds it via CP1a properties
      creator,
      // m4i:isAbout placeholder — UH1b will populate from PROV1h trail.
      List.of()
    );
  }

  /**
   * Build a {@code schema:Person} sub-object from a shepard
   * {@link User}. Emits {@code @id: https://orcid.org/{ORCID}} when
   * an ORCID is on file so Unhide's harvester can equate identities
   * across Helmholtz datasets; otherwise omits {@code @id} (the
   * person is identified by name only).
   *
   * <p>Visible for tests.
   */
  static Object creatorOf(User user) {
    if (user == null) return null;
    Map<String, Object> creator = new HashMap<>();
    creator.put("@type", "schema:Person");
    String orcid = user.getOrcid();
    if (orcid != null && !orcid.isBlank()) {
      creator.put("@id", "https://orcid.org/" + orcid.trim());
    }
    String displayName = displayNameOf(user);
    if (displayName != null) {
      creator.put("name", displayName);
    }
    return creator;
  }

  private static String displayNameOf(User user) {
    if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
      return user.getDisplayName();
    }
    String first = user.getFirstName();
    String last = user.getLastName();
    if (first != null && last != null && !first.isBlank() && !last.isBlank()) {
      return first.trim() + " " + last.trim();
    }
    if (user.getUsername() != null && !user.getUsername().isBlank()) {
      return user.getUsername();
    }
    return null;
  }

  static int clampPageSize(int requested) {
    if (requested <= 0) return DEFAULT_PAGE_SIZE;
    return Math.min(requested, MAX_PAGE_SIZE);
  }

  private static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }
}
