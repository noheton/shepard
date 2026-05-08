package de.dlr.shepard.auth.bootstrap;

import de.dlr.shepard.common.neo4j.NeoConnector;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;

/**
 * Application-scoped DAO for the {@link BootstrapState} flag node.
 *
 * <p>Application-scoped (not request-scoped) because the bootstrap
 * lifecycle runs at startup before any request context exists.
 */
@ApplicationScoped
public class BootstrapStateDAO {

  public Optional<BootstrapState> findOne() {
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return Optional.empty();
    var iter = session.query(BootstrapState.class, "MATCH (b:BootstrapState) RETURN b LIMIT 1", Map.of());
    var it = iter.iterator();
    if (!it.hasNext()) return Optional.empty();
    return Optional.of(it.next());
  }

  public BootstrapState save(BootstrapState state) {
    var session = NeoConnector.getInstance().getNeo4jSession();
    session.save(state);
    return state;
  }

  public void deleteAll() {
    var session = NeoConnector.getInstance().getNeo4jSession();
    session.query("MATCH (b:BootstrapState) DETACH DELETE b", Map.of());
  }
}
