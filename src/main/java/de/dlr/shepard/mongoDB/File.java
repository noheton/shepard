package de.dlr.shepard.mongoDB;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class File {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String oid;
	@Schema(accessMode = AccessMode.READ_ONLY)
	private String filename;

}
