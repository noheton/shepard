package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.Subscription;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class SubscriptionDAO extends GenericDAO<Subscription> {

  @Override
  public Class<Subscription> getEntityType() {
    return Subscription.class;
  }
}
