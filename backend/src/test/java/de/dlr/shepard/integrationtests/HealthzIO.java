package de.dlr.shepard.integrationtests;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;

@Getter
@EqualsAndHashCode
public class HealthzIO {

  private HealthCheckResponse.Status status;
  private List<ServiceHealthCheckIO> checks;

  public HealthzIO(Status status, List<ServiceHealthCheckIO> checks) {
    this.status = status;
    this.checks = checks;
  }

  /**
   * Helper to create test cases
   */
  public static HealthzIO createInstanceWithCheckedServices(
    HealthCheckResponse.Status status,
    List<String> checkedServices
  ) {
    return new HealthzIO(
      status,
      checkedServices
        .stream()
        .map(checkedService ->
          new ServiceHealthCheckIO(checkedService + " connection health check", HealthCheckResponse.Status.UP)
        )
        .toList()
    );
  }
}
