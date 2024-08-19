package de.dlr.shepard.util;

import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;
import lombok.NoArgsConstructor;

@RequestScoped
@NoArgsConstructor
public class UUIDHelper {

  public UUID getUUID() {
    return UUID.randomUUID();
  }
}
