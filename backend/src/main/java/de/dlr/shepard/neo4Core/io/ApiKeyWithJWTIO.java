package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "ApiKeyWithJWT")
public class ApiKeyWithJWTIO extends ApiKeyIO {

  @Schema(readOnly = true, required = true)
  private String jwt;

  public ApiKeyWithJWTIO(ApiKey key) {
    super(key);
    this.jwt = key.getJws();
  }
}
