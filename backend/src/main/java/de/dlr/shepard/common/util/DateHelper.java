package de.dlr.shepard.common.util;

import jakarta.enterprise.context.RequestScoped;
import java.util.Date;
import lombok.NoArgsConstructor;

@RequestScoped
@NoArgsConstructor
public class DateHelper {

  public Date getDate() {
    return new Date();
  }
}
