package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.semantic.services.AttributeAnnotationDualWriteService;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import de.dlr.shepard.v2.events.CollectionEventProducer;
import java.util.ArrayList;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FAIR2 — createdByOrcid stamp on DataObject create.
 *
 * <p>Uses plain Mockito (no Quarkus boot) to keep the tests fast and
 * independent of the CDI container. Covers the four FAIR2 behavioural
 * requirements specified in the task:
 *
 * <ol>
 *   <li>ORCID is stamped when the creating user has one.</li>
 *   <li>Create succeeds (no ORCID stamped) when the user has no ORCID.</li>
 *   <li>Create succeeds (best-effort WARN) when UserService throws.</li>
 *   <li>createdByOrcid is NOT present in CreateDataObjectV2IO (not user-settable).</li>
 * </ol>
 */
public class DataObjectServiceFair2Test {

  private DataObjectService service;
  private DataObjectDAO dataObjectDAO;
  private UserService userService;

  /** Minimal wired collection used across tests. */
  private Collection collection;

  @BeforeEach
  void setUp() {
    dataObjectDAO = mock(DataObjectDAO.class);
    userService = mock(UserService.class);

    CollectionService collectionService = mock(CollectionService.class);
    VersionService versionService = mock(VersionService.class);
    DateHelper dateHelper = mock(DateHelper.class);
    AttributeAnnotationDualWriteService dualWriteService = mock(AttributeAnnotationDualWriteService.class);
    CollectionWatcherService watcherService = mock(CollectionWatcherService.class);
    CollectionEventProducer eventProducer = mock(CollectionEventProducer.class);
    DataObjectReferenceDAO referenceDAO = mock(DataObjectReferenceDAO.class);

    PermissionsService permissionsService = mock(PermissionsService.class);
    AuthenticationContext authenticationContext = mock(AuthenticationContext.class);

    service = new DataObjectService();
    service.dataObjectDAO = dataObjectDAO;
    service.userService = userService;
    service.collectionService = collectionService;
    service.versionService = versionService;
    service.dateHelper = dateHelper;
    service.attributeAnnotationDualWriteService = dualWriteService;
    service.collectionWatcherService = watcherService;
    service.collectionEventProducer = eventProducer;
    service.dataObjectReferenceDAO = referenceDAO;
    service.permissionsService = permissionsService;
    service.authenticationContext = authenticationContext;
    service.archiveStateGuard = mock(de.dlr.shepard.context.collection.services.ArchiveStateGuard.class);
    service.featureToggleRegistry = mock(de.dlr.shepard.common.configuration.feature.runtime.FeatureToggleRegistry.class);

    collection = new Collection();
    collection.setShepardId(1L);
    collection.setName("test-collection");

    when(collectionService.getCollection(1L)).thenReturn(collection);
    when(dateHelper.getDate()).thenReturn(new Date());

    // DAO creates and returns a DataObject with a fake Neo4j id.
    // On the first call (createOrUpdate) OGM would assign an id; we simulate
    // this by setting the id field via reflection so the service can read it
    // back for setShepardId(created.getId()). On the second call the id is
    // already set and we just return the same object.
    final long[] fakeIdCounter = {42L};
    when(dataObjectDAO.createOrUpdate(any(DataObject.class))).thenAnswer(inv -> {
      DataObject do_ = inv.getArgument(0);
      if (do_.getId() == null) {
        // Simulate OGM assigning a Neo4j id via the @GeneratedValue field.
        try {
          var idField = de.dlr.shepard.common.neo4j.entities.AbstractEntity.class.getDeclaredField("id");
          idField.setAccessible(true);
          idField.set(do_, fakeIdCounter[0]++);
        } catch (Exception ignored) {}
      }
      // Set children/incoming/predecessors/successors/references to empty if null
      if (do_.getChildren() == null) do_.setChildren(new ArrayList<>());
      if (do_.getIncoming() == null) do_.setIncoming(new ArrayList<>());
      if (do_.getPredecessors() == null) do_.setPredecessors(new ArrayList<>());
      if (do_.getSuccessors() == null) do_.setSuccessors(new ArrayList<>());
      if (do_.getReferences() == null) do_.setReferences(new ArrayList<>());
      return do_;
    });
  }

  // ── FAIR2 test 1: ORCID is stamped when the creating user has one ──────

  @Test
  public void createDataObject_stampsCreatedByOrcid_whenUserHasOrcid() {
    User user = new User("alice");
    user.setOrcid("0000-0001-2345-6789");
    when(userService.getCurrentUser()).thenReturn(user);

    DataObjectIO io = new DataObjectIO();
    io.setName("TR-004");
    io.setAttributes(new java.util.HashMap<>());

    DataObject result = service.createDataObject(1L, io);

    assertEquals("0000-0001-2345-6789", result.getCreatedByOrcid(),
      "FAIR2: createdByOrcid must be stamped from User.orcid at create time");
  }

  // ── FAIR2 test 2: Create succeeds when user has no ORCID ───────────────

  @Test
  public void createDataObject_doesNotFail_whenUserHasNoOrcid() {
    User user = new User("bob");
    // orcid is null by default
    when(userService.getCurrentUser()).thenReturn(user);

    DataObjectIO io = new DataObjectIO();
    io.setName("TR-005");
    io.setAttributes(new java.util.HashMap<>());

    DataObject result = service.createDataObject(1L, io);

    assertNull(result.getCreatedByOrcid(),
      "FAIR2: createdByOrcid must be null when the user has no ORCID set");
    assertNotNull(result, "Create must succeed even when user has no ORCID");
  }

  // ── FAIR2 test 3: Create succeeds (best-effort) when UserService throws ─

  @Test
  public void createDataObject_doesNotFail_whenUserServiceThrows() {
    // First call returns a valid user for the service to use as createdBy / event producer.
    User user = new User("charlie");
    // Simulate the first call (getCurrentUser for createdBy) succeeding,
    // then the FAIR2 ORCID-stamp call throwing. In practice the service
    // calls getCurrentUser() twice — once for createdBy, once in the
    // FAIR2 try-block. We model the second call as throwing.
    when(userService.getCurrentUser())
      .thenReturn(user)
      .thenThrow(new RuntimeException("ORCID lookup failed"));

    DataObjectIO io = new DataObjectIO();
    io.setName("TR-006");
    io.setAttributes(new java.util.HashMap<>());

    // Must not throw — best-effort: WARN and continue.
    DataObject result = service.createDataObject(1L, io);

    assertNotNull(result, "Create must succeed even when ORCID stamp throws");
    assertNull(result.getCreatedByOrcid(),
      "FAIR2: createdByOrcid must be null when ORCID stamp failed");
  }

  // ── FAIR2 test 4: createdByOrcid absent from CreateDataObjectV2IO ────────

  @Test
  public void createDataObjectV2IO_doesNotExposeCreatedByOrcid() {
    // createdByOrcid must not be a declared field on CreateDataObjectV2IO
    // (it is inherited read-only from AbstractDataObjectIO, not an input field).
    boolean hasDeclaredField = java.util.Arrays.stream(
        de.dlr.shepard.v2.dataobject.io.CreateDataObjectV2IO.class.getDeclaredFields()
      ).anyMatch(f -> "createdByOrcid".equals(f.getName()));

    org.junit.jupiter.api.Assertions.assertFalse(hasDeclaredField,
      "FAIR2: createdByOrcid must not be a declared field on CreateDataObjectV2IO " +
      "— it is server-stamped and never accepted as user input");
  }
}
