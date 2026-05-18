package de.dlr.shepard.v2.watches.entities;

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
 * WATCH1 — a single "Collection X watches Container Y" link.
 *
 * <p>Modelled as a {@code :Watch} node rather than a plain Neo4j
 * relationship so the watch itself has an addressable {@code appId}
 * (clean REST DELETE), and additional fields (timestamps, audit
 * trail) can be added later without a schema migration.
 *
 * <p>Graph shape:
 * <pre>
 *   (Collection) -[:has_watch]-> (Watch) -[:watches]-> (Container)
 * </pre>
 *
 * <p>{@link #containerKind} is the closed enum {@code TIMESERIES /
 * FILE / STRUCTURED_DATA}; the REST layer uses it to route the
 * Container fetch to the right service. The {@link #containerAppId}
 * is the application-level id of the watched Container (not the
 * OGM long id, so the link survives any future id-shape changes).
 *
 * <p>Constraint: {@code V48__Add_appId_constraint_Watch.cypher} adds
 * {@code REQUIRE n.appId IS UNIQUE} on {@code :Watch}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Watch implements HasAppId {

  /**
   * Closed enum of container kinds a Collection can watch.
   * Stored as a string property on the node.
   */
  public enum Kind {
    TIMESERIES,
    FILE,
    STRUCTURED_DATA,
  }

  @Id
  @GeneratedValue
  private Long id;

  /** UUID v7 minted on save by {@code GenericDAO.createOrUpdate}. */
  @Property("appId")
  private String appId;

  /** AppId of the watching :Collection. */
  @Property("collectionAppId")
  private String collectionAppId;

  /** AppId of the watched container. */
  @Property("containerAppId")
  private String containerAppId;

  /** Kind of the watched container — TIMESERIES / FILE / STRUCTURED_DATA. */
  @Property("containerKind")
  private Kind containerKind;

  /** Millis-epoch the watch was created. */
  @Property("since")
  private Long since;

  /** Username of the user who added the watch. */
  @Property("addedBy")
  private String addedBy;

  /** Testing helper. */
  public Watch(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Watch other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
