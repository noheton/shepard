package de.dlr.shepard.common.subscription.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.subscription.entities.Subscription;
import jakarta.enterprise.context.RequestScoped;
import java.util.Map;

@RequestScoped
public class SubscriptionDAO extends GenericDAO<Subscription> {

  @Override
  public Class<Subscription> getEntityType() {
    return Subscription.class;
  }

  /** Returns the number of `:Subscription` nodes — 0 when the registry is empty. */
  public long countAll() {
    var result = session.query(Long.class,
        "MATCH (n:Subscription) RETURN count(n)",
        Map.of());
    var iter = result.iterator();
    return iter.hasNext() ? iter.next() : 0L;
  }
}
