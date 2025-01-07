package de.dlr.shepard.common.healthz;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxDBConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class InfluxDBHealthCheck implements HealthCheck {

  private InfluxDBConnector influxdb;

  @Inject
  public InfluxDBHealthCheck(InfluxDBConnector influxdb) {
    this.influxdb = influxdb;
  }

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("InfluxDB connection health check").status(influxdb.alive()).build();
  }
}
