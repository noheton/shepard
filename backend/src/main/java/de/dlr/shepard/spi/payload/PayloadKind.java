package de.dlr.shepard.spi.payload;

import java.util.List;

/**
 * SPI contract for payload kinds that contribute Neo4j-OGM entity
 * packages to the {@link de.dlr.shepard.common.neo4j.NeoConnector}
 * SessionFactory.
 *
 * <p>Each payload-kind plugin ships a
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}
 * file naming its single implementation. Implementations are plain
 * POJOs — NOT CDI beans — so that {@link java.util.ServiceLoader}
 * can discover them during {@code NeoConnector.connect()}, which
 * fires before CDI is up (it runs inside {@code ShepardMain.init()}
 * annotated {@code @Startup}).
 *
 * <p>{@link #entityPackages()} returns the fully-qualified Java
 * package names whose {@code @NodeEntity} / {@code @RelationshipEntity}
 * classes the SessionFactory must know about. Each package name is
 * passed as a positional argument to {@code new SessionFactory(...)}.
 *
 * <p>Note: feature-toggle responsibility (whether the payload kind is
 * enabled at runtime) stays with {@code FeatureToggleRegistry} and
 * the existing {@code SpatialDataFeatureToggle} / config classes.
 * Entity-package registration is a schema concern that must happen
 * regardless of toggle state — Neo4j-OGM needs the types registered
 * so that Cypher results against pre-existing nodes deserialize
 * correctly even when the REST surface is disabled.
 */
public interface PayloadKind {

  /**
   * Stable name of this payload kind, used only for log messages.
   * Example: {@code "spatial"}, {@code "hdf5"}.
   */
  String name();

  /**
   * Fully-qualified Java package names containing Neo4j-OGM
   * annotated entity classes ({@code @NodeEntity}, etc.) that must
   * be registered with the SessionFactory at startup.
   *
   * <p>Returns an empty list for payload kinds whose entities live
   * in a package already on the backend's base entity list (unlikely
   * after extraction).
   */
  List<String> entityPackages();
}
