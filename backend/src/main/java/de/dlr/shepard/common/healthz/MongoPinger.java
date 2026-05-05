package de.dlr.shepard.common.healthz;

import com.mongodb.client.MongoDatabase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.bson.Document;

@ApplicationScoped
public class MongoPinger implements DbPinger {

  private final DbHealthState state = new DbHealthState();

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @Override
  public String name() {
    return "mongodb";
  }

  @Override
  public DbHealthState state() {
    return state;
  }

  @Override
  public boolean ping() {
    long start = System.nanoTime();
    try {
      Document result = mongoDatabase.runCommand(new Document("ping", "1"));
      long latency = (System.nanoTime() - start) / 1_000_000L;
      if (result != null && result.containsKey("ok")) {
        state.recordSuccess(latency);
        return true;
      }
      state.recordFailure(latency, new RuntimeException("MongoDB ping returned no 'ok'"));
      return false;
    } catch (Exception e) {
      long latency = (System.nanoTime() - start) / 1_000_000L;
      state.recordFailure(latency, e);
      Log.debugf("MongoDB ping failed: %s", e.toString());
      return false;
    }
  }
}
