package de.dlr.shepard.util;

import jakarta.enterprise.context.RequestScoped;
import java.util.Date;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@RequestScoped
public class DateHelper {

  // TODO: convert to static function
  public static Date getDate() {
    return new Date();
  }
}
