package de.dlr.shepard.auth.security;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApiKeyLastSeenCache extends LastSeenCache {

  private static final int FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

  ApiKeyLastSeenCache() {
    super(FIVE_MINUTES_IN_MILLIS);
  }
}
