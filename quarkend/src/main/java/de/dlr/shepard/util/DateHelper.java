package de.dlr.shepard.util;

import jakarta.enterprise.context.RequestScoped;
import java.util.Date;
import lombok.NoArgsConstructor;

public final class DateHelper {

  public static Date getDate() {
    return new Date();
  }
}
