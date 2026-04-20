package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.cypherdsl.core.Cypher.node;

import org.neo4j.cypherdsl.core.Cypher;

public class TestV13 extends MigrationTest {

  @Override
  void setupPreMigrationData() {
    var ts = node("Timeseries").withProperties("timeseriesId", Cypher.literalOf(999));
    var annotatedTs = node("AnnotatableTimeseries").withProperties("timeseriesId", Cypher.literalOf(999));
    var annotation = sample.annotation();

    q.create(annotatedTs.relationshipTo(annotation, "has_annotation"));
    q.create(ts);
  }

  @Override
  String getTargetVersion() {
    return "V13";
  }

  public void assertAnnotatedTimeseriesMigrated() {
    var ts = node("Timeseries").withProperties("timeseriesId", Cypher.literalOf(999));
    var annotation = sample.annotation();
    var query = Cypher.match(ts.relationshipTo(annotation, "has_annotation")).returning(ts).build();
    assertEquals(1, q.queryResults(query).size());
  }

  public void assertLegacyAnnotatedTimeseriesDeleted() {
    var ts = node("AnnotatableTimeseries");
    assertEquals(0, q.match(ts).size());
  }
}
