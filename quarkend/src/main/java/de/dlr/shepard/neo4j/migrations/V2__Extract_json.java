package de.dlr.shepard.neo4j.migrations;

import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.MigrationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Session;

@Slf4j
public class V2__Extract_json implements JavaBasedMigration {

  @AllArgsConstructor
  class ShepardFile {

    public final String oid;
    public final String filename;
    public final long createdAt;
    public final String md5;
  }

  @AllArgsConstructor
  class StructuredData {

    public final String oid;
    public final String name;
    public final long createdAt;
  }

  @AllArgsConstructor
  class Timeseries {

    public final String measurement;
    public final String device;
    public final String location;
    public final String symbolicName;
    public final String field;
  }

  private static final String FILES_JSON = "filesJson";
  private static final String STRUCTURED_DATAS_JSON = "structuredDatasJson";
  private static final String TIMESERIES_JSON = "timeseriesJson";
  private ObjectMapper mapper = new ObjectMapper();

  @Override
  public void apply(MigrationContext context) {
    try (Session session = context.getSession()) {
      log.info("Running migration (1/5)");
      migrateFileContainer(session);
      log.info("Running migration (2/5)");
      migrateFileReferences(session);
      log.info("Running migration (3/5)");
      migrateStructuredDataContainer(session);
      log.info("Running migration (4/5)");
      migrateStructuredDataReferences(session);
      log.info("Running migration (5/5)");
      migrateTimeseriesReferences(session);
    } catch (Exception e) {
      log.error("Error while running migration: ", e);
    }
  }

  private void migrateFileContainer(Session session) {
    var cResults = session.executeRead(tx ->
      tx.run("MATCH (c:FileContainer) WHERE c.filesJson IS NOT NULL RETURN c").list()
    );
    for (int i = 0; i < cResults.size(); i++) {
      logPercent(i, cResults.size());
      var c = cResults.get(i).get("c").asNode();
      var cId = c.elementId();

      if (!c.containsKey(FILES_JSON)) continue;

      var tx = session.beginTransaction();
      for (var fileObj : c.get(FILES_JSON).asList()) {
        if (fileObj instanceof String fileStr) {
          var fileNode = parseJson(fileStr);
          if (fileNode.isEmpty()) {
            log.error("NodeID {}: File cannot be parsed and will be skipped: {}", cId, fileStr);
            continue;
          }
          var file = parseShepardFile(fileNode.get());
          Map<String, Object> params = new HashMap<>();
          params.put(
            "props",
            Map.of("oid", file.oid, "createdAt", file.createdAt, "filename", file.filename, "md5", file.md5)
          );
          var query =
            """
            MATCH (c:FileContainer) WHERE ID(c) = %s
            CREATE (c)-[:file_in_container]->(sf:ShepardFile $props)
            """;
          tx.run(String.format(query, cId), params);
        }
      }
      tx.run("MATCH (c:FileContainer) WHERE ID(c) = " + cId + " REMOVE c.filesJson");
      tx.commit();
    }
  }

  private void migrateFileReferences(Session session) {
    var rResults = session.executeRead(tx ->
      tx.run("MATCH (r:FileReference) WHERE r.filesJson IS NOT NULL RETURN r").list()
    );
    for (int i = 0; i < rResults.size(); i++) {
      logPercent(i, rResults.size());
      var r = rResults.get(i).get("r").asNode();
      var rId = r.elementId();

      if (!r.containsKey(FILES_JSON)) continue;

      var tx = session.beginTransaction();
      for (var fileObj : r.get(FILES_JSON).asList()) {
        if (fileObj instanceof String fileStr) {
          var fileNode = parseJson(fileStr);
          if (fileNode.isEmpty()) {
            log.error("NodeID {}: File cannot be parsed and will be skipped: {}", rId, fileStr);
            continue;
          }
          var file = parseShepardFile(fileNode.get());
          Map<String, Object> params = new HashMap<>();
          params.put("oid", file.oid);
          params.put("props", Map.of("createdAt", file.createdAt, "filename", file.filename, "md5", file.md5));
          var query =
            """
            MATCH (r:FileReference)-[:is_in_container]->(c:FileContainer) WHERE ID(r) = %s
            MERGE (c)-[:file_in_container]->(sf:ShepardFile { oid: $oid })
            SET sf += $props
            CREATE (r)-[hp:has_payload]->(sf)
            """;
          tx.run(String.format(query, rId), params);
        }
      }
      tx.run("MATCH (r:FileReference) WHERE ID(r) = " + rId + " REMOVE r.filesJson");
      tx.commit();
    }
  }

  private void migrateStructuredDataContainer(Session session) {
    var cResults = session.executeRead(tx ->
      tx.run("MATCH (c:StructuredDataContainer) WHERE c.structuredDatasJson IS NOT NULL RETURN c").list()
    );
    for (int i = 0; i < cResults.size(); i++) {
      logPercent(i, cResults.size());
      var c = cResults.get(i).get("c").asNode();
      var cId = c.elementId();

      if (!c.containsKey(STRUCTURED_DATAS_JSON)) continue;

      var tx = session.beginTransaction();
      for (var structuredDataObj : c.get(STRUCTURED_DATAS_JSON).asList()) {
        if (structuredDataObj instanceof String structuredDataStr) {
          var structuredDataNode = parseJson(structuredDataStr);
          if (structuredDataNode.isEmpty()) {
            log.error("NodeID {}: StructuredData cannot be parsed and will be skipped: {}", cId, structuredDataStr);
            continue;
          }
          var sd = parseStructuredData(structuredDataNode.get());
          Map<String, Object> params = new HashMap<>();
          params.put("props", Map.of("oid", sd.oid, "createdAt", sd.createdAt, "name", sd.name));
          var query =
            """
            MATCH (c:StructuredDataContainer) WHERE ID(c) = %s
            CREATE (c)-[:structureddata_in_container]->(sd:StructuredData $props)
            """;
          tx.run(String.format(query, cId), params);
        }
      }
      tx.run("MATCH (c:StructuredDataContainer) WHERE ID(c) = " + cId + " REMOVE c.structuredDatasJson");
      tx.commit();
    }
  }

  private void migrateStructuredDataReferences(Session session) {
    var rResults = session.executeRead(tx ->
      tx.run("MATCH (r:StructuredDataReference) WHERE r.structuredDatasJson IS NOT NULL RETURN r").list()
    );
    for (int i = 0; i < rResults.size(); i++) {
      logPercent(i, rResults.size());
      var r = rResults.get(i).get("r").asNode();
      var rId = r.elementId();

      if (!r.containsKey(STRUCTURED_DATAS_JSON)) continue;

      var tx = session.beginTransaction();
      for (var structuredDataObj : r.get(STRUCTURED_DATAS_JSON).asList()) {
        if (structuredDataObj instanceof String structuredDataStr) {
          var structuredDataNode = parseJson(structuredDataStr);
          if (structuredDataNode.isEmpty()) {
            log.error("NodeID {}: StructuredData cannot be parsed and will be skipped: {}", rId, structuredDataStr);
            continue;
          }
          var sd = parseStructuredData(structuredDataNode.get());
          Map<String, Object> params = new HashMap<>();
          params.put("oid", sd.oid);
          params.put("props", Map.of("createdAt", sd.createdAt, "name", sd.name));
          var query =
            """
            MATCH (r:StructuredDataReference)-[:is_in_container]->(c:StructuredDataContainer) WHERE ID(r) = %s
            MERGE (c)-[:structureddata_in_container]->(sd:StructuredData { oid: $oid })
            SET sd += $props
            CREATE (r)-[hp:has_payload]->(sd)
            """;
          tx.run(String.format(query, rId), params);
        }
      }
      tx.run("MATCH (r:StructuredDataReference) WHERE ID(r) = " + rId + " REMOVE r.structuredDatasJson");
      tx.commit();
    }
  }

  private void migrateTimeseriesReferences(Session session) {
    var rResults = session.executeRead(tx ->
      tx.run("MATCH (r:TimeseriesReference) WHERE r.timeseriesJson IS NOT NULL RETURN r").list()
    );
    for (int i = 0; i < rResults.size(); i++) {
      logPercent(i, rResults.size());
      var r = rResults.get(i).get("r").asNode();
      var rId = r.elementId();

      if (!r.containsKey(TIMESERIES_JSON)) continue;

      var tx = session.beginTransaction();
      for (var timeseriesObj : r.get(TIMESERIES_JSON).asList()) {
        if (timeseriesObj instanceof String timeseriesStr) {
          var timeseriesNode = parseJson(timeseriesStr);
          if (timeseriesNode.isEmpty()) {
            log.error("NodeID {}: Timeseries cannot be parsed and will be skipped: {}", rId, timeseriesStr);
            continue;
          }
          var ts = parseTimeseries(timeseriesNode.get());
          Map<String, Object> params = Map.of(
            "measurement",
            ts.measurement,
            "device",
            ts.device,
            "location",
            ts.location,
            "symbolicName",
            ts.symbolicName,
            "field",
            ts.field
          );
          var query =
            """
            MATCH (r:TimeseriesReference) WHERE ID(r) = %s
            MERGE (ts:Timeseries { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field })
            CREATE (r)-[hp:has_payload]->(ts)
            """;

          tx.run(String.format(query, rId), params);
        }
      }
      tx.run("MATCH (r:TimeseriesReference) WHERE ID(r) = " + rId + " REMOVE r.timeseriesJson");
      tx.commit();
    }
  }

  private Optional<JsonNode> parseJson(String str) {
    JsonNode node;
    try {
      node = mapper.readTree(str);
    } catch (JsonProcessingException e) {
      // This should not be possible
      log.error(e.toString());
      return Optional.empty();
    }
    return Optional.of(node);
  }

  private long parseDate(String date) {
    if (date.length() == 0) return 0L;
    Date parsed;
    try {
      parsed = new StdDateFormat().parse(date);
    } catch (ParseException e) {
      // This should not be possible
      log.warn("{}, using 0 instead", e.getMessage());
      return 0L;
    }
    return parsed.getTime();
  }

  private ShepardFile parseShepardFile(JsonNode node) {
    var oid = Optional.ofNullable(node.get("oid")).map(JsonNode::asText).orElse("");
    var createdAt = Optional.ofNullable(node.get("createdAt")).map(JsonNode::asText).orElse("");
    var filename = Optional.ofNullable(node.get("filename")).map(JsonNode::asText).orElse("");
    var md5 = Optional.ofNullable(node.get("md5")).map(JsonNode::asText).orElse("");
    return new ShepardFile(oid, filename, parseDate(createdAt), md5);
  }

  private StructuredData parseStructuredData(JsonNode node) {
    var oid = Optional.ofNullable(node.get("oid")).map(JsonNode::asText).orElse("");
    var createdAt = Optional.ofNullable(node.get("createdAt")).map(JsonNode::asText).orElse("");
    var name = Optional.ofNullable(node.get("name")).map(JsonNode::asText).orElse("");
    return new StructuredData(oid, name, parseDate(createdAt));
  }

  private Timeseries parseTimeseries(JsonNode node) {
    var measurement = Optional.ofNullable(node.get("measurement")).map(JsonNode::asText).orElse("");
    var device = Optional.ofNullable(node.get("device")).map(JsonNode::asText).orElse("");
    var location = Optional.ofNullable(node.get("location")).map(JsonNode::asText).orElse("");
    var symbolicName = Optional.ofNullable(node.get("symbolicName")).map(JsonNode::asText).orElse("");
    var field = Optional.ofNullable(node.get("field")).map(JsonNode::asText).orElse("");
    return new Timeseries(measurement, device, location, symbolicName, field);
  }

  private void logPercent(int i, int size) {
    int curPercent = (int) ((100f / size) * i);
    int prePercent = (int) ((100f / size) * (i - 1));
    if (prePercent < curPercent) {
      log.info("... {} %", curPercent);
    }
  }
}
