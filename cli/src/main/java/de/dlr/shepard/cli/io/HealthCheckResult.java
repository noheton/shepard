package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Wire-shape view of the Quarkus SmallRye-Health response served at
 * {@code /shepard/api/healthz/ready} / {@code /healthz/live}.
 *
 * <p>Quarkus emits a top-level {@code status} ({@code UP}/{@code DOWN})
 * and a {@code checks} array with one entry per registered check.
 * Each check has {@code name}, {@code status}, and an optional
 * {@code data} map. Unknown fields are tolerated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HealthCheckResult {

  private final String status;
  private final List<Check> checks;

  public HealthCheckResult(
    @JsonProperty("status") String status,
    @JsonProperty("checks") List<Check> checks
  ) {
    this.status = status == null ? "UNKNOWN" : status;
    this.checks = checks == null ? List.of() : checks;
  }

  public String getStatus() { return status; }
  public List<Check> getChecks() { return checks; }

  /** True when the top-level Quarkus health status is {@code UP}. */
  public boolean isUp() {
    return "UP".equalsIgnoreCase(status);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Check {
    private final String name;
    private final String status;
    private final Map<String, Object> data;

    public Check(
      @JsonProperty("name") String name,
      @JsonProperty("status") String status,
      @JsonProperty("data") Map<String, Object> data
    ) {
      this.name = name;
      this.status = status == null ? "UNKNOWN" : status;
      this.data = data == null ? Map.of() : data;
    }

    public String getName() { return name; }
    public String getStatus() { return status; }
    public Map<String, Object> getData() { return data; }

    public boolean isUp() {
      return "UP".equalsIgnoreCase(status);
    }
  }
}
