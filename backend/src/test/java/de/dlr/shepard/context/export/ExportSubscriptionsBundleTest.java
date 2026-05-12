package de.dlr.shepard.context.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.subscription.services.SubscriptionService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.RequestMethod;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Walker-level coverage for the R2d2 follow-up: {@code metadata.subscriptions=true} causes
 * {@link ExportService} to (a) synthesise an entity's canonical relative URL via
 * {@link EntityUrlSynthesiser}, (b) ask {@link SubscriptionService#getMatchingSubscriptionsForUrl}
 * for the subscriptions whose pattern matches that URL, and (c) hand the resulting
 * {@link SubscriptionIO} list to {@link ExportBuilder#addSubscriptionsFor}. Companion to
 * {@link ExportMetadataBundleTest} which covered the other three metadata kinds.
 */
@QuarkusComponentTest
public class ExportSubscriptionsBundleTest {

  DateHelper dateHelper = new DateHelper();

  @InjectMock
  UserService userService;

  @InjectMock
  CollectionService collectionService;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  BasicReferenceService basicReferenceService;

  @InjectMock
  TimeseriesReferenceService timeseriesReferenceService;

  @InjectMock
  FileBundleReferenceService fileReferenceService;

  @InjectMock
  StructuredDataReferenceService structuredDataReferenceService;

  @InjectMock
  URIReferenceService uriReferenceService;

  @InjectMock
  LabJournalEntryService labJournalEntryService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  VersionService versionService;

  @InjectMock
  SubscriptionService subscriptionService;

  @Inject
  ExportService service;

  private final User user = new User("bob");
  private DataObject dataObject;
  private Collection collection;

  @BeforeEach
  void initMocks() {
    dataObject = new DataObject() {
      {
        setId(25L);
        setShepardId(25L);
        setCreatedAt(dateHelper.getDate());
        setCreatedBy(user);
      }
    };
    collection = new Collection() {
      {
        setId(15L);
        setShepardId(15L);
        setCreatedAt(dateHelper.getDate());
        setCreatedBy(user);
      }
    };
    collection.addDataObject(dataObject);
    dataObject.setCollection(collection);

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(collection.getShepardId())).thenReturn(
      collection
    );
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(permissionsService.getPermissionsOfEntityOptional(anyLong())).thenReturn(Optional.empty());
    when(versionService.getAllVersions(anyLong())).thenReturn(List.of());
    when(subscriptionService.getMatchingSubscriptionsForUrl(any(), eq(RequestMethod.GET))).thenReturn(List.of());
  }

  // ---------- helpers ------------------------------------------------------

  private static Subscription stubSubscription(long id, String name, String pattern, User owner) {
    Subscription sub = new Subscription();
    sub.setId(id);
    sub.setName(name);
    sub.setSubscribedURL(pattern);
    sub.setRequestMethod(RequestMethod.GET);
    sub.setCreatedBy(owner);
    sub.setCallbackURL("http://callback.example/test");
    return sub;
  }

  // ---------- core scenarios ----------------------------------------------

  @Test
  public void subscriptionsTrue_collectionUrlMatch_emittedForCollectionOnly() throws IOException {
    Subscription colSub = stubSubscription(1L, "ColOnly", "/collections/15", user);
    when(subscriptionService.getMatchingSubscriptionsForUrl("/collections/15", RequestMethod.GET)).thenReturn(
      List.of(colSub)
    );

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, true));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();

      // Collection: one matching subscription
      ArgumentCaptor<List<SubscriptionIO>> colCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(15L), colCap.capture());
      List<SubscriptionIO> colSubs = colCap.getValue();
      assertSize(colSubs, 1);

      // DataObject: empty match -> still emits an empty subscriptions doc (idempotent layout).
      ArgumentCaptor<List<SubscriptionIO>> doCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(25L), doCap.capture());
      assertSize(doCap.getValue(), 0);
    }
  }

  @Test
  public void subscriptionsTrue_patternMatchesNoExportedUrl_notBundled() throws IOException {
    // Only a subscription targeting a different collection — no entity URL matches it.
    when(subscriptionService.getMatchingSubscriptionsForUrl("/collections/15", RequestMethod.GET)).thenReturn(
      List.of()
    );
    when(
      subscriptionService.getMatchingSubscriptionsForUrl("/collections/15/dataObjects/25", RequestMethod.GET)
    ).thenReturn(List.of());

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, true));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();

      ArgumentCaptor<List<SubscriptionIO>> colCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(15L), colCap.capture());
      assertSize(colCap.getValue(), 0);

      ArgumentCaptor<List<SubscriptionIO>> doCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(25L), doCap.capture());
      assertSize(doCap.getValue(), 0);
    }
  }

  @Test
  public void subscriptionsTrue_patternMatchesCollectionAndDataObject_appearsInBoth() throws IOException {
    Subscription wide = stubSubscription(7L, "WideMatcher", ".*", user);
    when(subscriptionService.getMatchingSubscriptionsForUrl("/collections/15", RequestMethod.GET)).thenReturn(
      List.of(wide)
    );
    when(
      subscriptionService.getMatchingSubscriptionsForUrl("/collections/15/dataObjects/25", RequestMethod.GET)
    ).thenReturn(List.of(wide));

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, true));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();

      ArgumentCaptor<List<SubscriptionIO>> colCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(15L), colCap.capture());
      assertSize(colCap.getValue(), 1);

      ArgumentCaptor<List<SubscriptionIO>> doCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(25L), doCap.capture());
      assertSize(doCap.getValue(), 1);
    }
  }

  @Test
  public void subscriptionsDefault_doesNotEmit() throws IOException {
    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addSubscriptionsFor(anyLong(), any());
    }
  }

  @Test
  public void subscriptionsFalse_doesNotEmit() throws IOException {
    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, false));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addSubscriptionsFor(anyLong(), any());
    }
  }

  @Test
  public void subscriptionsTrue_zeroSubscriptionsInDb_emitsEmptyDocPerEntity() throws IOException {
    when(subscriptionService.getMatchingSubscriptionsForUrl(any(), eq(RequestMethod.GET))).thenReturn(List.of());

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, true));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();

      ArgumentCaptor<List<SubscriptionIO>> colCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(15L), colCap.capture());
      assertSize(colCap.getValue(), 0);

      ArgumentCaptor<List<SubscriptionIO>> doCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(25L), doCap.capture());
      assertSize(doCap.getValue(), 0);
    }
  }

  @Test
  public void subscriptionsTrue_referenceUrlMatch_emittedForReference() throws IOException {
    // A FileReference attached to dataObject(25) inside collection(15).
    FileBundleReference reference = mock(FileBundleReference.class);
    when(reference.getId()).thenReturn(99L);
    when(reference.getShepardId()).thenReturn(99L);
    when(reference.getNumericId()).thenReturn(99L);
    when(reference.getCreatedBy()).thenReturn(user);
    when(reference.getCreatedAt()).thenReturn(dateHelper.getDate());
    when(reference.getDataObject()).thenReturn(dataObject);
    when(reference.getType()).thenReturn("FileReference");
    when(reference.getAnnotations()).thenReturn(new ArrayList<>());

    List<BasicReference> refs = new ArrayList<>();
    refs.add(reference);
    dataObject.setReferences(refs);
    when(fileReferenceService.getReference(15L, 25L, 99L, null)).thenReturn(reference);
    when(fileReferenceService.getAllPayloads(15L, 25L, 99L)).thenReturn(List.of());

    String fileRefUrl = "/collections/15/dataObjects/25/fileReferences/99";
    Subscription fileSub = stubSubscription(50L, "FileRefSub", fileRefUrl, user);
    when(subscriptionService.getMatchingSubscriptionsForUrl(fileRefUrl, RequestMethod.GET)).thenReturn(
      List.of(fileSub)
    );

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, true));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();

      ArgumentCaptor<List<SubscriptionIO>> refCap = listCaptor();
      verify(builder).addSubscriptionsFor(eq(99L), refCap.capture());
      assertSize(refCap.getValue(), 1);
    }
  }

  // ---------- micro helpers ------------------------------------------------

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static ArgumentCaptor<List<SubscriptionIO>> listCaptor() {
    return ArgumentCaptor.forClass((Class) List.class);
  }

  private static void assertSize(List<?> list, int expected) {
    if (list == null || list.size() != expected) {
      throw new AssertionError("expected size " + expected + " but was " + (list == null ? "null" : list.size()));
    }
  }
}
