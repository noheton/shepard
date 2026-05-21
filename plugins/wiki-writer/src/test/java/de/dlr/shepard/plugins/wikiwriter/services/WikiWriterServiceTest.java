package de.dlr.shepard.plugins.wikiwriter.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteRequestIO;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteResponseIO;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.LlmException; // RuntimeException — no throws needed on callers
import de.dlr.shepard.spi.ai.LlmProvider;
import de.dlr.shepard.spi.ai.LlmRequest;
import de.dlr.shepard.spi.ai.LlmResponse;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WikiWriterService}.
 *
 * <p>All collaborators are mocked; no Quarkus context is required.
 */
class WikiWriterServiceTest {

  @Mock
  Instance<LlmProvider> llmProviderInstance;

  @Mock
  LlmProvider llmProvider;

  @Mock
  CollectionService collectionService;

  @Mock
  DataObjectService dataObjectService;

  @Mock
  LabJournalEntryService labJournalEntryService;

  @InjectMocks
  WikiWriterService service;

  // ── test fixtures ──────────────────────────────────────────────────────────

  private static final long COLLECTION_OGM_ID = 1L;
  private static final long DATA_OBJECT_OGM_ID = 42L;
  private static final long JOURNAL_ENTRY_ID = 99L;
  private static final String ACTIVITY_APP_ID = "01919191-0000-7000-8000-000000000001";

  private Collection collection;
  private DataObject target;
  private LabJournalEntry journalEntry;
  private LlmResponse llmResponse;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // Collection.
    collection = new Collection(COLLECTION_OGM_ID);
    collection.setName("LUMEN 2024");
    collection.setDescription("Rocket engine test campaign");
    collection.setStatus("PUBLISHED");

    // Target DataObject.
    target = new DataObject(DATA_OBJECT_OGM_ID);
    target.setName("TR-004");
    target.setDescription("Hot-fire run with turbopump anomaly at t=8s");
    target.setStatus("IN_REVIEW");
    target.setAttributes(Map.of("propellant", "LOX/LH2", "test_engineer", "jsmith"));

    collection.setDataObjects(List.of(target));

    // Journal entry stub — set the OGM id so the service can map it.
    journalEntry = new LabJournalEntry();
    journalEntry.setId(JOURNAL_ENTRY_ID);

    // LLM response.
    llmResponse = LlmResponse.builder()
      .text("## TR-004\n\nHot-fire run with turbopump anomaly.")
      .activityAppId(ACTIVITY_APP_ID)
      .inputTokens(200)
      .outputTokens(80)
      .build();
  }

  // ── isAvailable ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("isAvailable returns false when Instance is not resolvable")
  void isAvailable_notResolvable() {
    when(llmProviderInstance.isResolvable()).thenReturn(false);
    assertThat(service.isAvailable()).isFalse();
  }

  @Test
  @DisplayName("isAvailable returns false when TEXT capability not configured")
  void isAvailable_textCapabilityMissing() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(llmProvider.isAvailable(AiCapability.TEXT)).thenReturn(false);

    assertThat(service.isAvailable()).isFalse();
  }

  @Test
  @DisplayName("isAvailable returns true when provider is present and TEXT is configured")
  void isAvailable_available() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(llmProvider.isAvailable(AiCapability.TEXT)).thenReturn(true);

    assertThat(service.isAvailable()).isTrue();
  }

  // ── wikiWrite happy path ───────────────────────────────────────────────────

  @Test
  @DisplayName("wikiWrite calls LLM and creates LabJournalEntry with generated text")
  void wikiWrite_happyPath() {
    // Arrange.
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenReturn(llmResponse);
    when(labJournalEntryService.createLabJournalEntry(eq(DATA_OBJECT_OGM_ID), anyString()))
      .thenReturn(journalEntry);

    WikiWriteRequestIO request = new WikiWriteRequestIO();

    // Act.
    WikiWriteResponseIO response = service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, request);

    // Assert.
    assertThat(response.getGeneratedSummary()).isEqualTo("## TR-004\n\nHot-fire run with turbopump anomaly.");
    assertThat(response.getActivityAppId()).isEqualTo(ACTIVITY_APP_ID);
    assertThat(response.getInputTokens()).isEqualTo(200);
    assertThat(response.getOutputTokens()).isEqualTo(80);

    verify(labJournalEntryService).createLabJournalEntry(
      eq(DATA_OBJECT_OGM_ID),
      eq("## TR-004\n\nHot-fire run with turbopump anomaly.")
    );
  }

  @Test
  @DisplayName("wikiWrite with null body uses defaults")
  void wikiWrite_nullBody_usesDefaults() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenReturn(llmResponse);
    when(labJournalEntryService.createLabJournalEntry(anyLong(), anyString()))
      .thenReturn(journalEntry);

    // Act — null body should not throw.
    WikiWriteResponseIO response = service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, null);

    assertThat(response.getGeneratedSummary()).isNotBlank();
  }

  @Test
  @DisplayName("wikiWrite clamps maxTokens above upper bound to 4096")
  void wikiWrite_maxTokens_clampedToUpperBound() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenAnswer(inv -> {
      LlmRequest req = inv.getArgument(0);
      // Verify clamping.
      assertThat(req.maxTokens()).isEqualTo(4096);
      return llmResponse;
    });
    when(labJournalEntryService.createLabJournalEntry(anyLong(), anyString()))
      .thenReturn(journalEntry);

    WikiWriteRequestIO request = new WikiWriteRequestIO();
    request.setMaxTokens(99999);

    service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, request);
  }

  @Test
  @DisplayName("wikiWrite clamps maxTokens below lower bound to 128")
  void wikiWrite_maxTokens_clampedToLowerBound() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenAnswer(inv -> {
      LlmRequest req = inv.getArgument(0);
      assertThat(req.maxTokens()).isEqualTo(128);
      return llmResponse;
    });
    when(labJournalEntryService.createLabJournalEntry(anyLong(), anyString()))
      .thenReturn(journalEntry);

    WikiWriteRequestIO request = new WikiWriteRequestIO();
    request.setMaxTokens(1);

    service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, request);
  }

  @Test
  @DisplayName("wikiWrite includes extraInstruction in user instruction layer")
  void wikiWrite_extraInstruction_included() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenAnswer(inv -> {
      LlmRequest req = inv.getArgument(0);
      assertThat(req.userInstruction()).contains("Focus on anomalies.");
      return llmResponse;
    });
    when(labJournalEntryService.createLabJournalEntry(anyLong(), anyString()))
      .thenReturn(journalEntry);

    WikiWriteRequestIO request = new WikiWriteRequestIO();
    request.setExtraInstruction("Focus on anomalies.");

    service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, request);
  }

  // ── wikiWrite error paths ──────────────────────────────────────────────────

  @Test
  @DisplayName("wikiWrite propagates LlmException from provider")
  void wikiWrite_llmException_propagates() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenThrow(new LlmException("upstream timeout"));

    WikiWriteRequestIO request = new WikiWriteRequestIO();

    assertThatThrownBy(() -> service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, request))
      .isInstanceOf(LlmException.class)
      .hasMessageContaining("upstream timeout");

    // Journal entry must NOT be created when LLM fails.
    verify(labJournalEntryService, never()).createLabJournalEntry(anyLong(), anyString());
  }

  @Test
  @DisplayName("LLM request capability is TEXT")
  void wikiWrite_llmRequest_usesTextCapability() {
    when(llmProviderInstance.isResolvable()).thenReturn(true);
    when(llmProviderInstance.get()).thenReturn(llmProvider);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLLECTION_OGM_ID))
      .thenReturn(collection);
    when(dataObjectService.getDataObject(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID))
      .thenReturn(target);
    when(llmProvider.complete(any(LlmRequest.class))).thenAnswer(inv -> {
      LlmRequest req = inv.getArgument(0);
      assertThat(req.capability()).isEqualTo(AiCapability.TEXT);
      assertThat(req.pluginSystemPrompt()).contains("documentation assistant");
      assertThat(req.trustedContext()).contains("TR-004");
      assertThat(req.trustedContext()).contains("LOX/LH2");
      return llmResponse;
    });
    when(labJournalEntryService.createLabJournalEntry(anyLong(), anyString()))
      .thenReturn(journalEntry);

    service.wikiWrite(COLLECTION_OGM_ID, DATA_OBJECT_OGM_ID, new WikiWriteRequestIO());
  }
}
