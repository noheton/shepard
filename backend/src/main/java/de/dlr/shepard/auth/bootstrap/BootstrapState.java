package de.dlr.shepard.auth.bootstrap;

import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

/**
 * Singleton-ish flag node recording an outstanding bootstrap token.
 * Created by {@link BootstrapTokenInitializer} on first start when no
 * instance-admin exists; deleted by
 * {@code POST /v2/admin/bootstrap} on consumption.
 *
 * <p>Stores only the SHA-256 of the token (not the token itself) so a
 * Neo4j dump cannot leak admin access. Designed in
 * {@code aidocs/51 §5.1}.
 */
@NodeEntity("BootstrapState")
@Data
@NoArgsConstructor
public class BootstrapState {

  @Id
  @GeneratedValue
  private Long id;

  @Property("tokenHash")
  private String tokenHash;

  @DateLong
  @Property("createdAt")
  private Date createdAt;

  public BootstrapState(String tokenHash, Date createdAt) {
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
  }
}
