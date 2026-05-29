package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collection;
import org.neo4j.ogm.session.Session;

/**
 * N1c2 — DAO for the single-instance {@link SemanticConfig} node.
 *
 * <p>The singleton invariant is service-level — {@code OntologyConfigService}
 * is the only caller, and it routes every read through
 * {@link #findFirst()}. Multiple rows on disk would be a bug; in
 * that case the service uses the first one and logs a WARN, but
 * does not raise (defence-in-depth).
 *
 * <p>Although nominally {@code @RequestScoped}, this DAO is also
 * instantiated directly by {@code OntologySeedService.productionConfigService()}
 * at startup (before the {@code SessionFactory} has finished
 * booting), so the cached {@code this.session} field can be {@code null}.
 * To match the {@code JupyterConfigDAO} fix
 * (TS-INGEST-222GB-CHOKE-03), {@link #findFirst} and
 * {@link #createOrUpdate} both refetch a live session from
 * {@link NeoConnector} on every call.
 */
@RequestScoped
public class SemanticConfigDAO extends GenericDAO<SemanticConfig> {

  /**
   * Find the singleton {@link SemanticConfig}. Returns {@code null}
   * when no row exists yet (first-start case — the service then
   * creates one seeded from the deploy-time defaults).
   *
   * <p>Refetches the OGM session on every call so that admin reads
   * succeed even when the bean was constructed before the
   * {@code SessionFactory} finished booting (a known startup-ordering
   * race when the bean's {@code GenericDAO} constructor cached a
   * {@code null} session). Mirrors the {@code JupyterConfigDAO} fix
   * shipped as part of TS-INGEST-222GB-CHOKE-03.
   */
  public SemanticConfig findFirst() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    Collection<SemanticConfig> all = live.loadAll(SemanticConfig.class, DEPTH_ENTITY);
    if (all == null || all.isEmpty()) return null;
    return all.iterator().next();
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session for the
   * same reason {@link #findFirst} does — the cached {@code this.session}
   * inherited from {@code GenericDAO} can be null when the bean was
   * constructed before the {@code SessionFactory} finished booting.
   *
   * <p>Mints an appId via the parent's path if absent, then saves.
   */
  @Override
  public SemanticConfig createOrUpdate(SemanticConfig entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :SemanticConfig");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(de.dlr.shepard.common.identifier.AppIdGenerator.next());
    }
    live.save(entity, DEPTH_ENTITY);
    return entity;
  }

  @Override
  public Class<SemanticConfig> getEntityType() {
    return SemanticConfig.class;
  }
}
