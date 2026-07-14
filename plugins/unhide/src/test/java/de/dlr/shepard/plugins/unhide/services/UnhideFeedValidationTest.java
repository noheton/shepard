package de.dlr.shepard.plugins.unhide.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.plugins.unhide.io.FeedEntryIO;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import de.dlr.shepard.plugins.unhide.io.UnhideValidationReportIO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UH1e — unit tests for {@link UnhideFeedService#validateFeed(FeedIO)}.
 *
 * <p>These tests exercise the structural-validation logic in isolation.
 * Auth-gate tests (validate=true still blocked by disabled/private feed)
 * live in {@link de.dlr.shepard.plugins.unhide.resources.UnhideFeedValidationRestTest}.
 */
class UnhideFeedValidationTest {

  private UnhideFeedService service;

  @BeforeEach
  void setUp() {
    // validateFeed has no DAO dependencies; create a bare instance.
    service = new UnhideFeedService();
  }

  // ─── empty graph ────────────────────────────────────────────────────────

  @Test
  void emptyGraph_isValid() {
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertTrue(report.valid(), "empty graph should be valid");
    assertEquals(0, report.errorCount());
    assertTrue(report.errors().isEmpty());
  }

  @Test
  void nullGraph_isValid() {
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), null, Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertTrue(report.valid(), "null graph should be valid (treated as empty)");
    assertEquals(0, report.errorCount());
  }

  // ─── fully valid entry ───────────────────────────────────────────────────

  @Test
  void validEntry_returnsNoErrors() {
    FeedEntryIO entry = entryWith(
      "https://shepard.example.dlr.de/v2/collections/abc",
      "My Dataset",
      "A description of the dataset."
    );
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(entry), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertTrue(report.valid());
    assertEquals(0, report.errorCount());
    assertTrue(report.errors().isEmpty());
  }

  // ─── missing @id ────────────────────────────────────────────────────────

  @Test
  void missingId_returnsError() {
    FeedEntryIO entry = entryWith(null, "My Dataset", "A description.");
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(entry), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertFalse(report.valid());
    assertEquals(1, report.errorCount());
    assertTrue(report.errors().get(0).contains("@id"), "error should mention @id");
    assertTrue(report.errors().get(0).contains("[index=0]"), "error should include entry index");
  }

  @Test
  void blankId_returnsError() {
    FeedEntryIO entry = entryWith("   ", "My Dataset", "A description.");
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(entry), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertFalse(report.valid());
    assertEquals(1, report.errorCount());
  }

  // ─── missing name ────────────────────────────────────────────────────────

  @Test
  void missingName_returnsError() {
    FeedEntryIO entry = entryWith("https://shepard.example.dlr.de/v2/collections/abc", null, "A description.");
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(entry), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertFalse(report.valid());
    assertEquals(1, report.errorCount());
    assertTrue(report.errors().get(0).contains("name"), "error should mention name");
  }

  // ─── missing description ─────────────────────────────────────────────────

  @Test
  void missingDescription_returnsError() {
    FeedEntryIO entry = entryWith("https://shepard.example.dlr.de/v2/collections/abc", "My Dataset", null);
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(entry), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertFalse(report.valid());
    assertEquals(1, report.errorCount());
    assertTrue(report.errors().get(0).contains("description"), "error should mention description");
  }

  // ─── multiple entries / multiple errors ─────────────────────────────────

  @Test
  void multipleEntries_allValid_isValid() {
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(
      entryWith("https://shepard.example.dlr.de/v2/collections/a1", "Dataset A", "Desc A"),
      entryWith("https://shepard.example.dlr.de/v2/collections/a2", "Dataset B", "Desc B")
    ), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertTrue(report.valid());
    assertEquals(0, report.errorCount());
  }

  @Test
  void multipleEntries_oneInvalid_reportsCorrectIndex() {
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(
      entryWith("https://shepard.example.dlr.de/v2/collections/a1", "Dataset A", "Desc A"),
      entryWith(null, "Dataset B", "Desc B")  // missing @id at index 1
    ), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertFalse(report.valid());
    assertEquals(1, report.errorCount());
    assertTrue(report.errors().get(0).contains("[index=1]"), "error must cite index 1");
  }

  @Test
  void entryMissingBothIdAndName_reportsTwoErrors() {
    FeedEntryIO entry = entryWith(null, null, "A description.");
    FeedIO feed = new FeedIO(FeedIO.defaultContext(), List.of(entry), Map.of());
    UnhideValidationReportIO report = service.validateFeed(feed);
    assertFalse(report.valid());
    assertEquals(2, report.errorCount(), "both @id and name should produce separate errors");
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static FeedEntryIO entryWith(String id, String name, String description) {
    return new FeedEntryIO(
      id,
      List.of("schema:Dataset"),
      name,
      description,
      "2026-01-01T00:00:00Z",
      "2026-01-01T00:00:00Z",
      null, // license
      null, // creator
      null, // schemaIdentifier
      null, // schemaUrl
      null, // m4iHasIdentifier
      null  // hasProcessingStep
    );
  }
}
