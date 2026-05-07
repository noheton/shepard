package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class DbHealthRegistry {

  @Inject
  Instance<DbPinger> pingers;

  @Inject
  ReadinessConfig readinessConfig;

  public DbPinger pingerFor(DatabaseKind kind) {
    Map<DatabaseKind, DbPinger> byKind = byKind();
    return byKind.get(kind);
  }

  public boolean isCurrentlyDown(DatabaseKind kind) {
    DbPinger p = pingerFor(kind);
    if (p == null || !p.isRequired()) {
      return false;
    }
    return !p.state().isFreshWithin(readinessConfig.maxStalenessMs());
  }

  public long lastSuccessfulPingMs(DatabaseKind kind) {
    DbPinger p = pingerFor(kind);
    if (p == null) return 0L;
    return p.state().getLastSuccessfulPingMs();
  }

  private Map<DatabaseKind, DbPinger> byKind() {
    EnumMap<DatabaseKind, DbPinger> map = new EnumMap<>(DatabaseKind.class);
    for (DbPinger p : pingers) {
      DatabaseKind k = kindOf(p.name());
      if (k != null) {
        map.put(k, p);
      }
    }
    return map;
  }

  static DatabaseKind kindOf(String pingerName) {
    if (pingerName == null) return null;
    return switch (pingerName) {
      case "neo4j" -> DatabaseKind.NEO4J;
      case "mongodb" -> DatabaseKind.MONGO;
      case "timescaledb" -> DatabaseKind.TIMESCALE;
      case "postgis" -> DatabaseKind.SPATIAL;
      default -> null;
    };
  }
}
