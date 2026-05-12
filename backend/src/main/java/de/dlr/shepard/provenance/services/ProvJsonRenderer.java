package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.entities.Activity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W3C PROV-JSON serialisation for {@link Activity} rows. Designed in
 * {@code aidocs/55 §6}.
 *
 * <p>The output is a small subset of the
 * <a href="https://www.w3.org/Submission/2013/SUBM-prov-json-20130424/">W3C
 * PROV-JSON Submission</a> shape: a {@code prefix} block, an
 * {@code activity} block keyed by qualified-name, and the
 * {@code wasAssociatedWith}/{@code used}/{@code wasGeneratedBy}
 * relationship blocks. Sufficient for round-tripping a shepard
 * activity log through tools that consume PROV-JSON without claiming
 * full W3C-spec conformance.
 *
 * <p>Returned as a {@link Map} so JAX-RS / Jackson serialise it as
 * generic JSON; the {@code application/prov+json} media-type is
 * applied at the resource layer.
 */
@ApplicationScoped
public class ProvJsonRenderer {

  public static final String MEDIA_TYPE = "application/prov+json";

  static final String SHEPARD_PREFIX = "shepard";
  static final String SHEPARD_NS = "https://noheton.github.io/shepard/prov#";
  static final String PROV_PREFIX = "prov";
  static final String PROV_NS = "http://www.w3.org/ns/prov#";

  /**
   * Render a list of activities as PROV-JSON. The result map is
   * insertion-ordered so the output reads top-to-bottom.
   */
  public Map<String, Object> render(List<Activity> activities) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("prefix", Map.of(SHEPARD_PREFIX, SHEPARD_NS, PROV_PREFIX, PROV_NS));

    Map<String, Object> activityBlock = new LinkedHashMap<>();
    Map<String, Object> agentBlock = new LinkedHashMap<>();
    Map<String, Object> entityBlock = new LinkedHashMap<>();
    Map<String, Object> wasAssociatedBlock = new LinkedHashMap<>();
    Map<String, Object> usedBlock = new LinkedHashMap<>();
    Map<String, Object> generatedBlock = new LinkedHashMap<>();

    int ix = 0;
    for (Activity a : activities) {
      String actId = SHEPARD_PREFIX + ":activity/" + (a.getAppId() == null ? "ix-" + ix : a.getAppId());
      Map<String, Object> actNode = new LinkedHashMap<>();
      if (a.getActionKind() != null) actNode.put(PROV_PREFIX + ":type", SHEPARD_PREFIX + ":" + a.getActionKind());
      if (a.getStartedAtMillis() != null) actNode.put(PROV_PREFIX + ":startTime", isoFromMillis(a.getStartedAtMillis()));
      if (a.getEndedAtMillis() != null) actNode.put(PROV_PREFIX + ":endTime", isoFromMillis(a.getEndedAtMillis()));
      if (a.getSummary() != null) actNode.put(SHEPARD_PREFIX + ":summary", a.getSummary());
      if (a.getOriginInstance() != null) actNode.put(SHEPARD_PREFIX + ":originInstance", a.getOriginInstance());
      activityBlock.put(actId, actNode);

      if (a.getAgentUsername() != null) {
        String agentId = SHEPARD_PREFIX + ":agent/" + a.getAgentUsername();
        agentBlock.putIfAbsent(agentId, Map.of(PROV_PREFIX + ":type", PROV_PREFIX + ":Person"));
        wasAssociatedBlock.put(
          "_:wa" + ix,
          Map.of(PROV_PREFIX + ":activity", actId, PROV_PREFIX + ":agent", agentId)
        );
      }

      if (a.getTargetAppId() != null) {
        String entityId = SHEPARD_PREFIX + ":entity/" + a.getTargetAppId();
        Map<String, Object> entNode = new LinkedHashMap<>();
        if (a.getTargetKind() != null) entNode.put(PROV_PREFIX + ":type", SHEPARD_PREFIX + ":" + a.getTargetKind());
        entityBlock.putIfAbsent(entityId, entNode);

        if (isReadAction(a.getActionKind())) {
          usedBlock.put(
            "_:u" + ix,
            Map.of(PROV_PREFIX + ":activity", actId, PROV_PREFIX + ":entity", entityId)
          );
        } else if (isWriteAction(a.getActionKind())) {
          generatedBlock.put(
            "_:g" + ix,
            Map.of(PROV_PREFIX + ":entity", entityId, PROV_PREFIX + ":activity", actId)
          );
        }
      }

      ix++;
    }

    if (!activityBlock.isEmpty()) out.put("activity", activityBlock);
    if (!agentBlock.isEmpty()) out.put("agent", agentBlock);
    if (!entityBlock.isEmpty()) out.put("entity", entityBlock);
    if (!wasAssociatedBlock.isEmpty()) out.put("wasAssociatedWith", wasAssociatedBlock);
    if (!usedBlock.isEmpty()) out.put("used", usedBlock);
    if (!generatedBlock.isEmpty()) out.put("wasGeneratedBy", generatedBlock);

    return out;
  }

  static boolean isReadAction(String actionKind) {
    return "READ".equals(actionKind);
  }

  static boolean isWriteAction(String actionKind) {
    return "CREATE".equals(actionKind) || "UPDATE".equals(actionKind) || "DELETE".equals(actionKind) || "EXECUTE".equals(actionKind);
  }

  private static String isoFromMillis(long millis) {
    return Instant.ofEpochMilli(millis).toString();
  }
}
