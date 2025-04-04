package de.dlr.shepard.auth.security;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserLastSeenCache extends LastSeenCache {

  private static final int THIRTY_MINUTES_IN_MILLIS = 30 * 60 * 1000;

  public UserLastSeenCache() {
    super(THIRTY_MINUTES_IN_MILLIS);
  }
}
