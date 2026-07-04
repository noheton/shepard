package de.dlr.shepard.common.neo4j.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2-only base for all /v2/ entity IO classes that must suppress the
 * legacy numeric {@code id} (Neo4j internal node-id) from the wire shape.
 *
 * <p>{@code id} is a Neo4j internal identifier — an implementation detail
 * of the storage substrate. The /v2/ surface addresses all entities by
 * {@code appId} (UUID v7) exclusively. Exposing the Neo4j node-id in /v2/
 * responses would leak the substrate and couple clients to an id type that
 * can change on re-import or shard migration.
 *
 * <p>The v1 {@code /shepard/api/} surface is unaffected — {@link BasicEntityIO}
 * is not touched.
 *
 * <p>Jackson resolves {@code @JsonIgnoreProperties} by walking the class
 * hierarchy and merging annotations, so every concrete subclass of this
 * class (and of its subclasses) inherits the {@code id} suppression without
 * further annotation.
 */
@JsonIgnoreProperties({"id"})
@NoArgsConstructor
@Schema(name = "BasicEntityV2")
public abstract class BasicEntityV2IO extends BasicEntityIO {

  public BasicEntityV2IO(BasicEntity entity) {
    super(entity);
  }

  public BasicEntityV2IO(BasicEntityIO entity) {
    super(entity);
  }

  public BasicEntityV2IO(VersionableEntity entity) {
    super(entity);
  }

  /** Route the HasId contract through appId (the stable v2 address). */
  @Override
  public String getUniqueId() {
    return getAppId();
  }
}
