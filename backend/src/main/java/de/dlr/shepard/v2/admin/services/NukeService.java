package de.dlr.shepard.v2.admin.services;

import com.mongodb.client.MongoDatabase;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.v2.admin.io.NukeResultIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.neo4j.ogm.session.Session;

/**
 * Nuclear instance-reset service — wipes all research data while preserving
 * users, API keys, and instance configuration.
 *
 * <p>Deletion order:
 * <ol>
 *   <li>Collect MongoDB collection identifiers from Neo4j (before any deletes).</li>
 *   <li>Drop those MongoDB collections (file + structured-data payloads).</li>
 *   <li>Delete all timeseries channel records from Postgres.</li>
 *   <li>Delete all Activity nodes from Neo4j.</li>
 *   <li>DETACH DELETE all data nodes from Neo4j, preserving identity/config nodes.</li>
 * </ol>
 *
 * <p>Preserved node labels: {@code User}, {@code UserGroup}, {@code ApiKey},
 * {@code InstanceAdminGrant}, {@code InstanceRorConfig},
 * {@code SemanticRepository}, {@code OntologyConfig}, {@code SemanticConfig},
 * {@code FeatureToggle}, {@code SqlTimeseriesConfig}.
 */
@ApplicationScoped
public class NukeService {

  /** The exact phrase the caller must supply to confirm the destructive reset. */
  public static final String CONFIRM_PHRASE = "yes drop everything";

  /** Labels whose nodes survive the nuclear reset. */
  private static final List<String> PRESERVED_LABELS = List.of(
    "User", "UserGroup", "ApiKey",
    "InstanceAdminGrant", "InstanceRorConfig",
    "SemanticRepository", "OntologyConfig", "SemanticConfig",
    "FeatureToggle", "SqlTimeseriesConfig"
  );

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @Inject
  TimeseriesRepository timeseriesRepository;

  public boolean confirmPhraseValid(String phrase) {
    return CONFIRM_PHRASE.equals(phrase);
  }

  @Transactional
  public NukeResultIO nuke() {
    Log.warn("NUKE: instance data reset initiated by instance-admin");

    Session session = NeoConnector.getInstance().getNeo4jSession();

    // 1. Collect MongoDB collection ids before any Neo4j deletes.
    List<String> mongoIds = collectMongoIds(session);

    // 2. Drop each MongoDB collection (file + structured-data payloads).
    int droppedMongo = dropMongoCollections(mongoIds);

    // 3. Wipe timeseries channel records from Postgres.
    long deletedTs = timeseriesRepository.deleteAll();

    // 4. Delete Activity nodes separately so we can count them.
    long deletedActivities = deleteActivities(session);

    // 5. DETACH DELETE all remaining data nodes from Neo4j.
    long deletedNodes = deleteDataNodes(session);

    Log.warnf(
      "NUKE complete: neo4jNodes=%d, mongoCollections=%d, tsRecords=%d, activities=%d",
      deletedNodes, droppedMongo, deletedTs, deletedActivities
    );

    return new NukeResultIO(
      deletedNodes,
      droppedMongo,
      deletedTs,
      deletedActivities,
      "Instance data reset complete. Preserved: " + PRESERVED_LABELS + "."
    );
  }

  private List<String> collectMongoIds(Session session) {
    String cypher = "MATCH (n) WHERE n.mongoId IS NOT NULL RETURN DISTINCT n.mongoId AS mongoId";
    List<String> ids = new ArrayList<>();
    for (var row : session.query(cypher, Collections.emptyMap()).queryResults()) {
      Object id = row.get("mongoId");
      if (id != null && !id.toString().isBlank()) ids.add(id.toString());
    }
    Log.infof("NUKE: found %d MongoDB collection(s) to drop", ids.size());
    return ids;
  }

  private int dropMongoCollections(List<String> mongoIds) {
    int count = 0;
    for (String mongoId : mongoIds) {
      try {
        mongoDatabase.getCollection(mongoId).drop();
        count++;
      } catch (Exception e) {
        Log.warnf("NUKE: failed to drop MongoDB collection '%s': %s", mongoId, e.getMessage());
      }
    }
    return count;
  }

  private long deleteActivities(Session session) {
    String cypher =
      "MATCH (a:Activity) WITH count(a) AS n, collect(a) AS nodes " +
      "FOREACH (a IN nodes | DETACH DELETE a) " +
      "RETURN n";
    long total = 0L;
    for (var row : session.query(cypher, Collections.emptyMap()).queryResults()) {
      Object n = row.get("n");
      if (n instanceof Number num) total += num.longValue();
    }
    return total;
  }

  private long deleteDataNodes(Session session) {
    // Build the label exclusion list for Cypher.
    String labelList = String.join(", ",
      PRESERVED_LABELS.stream().map(l -> "'" + l + "'").toList()
    );
    String cypher =
      "MATCH (n) " +
      "WHERE NOT any(l IN labels(n) WHERE l IN [" + labelList + "]) " +
      "WITH count(n) AS total, collect(n) AS nodes " +
      "FOREACH (n IN nodes | DETACH DELETE n) " +
      "RETURN total";
    long total = 0L;
    for (var row : session.query(cypher, Collections.emptyMap()).queryResults()) {
      Object t = row.get("total");
      if (t instanceof Number num) total += num.longValue();
    }
    return total;
  }
}
