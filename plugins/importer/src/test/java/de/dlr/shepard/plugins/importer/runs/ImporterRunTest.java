package de.dlr.shepard.plugins.importer.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * IMP1a / PR-2 — entity-level smoke tests. We cover only the
 * default-constructor contract (status, createdAt, progressDone,
 * cancelRequested) because that's what
 * {@code ImporterRunService.submit(...)} relies on; full
 * round-trip-against-Postgres coverage is the integration test in
 * PR-7.
 */
final class ImporterRunTest {

  @Test
  void default_constructor_initialises_invariants() {
    var run = new ImporterRun();
    assertThat(run.getStatus()).isEqualTo(ImporterRunStatus.PENDING);
    assertThat(run.getProgressDone()).isZero();
    assertThat(run.isCancelRequested()).isFalse();
    assertThat(run.getCreatedAt())
      .as("createdAt initialised to now()")
      .isCloseTo(Instant.now(), within(2, java.time.temporal.ChronoUnit.SECONDS));
  }

  @Test
  void getters_and_setters_round_trip() {
    var run = new ImporterRun();
    var id = UUID.randomUUID();
    var now = Instant.now();
    run.setId(id);
    run.setSourceKind(ImporterSourceKind.DLR_V5_SHEPARD);
    run.setPrincipal("alice@example.com");
    run.setTargetCollectionAppId("0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506");
    run.setStatus(ImporterRunStatus.RUNNING);
    run.setCancelRequested(true);
    run.setStartedAt(now);
    run.setLastProgressAt(now.plus(Duration.ofSeconds(10)));
    run.setFinishedAt(null);
    run.setProgressTotal(100L);
    run.setProgressDone(42L);
    run.setProgressMessage("ingesting DataObjects");
    run.setErrorClass(null);
    run.setErrorMessage(null);
    run.setResultUrl(null);
    run.setResultMetadata("{\"dataObjects\":42}");
    run.setRequestPayload("{\"sourceUrl\":\"https://shepard.example.dlr.de\"}");
    run.setSourceConfig("{\"apiKey\":\"REDACTED\"}");

    assertThat(run.getId()).isEqualTo(id);
    assertThat(run.getSourceKind()).isEqualTo(ImporterSourceKind.DLR_V5_SHEPARD);
    assertThat(run.getPrincipal()).isEqualTo("alice@example.com");
    assertThat(run.getTargetCollectionAppId())
      .isEqualTo("0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506");
    assertThat(run.getStatus()).isEqualTo(ImporterRunStatus.RUNNING);
    assertThat(run.isCancelRequested()).isTrue();
    assertThat(run.getStartedAt()).isEqualTo(now);
    assertThat(run.getLastProgressAt()).isEqualTo(now.plus(Duration.ofSeconds(10)));
    assertThat(run.getFinishedAt()).isNull();
    assertThat(run.getProgressTotal()).isEqualTo(100L);
    assertThat(run.getProgressDone()).isEqualTo(42L);
    assertThat(run.getProgressMessage()).isEqualTo("ingesting DataObjects");
    assertThat(run.getResultMetadata()).isEqualTo("{\"dataObjects\":42}");
    assertThat(run.getRequestPayload())
      .isEqualTo("{\"sourceUrl\":\"https://shepard.example.dlr.de\"}");
    assertThat(run.getSourceConfig()).isEqualTo("{\"apiKey\":\"REDACTED\"}");
  }
}
