package de.dlr.shepard.common.subscription.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.subscription.entities.Subscription;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class SubscriptionDAO extends GenericDAO<Subscription> {

  @Override
  public Class<Subscription> getEntityType() {
    return Subscription.class;
  }
}
