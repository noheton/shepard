package de.dlr.shepard.data.timeseries.daos;

import static de.dlr.shepard.common.util.Constants.HAS_TIMESERIES_TUPLE;
import static de.dlr.shepard.common.util.Constants.IS_IN_CONTAINER;
import static de.dlr.shepard.common.util.Neo4jLabels.*;
import static org.neo4j.cypherdsl.core.Cypher.*;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;
import org.neo4j.cypherdsl.core.Condition;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

@RequestScoped
public class TimeseriesDAO extends GenericDAO<Timeseries> {

  @Override
  public Class<Timeseries> getEntityType() {
    return Timeseries.class;
  }

  public Stream<Timeseries> getAllTimeseriesInContainer(long containerId) {
    var tsc = node(TIMESERIES_CONTAINER);
    var ts = node(TIMESERIES);
    var tsTuple = node(TIMESERIES_TUPLE);
    var isInContainer = ts.relationshipTo(tsc, IS_IN_CONTAINER);
    var hasTuple = ts.relationshipTo(tsTuple, HAS_TIMESERIES_TUPLE);
    var query = match(isInContainer, hasTuple)
      .where(internalIdIs(tsc, containerId).and(notDeleted(ts)))
      .returning(ts, tsc, tsTuple)
      .build()
      .getCypher();
    return this.findByQuery(query);
  }

  public long getCurrentMaximumTimeseriesId() {
    var ts = node(TIMESERIES);
    var query = match(ts)
      .returning(ts.property("timeseriesId"))
      .orderBy(ts.property("timeseriesId").descending())
      .limit(1)
      .build()
      .getCypher();
    try {
      return session.query(Long.class, query, Collections.emptyMap()).iterator().next();
    } catch (NoSuchElementException e) {
      // If no Timeseries is found we can assume a "fresh" database and the timeseries IDs can start anew.
      return 0;
    }
  }

  public Optional<Timeseries> findTimeseries(long containerId, TimeseriesTuple tsTuple) {
    var tsTupleNode = node(TIMESERIES_TUPLE).withProperties(
      "measurement",
      Cypher.literalOf(tsTuple.getMeasurement()),
      "device",
      Cypher.literalOf(tsTuple.getDevice()),
      "location",
      Cypher.literalOf(tsTuple.getLocation()),
      "symbolicName",
      Cypher.literalOf(tsTuple.getSymbolicName()),
      "field",
      Cypher.literalOf(tsTuple.getField())
    );
    var tsc = node(TIMESERIES_CONTAINER);
    var ts = node(TIMESERIES);
    var query = match(tsTupleNode.relationshipFrom(ts, HAS_TIMESERIES_TUPLE).relationshipTo(tsc, IS_IN_CONTAINER))
      .where(internalIdIs(tsc, containerId).and(notDeleted(ts)))
      .returning(tsTupleNode, tsc, ts)
      .build()
      .getCypher();

    return this.findByQuery(query).findFirst();
  }

  public Optional<Timeseries> findByTimeseriesId(long timeseriesId) {
    var ts = node(TIMESERIES).withProperties("timeseriesId", Cypher.literalOf(timeseriesId));
    var query = match(ts).where(notDeleted(ts)).returning(ts).build().getCypher();
    return this.findByQuery(query).findFirst();
  }

  private static Condition notDeleted(Node node) {
    return node.property("deleted").isNull().or(node.property("deleted").ne(Cypher.literalOf(true)));
  }

  private static Condition internalIdIs(Node node, long id) {
    return node.internalId().eq(Cypher.literalOf(id));
  }

  public void deleteAllTimeseriesInContainer(long containerId) {
    var ts = node(TIMESERIES);
    var tsc = node(TIMESERIES_CONTAINER);
    var query = match(ts.relationshipTo(tsc, IS_IN_CONTAINER))
      .where(internalIdIs(tsc, containerId))
      .set(ts.property("deleted"), Cypher.literalOf(true))
      .build()
      .getCypher();
    session.query(query, Collections.emptyMap());
  }
}
