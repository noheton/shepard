package de.dlr.shepard.integrationtests;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;

@Getter
@EqualsAndHashCode
public class ServiceHealthCheckIO {

  public ServiceHealthCheckIO(String name, Status status) {
    this.name = name;
    this.status = status;
  }

  private String name;
  private HealthCheckResponse.Status status;
}
