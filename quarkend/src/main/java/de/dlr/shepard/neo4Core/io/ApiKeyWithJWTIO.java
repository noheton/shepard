package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "ApiKeyWithJWT")
public class ApiKeyWithJWTIO extends ApiKeyIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String jwt;

	public ApiKeyWithJWTIO(ApiKey key) {
		super(key);
		this.jwt = key.getJws();
	}
}
