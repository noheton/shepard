package de.dlr.shepard.security;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PermissionGracePeriod extends GracePeriodUtil {

  private static final int FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

  public PermissionGracePeriod() {
    super(FIVE_MINUTES_IN_MILLIS);
  }
}
