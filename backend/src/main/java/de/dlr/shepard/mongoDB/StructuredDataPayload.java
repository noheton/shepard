package de.dlr.shepard.mongoDB;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StructuredDataPayload {

  private StructuredData structuredData;

  @Size(min = 2)
  private String payload;
}
