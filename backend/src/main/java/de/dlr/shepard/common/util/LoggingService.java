package de.dlr.shepard.common.util;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

@RequestScoped
public class LoggingService {

  @Context
  private SecurityContext securityContext;

  /**
   * Log an unsuccessful attempt by a user to do something.
   * @param action Brief action description of what the user tried to do.
   *               Must be written in infinitive without trailing punctuation.
   *               The log will be formatted according to "User {username} tried to {action}!"
   */
  public void logUnsuccessfulAttempt(String action) {
    Log.error("User " + securityContext.getUserPrincipal() + " tried to " + action + "!");
  }
}
