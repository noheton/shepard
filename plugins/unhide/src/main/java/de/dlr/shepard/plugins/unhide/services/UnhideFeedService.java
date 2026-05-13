package de.dlr.shepard.plugins.unhide.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedEntryIO;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * UH1a / UH1b — feed-assembly service.
 *
 * <p>Owns the "list every Collection on the instance (visible to
 * the harvest caller), project each onto the schema.org +
 * metadata4ing JSON-LD frame with m4i provenance fragments, return
 * the cursor-paged page."
 *
 * <p>Phase 1 (UH1a) scope: Collections only; no publish-toggle
 * filter yet (the per-Collection {@code publishToHelmholtzKG} is
 * the UH1d milestone). For now every non-deleted Collection visible
 * to the caller appears in the feed; the master toggle on
 * {@code :UnhideConfig.enabled} is the only gate.
 *
 * <p>UH1b extension: each entry's body grows a
 * {@code m4i:hasProcessingStep} array of the most-recent N
 * {@code :Activity} rows targeting the Collection. Window size is
 * the deploy-time-only {@code shepard.unhide.feed.provenance-window}
 * property (default 5; "buffer-sizing" exception per the CLAUDE.md
 * admin-config rule — no operator demand for per-window tuning at
 * runtime).
 *
 * <p>Pagination via {@code ?page=N&page-size=N} (page-size capped at
 * {@link #MAX_PAGE_SIZE}). Cursor-based pagination per
 * {@code aidocs/13 §2.6} is the upgrade once we have a stable
 * sort-key — Phase 1 keeps it simple.
 */
@ApplicationScoped
public class UnhideFeedService {

  /** Hard cap on the cursor-page-size param (DoS guard). */
  public static final int MAX_PAGE_SIZE = 1000;

  /** Default + minimum page size when the caller omits the parameter. */
  public static final int DEFAULT_PAGE_SIZE = 100;

  /**
   * Default provenance window when no operator-supplied override is
   * present. Five activities is the "what changed lately" eye-test —
   * enough to give Unhide a representative slice without bloating
   * every feed entry with the full history.
   */
  public static final int DEFAULT_PROVENANCE_WINDOW = 5;

  /** Hard cap on the provenance-window so a typo doesn't bloat the feed. */
  static final int MAX_PROVENANCE_WINDOW = 100;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  ActivityDAO activityDAO;

  @Inject
  ProvJsonLdRenderer provJsonLdRenderer;

  @ConfigProperty(name = "shepard.unhide.feed.provenance-window", defaultValue = "5")
  int provenanceWindow;

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

    // UH1b — m4i:hasProcessingStep: most-recent N activities targeting
    // this Collection's appId, rendered via the PROV1h renderer.
    List<Object> processingSteps = buildProcessingSteps(c.getAppId());

    return new FeedEntryIO(
      id,
      List.of("schema:Dataset", "m4i:Dataset"),
      c.getName(),
      c.getDescription(),
      c.getCreatedAt(),
      c.getUpdatedAt(),
      null, // license — Collection schema doesn't carry one yet; UH1d wires it via CP1a properties
      creator,
      processingSteps
    );
  }

  /**
   * UH1b — fetch the most-recent {@code N} {@code :Activity} rows
   * targeting the given Collection appId and render each as a m4i
   * {@code ProcessingStep} node via {@link ProvJsonLdRenderer}.
   *
   * <p>Returns {@code null} (not {@code []}) when the Collection has
   * no activities — the {@code @JsonInclude(NON_NULL)} on
   * {@link FeedEntryIO} drops the field, which is the correct
   * JSON-LD semantics for "no provenance available."
   *
   * @param collectionAppId the Collection's {@code appId}; {@code null}
   *     yields {@code null}.
   * @return a list of m4i ProcessingStep node bodies (most-recent
   *     first per {@code ActivityDAO#list}'s default DESC sort), or
   *     {@code null} when no activities exist.
   */
  List<Object> buildProcessingSteps(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) {
      return null;
    }
    int window = clampProvenanceWindow(provenanceWindow);
    List<Activity> recent;
    try {
      recent = activityDAO.list(null, null, collectionAppId, null, null, window);
    } catch (RuntimeException e) {
      // Defence in depth: a Cypher hiccup on one Collection's
      // provenance read shouldn't fail the whole feed page. Log,
      // omit the field, carry on. Same fail-soft posture as the
      // UnhideConfigService startup-seed (ADR-0014's "ontology
      // bootstrap failure is non-fatal" precedent).
      Log.warnf(e, "UH1b: could not load activities for Collection appId=%s; omitting m4i:hasProcessingStep", collectionAppId);
      return null;
    }
    if (recent == null || recent.isEmpty()) {
      return null;
    }
    List<Object> out = new ArrayList<>(recent.size());
    for (Activity a : recent) {
      Map<String, Object> node = provJsonLdRenderer.renderActivityAsM4iNode(a);
      if (node != null && !node.isEmpty()) {
        out.add(node);
      }
    }
    return out.isEmpty() ? null : out;
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

  static int clampProvenanceWindow(int requested) {
    if (requested <= 0) return DEFAULT_PROVENANCE_WINDOW;
    return Math.min(requested, MAX_PROVENANCE_WINDOW);
  }

  private static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }
}
