package de.dlr.shepard.common.healthz;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.bson.Document;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class MongoHealthCheck implements HealthCheck {

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  private boolean getMongoDBHealth() {
    Document result;
    try {
      result = mongoDatabase.runCommand(new Document("ping", "1"));
    } catch (MongoException ex) {
      return false;
    }
    return result.containsKey("ok");
  }

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("MongoDB connection health check").status(getMongoDBHealth()).build();
  }
}
