package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.entities.Activity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-LD renderer for {@link Activity} rows — emits either plain
 * <strong>PROV-O</strong> JSON-LD (default) or the
 * <strong>metadata4ing</strong> (m4i) profile shape on top of PROV-O.
 *
 * <p>Designed in {@code aidocs/64-provenance-architecture.md §3.2}
 * (PROV1h). The two flavours share field-level mappings; m4i extends
 * the {@code @type} list with engineering-research subclasses
 * (`m4i:ProcessingStep`, `m4i:InvestigatedObject`, `m4i:Person`) and
 * adds the m4i-flavoured input/output/method links — keeping the
 * PROV-O parent types in the array so a PROV-O-only client still
 * parses the document.
 *
 * <p>Contexts are embedded inline (no external `@context` URL hosting
 * for the v1 slice). The m4i context {@code @import}s PROV-O so a
 * single processor sees both vocabularies.
 *
 * <p>Returned as a {@link Map} so JAX-RS / Jackson serialises it as
 * generic JSON; the {@code application/ld+json} media-type is
 * applied at the resource layer (with the m4i {@code profile=}
 * parameter when applicable).
 */
@ApplicationScoped
public class ProvJsonLdRenderer {

  /** Plain JSON-LD media type — triggers PROV-O flavour by default. */
  public static final String MEDIA_TYPE = "application/ld+json";

  /** Stable m4i profile URI accepted on the {@code Accept} header. */
  public static final String M4I_PROFILE_URI = "https://w3id.org/nfdi4ing/metadata4ing/";

  /** Convenience short form (`profile=metadata4ing`). */
  public static final String M4I_PROFILE_SHORT = "metadata4ing";

  static final String PROV_NS = "http://www.w3.org/ns/prov#";
  static final String M4I_NS = "http://w3id.org/nfdi4ing/metadata4ing#";
  static final String SHEPARD_NS = "https://noheton.github.io/shepard/prov#";

  /**
   * Render the activity list as plain PROV-O JSON-LD.
   */
  public Map<String, Object> renderProvO(List<Activity> activities) {
    return render(activities, false);
  }

  /**
   * Render the activity list as metadata4ing-flavoured JSON-LD.
   * The PROV-O parent types are preserved in each node's
   * {@code @type} array so a PROV-O-only client still resolves the
   * document.
   */
  public Map<String, Object> renderMetadata4ing(List<Activity> activities) {
    return render(activities, true);
  }

  /**
   * Render-with-profile dispatcher used by the REST layer; pick the
   * shape per the request's {@code Accept: ; profile=} parameter.
   */
  public Map<String, Object> render(List<Activity> activities, ProfileChoice profile) {
    return switch (profile) {
      case M4I -> renderMetadata4ing(activities);
      case PROV_O -> renderProvO(activities);
    };
  }

  /**
   * Render a row-count integer as a thin JSON-LD wrapper. Default
   * carries a {@code shepard:numberOfActivities} key under a typed
   * {@code @context}; m4i flavour adds the m4i namespace symmetry so
   * the JSON-LD media-type parameter and the body's
   * {@code @context} agree on the alphabet.
   *
   * <p>Designed in {@code aidocs/64-provenance-architecture.md §6}
   * (the JSON-LD variant of {@code /v2/provenance/count}). The
   * integer is typed as {@code xsd:nonNegativeInteger} so a
   * downstream SPARQL store rounds-trips cleanly.
   */
  public Map<String, Object> renderCount(long count, ProfileChoice profile) {
    boolean m4i = profile == ProfileChoice.M4I;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("@context", buildContext(m4i));

    Map<String, Object> typed = new LinkedHashMap<>();
    typed.put("@type", "xsd:nonNegativeInteger");
    typed.put("@value", Long.toString(count));
    out.put("shepard:numberOfActivities", typed);
    return out;
  }

  private Map<String, Object> render(List<Activity> activities, boolean m4i) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("@context", buildContext(m4i));

    List<Map<String, Object>> graph = new ArrayList<>();
    int ix = 0;
    for (Activity a : activities) {
      String actId = "shepard:activity/" + (a.getAppId() == null ? "ix-" + ix : a.getAppId());

      Map<String, Object> actNode = new LinkedHashMap<>();
      actNode.put("@id", actId);
      actNode.put("@type", activityTypes(m4i));
      if (a.getStartedAtMillis() != null) {
        actNode.put("prov:startedAtTime", typedDateTime(a.getStartedAtMillis()));
      }
      if (a.getEndedAtMillis() != null) {
        actNode.put("prov:endedAtTime", typedDateTime(a.getEndedAtMillis()));
      }
      if (a.getActionKind() != null) {
        // Pragmatic m4i:hasMethod mapping (POST/PUT/PATCH/DELETE → Create/Update/Delete);
        // surface the same value via prov:type for PROV-O parsers.
        actNode.put("prov:type", "shepard:" + a.getActionKind());
        if (m4i) actNode.put("m4i:hasMethod", "shepard:method/" + a.getActionKind());
      }
      if (a.getSummary() != null) actNode.put("shepard:summary", a.getSummary());
      if (a.getOriginInstance() != null) actNode.put("shepard:originInstance", a.getOriginInstance());

      // Agent association — wasAssociatedWith.
      if (a.getAgentUsername() != null) {
        String agentId = "shepard:agent/" + a.getAgentUsername();
        actNode.put("prov:wasAssociatedWith", Map.of("@id", agentId));
        // Emit the agent node into the @graph so a JSON-LD framer
        // can resolve it. Dedup is handled by the framer / consumer;
        // we still emit one node per activity-occurrence (cheap and
        // additive — agent rows are tiny).
        Map<String, Object> agentNode = new LinkedHashMap<>();
        agentNode.put("@id", agentId);
        agentNode.put("@type", agentTypes(m4i));
        agentNode.put("shepard:username", a.getAgentUsername());
        graph.add(agentNode);
      }

      // Target entity — prov:used / prov:generated by action kind.
      if (a.getTargetAppId() != null) {
        String entityId = "shepard:entity/" + a.getTargetAppId();
        boolean isRead = ProvJsonRenderer.isReadAction(a.getActionKind());
        boolean isWrite = ProvJsonRenderer.isWriteAction(a.getActionKind());
        if (isRead) {
          actNode.put("prov:used", Map.of("@id", entityId));
          if (m4i) actNode.put("m4i:hasInput", Map.of("@id", entityId));
        } else if (isWrite) {
          actNode.put("prov:generated", Map.of("@id", entityId));
          if (m4i) actNode.put("m4i:hasOutput", Map.of("@id", entityId));
        }
        Map<String, Object> entityNode = new LinkedHashMap<>();
        entityNode.put("@id", entityId);
        entityNode.put("@type", entityTypes(m4i));
        if (a.getTargetKind() != null) entityNode.put("shepard:kind", a.getTargetKind());
        graph.add(entityNode);
      }

      graph.add(actNode);
      ix++;
    }

    out.put("@graph", graph);
    return out;
  }

  private static Map<String, Object> buildContext(boolean m4i) {
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("prov", PROV_NS);
    ctx.put("shepard", SHEPARD_NS);
    ctx.put("xsd", "http://www.w3.org/2001/XMLSchema#");
    if (m4i) {
      ctx.put("m4i", M4I_NS);
    }
    return ctx;
  }

  private static List<String> activityTypes(boolean m4i) {
    return m4i ? List.of("m4i:ProcessingStep", "prov:Activity") : List.of("prov:Activity");
  }

  private static List<String> agentTypes(boolean m4i) {
    return m4i ? List.of("m4i:Person", "prov:Agent", "prov:Person") : List.of("prov:Agent", "prov:Person");
  }

  private static List<String> entityTypes(boolean m4i) {
    return m4i ? List.of("m4i:InvestigatedObject", "prov:Entity") : List.of("prov:Entity");
  }

  private static Map<String, Object> typedDateTime(long millis) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("@type", "xsd:dateTime");
    v.put("@value", Instant.ofEpochMilli(millis).toString());
    return v;
  }

  /**
   * Which JSON-LD flavour the caller asked for. Selected from the
   * {@code Accept: application/ld+json[; profile=...]} header by
   * {@link #resolveProfile(String)}.
   */
  public enum ProfileChoice {
    /** Plain PROV-O JSON-LD — default when no {@code profile=} given. */
    PROV_O,
    /** metadata4ing-flavoured JSON-LD. */
    M4I,
  }

  /**
   * Resolve an {@code Accept} header's {@code profile} parameter
   * (RFC 6906) to a renderer flavour. The parameter is treated
   * case-insensitively after canonical un-quoting.
   *
   * <p>Returns {@code ProfileChoice.PROV_O} when no {@code profile}
   * parameter is present; {@code ProfileChoice.M4I} when the parameter
   * matches {@link #M4I_PROFILE_URI} or {@link #M4I_PROFILE_SHORT};
   * {@code null} when the parameter is present but unrecognised
   * (caller must surface a 406).
   *
   * @param acceptHeader the raw {@code Accept} header value; may be
   *     {@code null} (treated as no profile)
   * @return the chosen profile, or {@code null} if the
   *     {@code profile=} parameter is present but unknown
   */
  public static ProfileChoice resolveProfile(String acceptHeader) {
    String prof = extractProfileParam(acceptHeader);
    if (prof == null || prof.isBlank()) return ProfileChoice.PROV_O;
    String trimmed = prof.trim();
    if (trimmed.equalsIgnoreCase(M4I_PROFILE_URI) || trimmed.equalsIgnoreCase(M4I_PROFILE_SHORT)) {
      return ProfileChoice.M4I;
    }
    // Strip an optional trailing slash before re-matching, so
    // `https://w3id.org/nfdi4ing/metadata4ing` (no slash) also wins.
    if (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    String urlNoSlash = M4I_PROFILE_URI.endsWith("/")
      ? M4I_PROFILE_URI.substring(0, M4I_PROFILE_URI.length() - 1)
      : M4I_PROFILE_URI;
    if (trimmed.equalsIgnoreCase(urlNoSlash)) return ProfileChoice.M4I;
    return null;
  }

  /**
   * Pull the {@code profile=} parameter value out of an Accept header,
   * scanning the type/subtype clauses for {@code application/ld+json}.
   * Returns {@code null} when no profile parameter applies.
   *
   * <p>Exposed for the REST layer (which needs to distinguish "no
   * profile param given → default PROV-O" from "profile param given
   * but unrecognised → 406") and for direct unit tests. Conservative
   * parser — accepts the common shapes:
   * {@code application/ld+json; profile="<url>"} or
   * {@code application/ld+json; profile=metadata4ing}.
   */
  public static String extractProfileParam(String acceptHeader) {
    if (acceptHeader == null) return null;
    // Header may carry multiple media-type clauses comma-separated.
    String[] clauses = acceptHeader.split(",");
    for (String clause : clauses) {
      String c = clause.trim();
      if (c.isEmpty()) continue;
      // First subtoken is the media type; rest are parameters.
      String[] parts = c.split(";");
      String mediaType = parts[0].trim();
      if (!mediaType.equalsIgnoreCase(MEDIA_TYPE)) continue;
      for (int i = 1; i < parts.length; i++) {
        String p = parts[i].trim();
        if (p.regionMatches(true, 0, "profile=", 0, "profile=".length())) {
          String v = p.substring("profile=".length()).trim();
          // Strip surrounding quotes per RFC 7231.
          if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
          }
          return v;
        }
      }
    }
    return null;
  }
}
