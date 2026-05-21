package de.dlr.shepard.v2.collectionwatchers.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * CW1 — a single "User watches Collection" link.
 *
 * <p>Modelled as a {@code :CollectionWatcher} node (rather than a plain
 * Neo4j relationship) so the record has an addressable {@code appId}
 * and can carry additional fields (timestamps, audit trail) without a
 * schema migration.
 *
 * <p>Graph shape:
 * <pre>
 *   (:CollectionWatcher { username, collectionAppId, since })
 * </pre>
 *
 * <p>The (username, collectionAppId) pair is the deduplication key — the
 * service enforces uniqueness at create time so a user can't watch the
 * same collection twice.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CollectionWatcher implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** UUID v7 minted on save by {@code GenericDAO.createOrUpdate}. */
  @Property("appId")
  private String appId;

  /** Username of the watching user. */
  @Property("username")
  private String username;

  /** AppId of the watched Collection. */
  @Property("collectionAppId")
  private String collectionAppId;

  /** Epoch millis when the watch was created. */
  @Property("since")
  private Long since;

  /** Testing helper. */
  public CollectionWatcher(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CollectionWatcher other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
