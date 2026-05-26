package de.dlr.shepard.provenance.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.provenance.entities.ActivityActionKind;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * Startup alarm that WARNs if any {@code :Activity.actionKind} value in the
 * live Neo4j database is outside the canonical set defined in
 * {@link ActivityActionKind#ALL_KNOWN_VALUES}.
 *
 * <p>Neo4j Community Edition does not support value-existence or
 * property-type constraints, so the database cannot enforce the enum
 * natively. This alarm is the monitoring counterpart to the write-path
 * validation in {@link ProvenanceService#record}: the validation prevents
 * new rogue values entering; the alarm catches any that crept in before
 * validation was in place (or via a direct Cypher write bypassing the
 * application layer).
 *
 * <p>The alarm logs at {@code WARN} and never throws — it is a monitoring
 * hook, not a hard-fail gate. Operators should investigate any WARN output
 * from this class; a stale actionKind value in the database indicates either
 * a code path that bypasses {@link ProvenanceService} or a manual Cypher
 * write that introduced a typo.
 *
 * <p>Part of NEO-AUDIT-015 ({@code aidocs/16} row 2026-05-24-015).
 */
@ApplicationScoped
public class ActivityActionKindAlarm {

  /**
   * Runs once at startup (CDI {@link PostConstruct}). Queries for any
   * {@code :Activity.actionKind} value not in {@link ActivityActionKind#ALL_KNOWN_VALUES}
   * and emits a WARN log for each rogue value found.
   *
   * <p>If the Neo4j session cannot be acquired (e.g. the database is not yet
   * reachable at {@code @PostConstruct} time) the exception is caught and
   * logged at WARN — the alarm skips rather than blocking startup.
   */
  @PostConstruct
  void checkOnStartup() {
    try {
      Session session = NeoConnector.getInstance().getNeo4jSession();
      // Build an ordered list from the known-good values for Cypher parameter.
      var knownValues = ActivityActionKind.ALL_KNOWN_VALUES.stream().sorted().toList();
      String cypher =
        "MATCH (a:Activity) " +
        "WHERE a.actionKind IS NOT NULL " +
        "  AND NOT a.actionKind IN $knownValues " +
        "RETURN DISTINCT a.actionKind AS rogueValue, count(a) AS occurrences " +
        "ORDER BY occurrences DESC";

      var result = session.query(cypher, Map.of("knownValues", knownValues));
      boolean anyRogue = false;
      for (var row : result) {
        anyRogue = true;
        Object rogueValue  = row.get("rogueValue");
        Object occurrences = row.get("occurrences");
        Log.warnf(
          "NEO-AUDIT-015 alarm: :Activity.actionKind value '%s' (%s occurrences) is not in the " +
          "canonical set %s. Investigate any code path that writes Activity rows " +
          "without going through ProvenanceService.record().",
          rogueValue, occurrences, ActivityActionKind.ALL_KNOWN_VALUES
        );
      }
      if (!anyRogue) {
        Log.debugf(
          "NEO-AUDIT-015: :Activity.actionKind values are clean — all match canonical set %s",
          ActivityActionKind.ALL_KNOWN_VALUES
        );
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "NEO-AUDIT-015 alarm: could not query :Activity.actionKind values at startup — skipping check");
    }
  }
}
