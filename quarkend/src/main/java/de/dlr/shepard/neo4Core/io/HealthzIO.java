package de.dlr.shepard.neo4Core.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

enum Healthy {
  healthy,
  unhealthy,
}

@Data
@Schema(name = "Healthz")
@NoArgsConstructor
public class HealthzIO {

  private Healthy shepard;
  private Healthy neo4j;
  private Healthy mongodb;
  private Healthy influxdb;

  public HealthzIO(boolean neo4j, boolean mongodb, boolean influxdb) {
    this.shepard = Healthy.healthy;
    this.neo4j = neo4j ? Healthy.healthy : Healthy.unhealthy;
    this.mongodb = mongodb ? Healthy.healthy : Healthy.unhealthy;
    this.influxdb = influxdb ? Healthy.healthy : Healthy.unhealthy;
  }
}
