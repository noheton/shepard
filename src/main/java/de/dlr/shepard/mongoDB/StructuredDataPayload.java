package de.dlr.shepard.mongoDB;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StructuredDataPayload {

	private StructuredData structuredData;

	private String payload;

}
