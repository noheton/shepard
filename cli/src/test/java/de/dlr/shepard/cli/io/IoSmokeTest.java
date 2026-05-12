package de.dlr.shepard.cli.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Round-trip checks on the wire-shape mirror classes. If the
 * backend ever renames a JSON field or changes its type, these
 * Jackson decodes break first.
 */
final class IoSmokeTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void featureToggleDecodes() throws Exception {
    FeatureToggle ft = mapper.readValue(
      "{\"name\":\"spatial\",\"enabled\":true,\"description\":\"desc\"}",
      FeatureToggle.class
    );

    assertThat(ft.getName()).isEqualTo("spatial");
    assertThat(ft.isEnabled()).isTrue();
    assertThat(ft.getDescription()).isEqualTo("desc");
  }

  @Test
  void healthCheckResultTolerantOfMissingFields() throws Exception {
    HealthCheckResult r = mapper.readValue("{}", HealthCheckResult.class);

    assertThat(r.getStatus()).isEqualTo("UNKNOWN");
    assertThat(r.getChecks()).isEmpty();
    assertThat(r.isUp()).isFalse();
  }

  @Test
  void healthCheckUpFlag() throws Exception {
    HealthCheckResult r = mapper.readValue(
      "{\"status\":\"UP\",\"checks\":[{\"name\":\"n\",\"status\":\"up\"}]}",
      HealthCheckResult.class
    );

    assertThat(r.isUp()).isTrue();
    assertThat(r.getChecks()).hasSize(1);
    assertThat(r.getChecks().get(0).isUp()).isTrue();
    assertThat(r.getChecks().get(0).getName()).isEqualTo("n");
    assertThat(r.getChecks().get(0).getData()).isEmpty();
  }

  @Test
  void migrationProgressDecodes() throws Exception {
    List<MigrationProgress> list = mapper.readValue(
      """
      [{
        "containerId": 1,
        "rowsTotal": 100,
        "rowsMigrated": 50,
        "rowsFailed": 0,
        "lastBatchIndex": 5,
        "status": "RUNNING",
        "startedAt": "2026-05-12T10:00:00Z",
        "lastUpdateAt": "2026-05-12T10:05:00Z",
        "errors": null,
        "estimatedRemainingSeconds": 60,
        "futureField": "tolerated"
      }]
      """,
      new TypeReference<List<MigrationProgress>>() {}
    );

    assertThat(list).hasSize(1);
    MigrationProgress p = list.get(0);
    assertThat(p.getContainerId()).isEqualTo(1L);
    assertThat(p.getRowsTotal()).isEqualTo(100L);
    assertThat(p.getRowsMigrated()).isEqualTo(50L);
    assertThat(p.getRowsFailed()).isEqualTo(0L);
    assertThat(p.getLastBatchIndex()).isEqualTo(5);
    assertThat(p.getStatus()).isEqualTo("RUNNING");
    assertThat(p.getStartedAt()).isEqualTo(Instant.parse("2026-05-12T10:00:00Z"));
    assertThat(p.getLastUpdateAt()).isEqualTo(Instant.parse("2026-05-12T10:05:00Z"));
    assertThat(p.getErrors()).isNull();
    assertThat(p.getEstimatedRemainingSeconds()).isEqualTo(60L);
  }
}
