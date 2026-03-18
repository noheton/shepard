package de.dlr.shepard.migrations.neo4j;

import de.dlr.shepard.common.neo4j.NeoConnector;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.PatternElement;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

class QueryHelper {

  private static Session session;

  public QueryHelper() {
    var conn = NeoConnector.getInstance();
    conn.connect();
    session = conn.getNeo4jSession();
  }

  public Result query(Statement statement) {
    return session.query(statement.getCypher(), Collections.emptyMap());
  }

  public void create(PatternElement... creatable) {
    var statement = Cypher.create(creatable).build();
    query(statement);
  }

  public List<Object> match(Node node) {
    return match(node, Object.class);
  }

  public <T> List<T> match(Node node, Class<T> type) {
    var statement = Cypher.match(node).returning(node).build();
    return queryResults(statement, type);
  }

  public <T> List<T> queryResults(Statement statement, Class<T> type) {
    return queryResults(statement.getCypher(), type);
  }

  public List<Object> queryResults(Statement statement) {
    return queryResults(statement.getCypher(), Object.class);
  }

  public List<Object> queryResults(String cypherQuery) {
    return queryResults(cypherQuery, Object.class);
  }

  public <T> List<T> queryResults(String cypherQuery, Class<T> type) {
    var result = session.query(cypherQuery, Collections.emptyMap());
    return StreamSupport.stream(result.spliterator(), false)
      .map(Map::values)
      .flatMap(java.util.Collection::stream)
      .map(type::cast)
      .toList();
  }
}
