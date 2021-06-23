package de.dlr.shepard.mongoDB;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StructuredDataPayload {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private StructuredData structuredData;

	private String payload;

}
