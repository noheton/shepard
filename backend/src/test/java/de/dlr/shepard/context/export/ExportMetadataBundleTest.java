package de.dlr.shepard.context.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.io.VersionIO;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ExportMetadataBundleTest {

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
  FileReferenceService fileReferenceService;

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
  }

  private <T extends BasicReference> T hydrateReferenceMock(T reference, String type, long shepardId) {
    when(reference.getId()).thenReturn(shepardId);
    when(reference.getShepardId()).thenReturn(shepardId);
    when(reference.getNumericId()).thenReturn(shepardId);
    when(reference.getCreatedBy()).thenReturn(user);
    when(reference.getCreatedAt()).thenReturn(dateHelper.getDate());
    when(reference.getDataObject()).thenReturn(dataObject);
    when(reference.getType()).thenReturn(type);
    when(reference.getAnnotations()).thenReturn(new ArrayList<>());
    return reference;
  }

  // ---------- permissions --------------------------------------------------

  @Test
  public void permissionsTrue_emitsPermissionsForCollectionAndDataObject() throws IOException {
    var perms = stubPermissions(15L);
    when(permissionsService.getPermissionsOfEntityOptional(15L)).thenReturn(Optional.of(perms));
    when(permissionsService.getPermissionsOfEntityOptional(25L)).thenReturn(Optional.of(stubPermissions(25L)));

    var sel = new ExportSelection(null, new ExportSelection.Metadata(true, null, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, atLeastOnce()).addPermissionsFor(eq(15L), any(PermissionsIO.class));
      verify(builder, atLeastOnce()).addPermissionsFor(eq(25L), any(PermissionsIO.class));
    }
  }

  @Test
  public void permissionsAbsent_butIncluded_emitsEmptyPermissionsDoc() throws IOException {
    when(permissionsService.getPermissionsOfEntityOptional(anyLong())).thenReturn(Optional.empty());

    var sel = new ExportSelection(null, new ExportSelection.Metadata(true, null, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      // null PermissionsIO is the contract for "no permissions on this entity"
      verify(builder).addPermissionsFor(15L, null);
      verify(builder).addPermissionsFor(25L, null);
    }
  }

  @Test
  public void permissionsDefault_doesNotEmit() throws IOException {
    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addPermissionsFor(anyLong(), any());
    }
  }

  @Test
  public void permissionsNullSelection_legacyDoesNotEmit() throws IOException {
    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId());
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addPermissionsFor(anyLong(), any());
    }
  }

  // ---------- versions -----------------------------------------------------

  @Test
  public void versionsTrue_emitsForCollectionOnly() throws IOException {
    var v = new Version();
    v.setUid(UUID.randomUUID());
    v.setName("v1");
    v.setHEADVersion(true);
    v.setCreatedAt(dateHelper.getDate());
    v.setCreatedBy(user);
    when(versionService.getAllVersions(15L)).thenReturn(List.of(v));

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, true, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder).addVersionsFor(eq(15L), any());
      verify(builder, never()).addVersionsFor(eq(25L), any());
    }
  }

  @Test
  public void versionsDefault_doesNotEmit() throws IOException {
    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addVersionsFor(anyLong(), any());
    }
  }

  // ---------- annotations --------------------------------------------------

  @Test
  public void annotationsTrue_emitsPerEntityFromEntityAnnotations() throws IOException {
    var ann = new SemanticAnnotation();
    ann.setId(101L);
    ann.setPropertyIRI("http://example.org/p");
    ann.setValueIRI("http://example.org/v");
    collection.addAnnotation(ann);
    dataObject.addAnnotation(ann);

    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, true, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder).addAnnotationsFor(eq(15L), any());
      verify(builder).addAnnotationsFor(eq(25L), any());
    }
  }

  @Test
  public void annotationsDefault_doesNotEmit() throws IOException {
    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, null));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder, never()).addAnnotationsFor(anyLong(), any());
    }
  }

  // ---------- subscriptions -----------------------------------------------

  @Test
  public void subscriptionsTrue_doesNotEmitDocsButPasses() throws IOException {
    // Subscriptions are URL-pattern based; per-entity emission is deferred. Setting the boolean
    // to true must not crash the export and must leave the subscription document API untouched.
    var sel = new ExportSelection(null, new ExportSelection.Metadata(null, null, null, null, true));

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

  // ---------- combined -----------------------------------------------------

  @Test
  public void allMetadataTrue_emitsAllKindsForCollection() throws IOException {
    when(permissionsService.getPermissionsOfEntityOptional(15L)).thenReturn(Optional.of(stubPermissions(15L)));
    when(permissionsService.getPermissionsOfEntityOptional(25L)).thenReturn(Optional.of(stubPermissions(25L)));
    var v = new Version();
    v.setUid(UUID.randomUUID());
    v.setName("v1");
    v.setHEADVersion(true);
    v.setCreatedAt(dateHelper.getDate());
    v.setCreatedBy(user);
    when(versionService.getAllVersions(15L)).thenReturn(List.of(v));

    var sel = new ExportSelection(null, new ExportSelection.Metadata(true, true, true, true, true));

    var mockStream = mock(InputStream.class);
    try (
      var ctrl = mockConstruction(ExportBuilder.class, (m, ctx) -> {
        when(m.build()).thenReturn(mockStream);
      })
    ) {
      service.exportCollectionByShepardId(collection.getShepardId(), sel);
      var builder = ctrl.constructed().getFirst();
      verify(builder).addPermissionsFor(eq(15L), any(PermissionsIO.class));
      verify(builder).addAnnotationsFor(eq(15L), any());
      verify(builder).addVersionsFor(eq(15L), any());
      verify(builder).addPermissionsFor(eq(25L), any(PermissionsIO.class));
      verify(builder).addAnnotationsFor(eq(25L), any());
    }
  }

  // ---------- helpers ------------------------------------------------------

  private Permissions stubPermissions(long entityId) {
    var entity = new DataObject() {
      {
        setId(entityId);
        setShepardId(entityId);
      }
    };
    var permissions = new Permissions();
    permissions.setOwner(user);
    permissions.setPermissionType(PermissionType.Private);
    permissions.setEntities(new ArrayList<>(List.of(entity)));
    permissions.setReader(new ArrayList<>());
    permissions.setWriter(new ArrayList<>());
    permissions.setManager(new ArrayList<>());
    permissions.setReaderGroups(new ArrayList<>());
    permissions.setWriterGroups(new ArrayList<>());
    return permissions;
  }
}
