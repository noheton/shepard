package de.dlr.shepard.v2.spatial.promote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** SPATIAL-UNIFY-004 — unit tests for {@link SpatialPromoteService}. */
class SpatialPromoteServiceTest {

  private static final String FILE_REF_APP_ID = "file-ref-1";
  private static final String DO_APP_ID = "do-app-1";

  @Mock
  SingletonFileReferenceService singletonFileReferenceService;

  @Mock
  SpatialDataContainerDAO spatialDataContainerDAO;

  @Mock
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  UserService userService;

  @Mock
  DateHelper dateHelper;

  SpatialPromoteService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new SpatialPromoteService();
    service.singletonFileReferenceService = singletonFileReferenceService;
    service.spatialDataContainerDAO = spatialDataContainerDAO;
    service.spatialDataReferenceDAO = spatialDataReferenceDAO;
    service.permissionsService = permissionsService;
    service.userService = userService;
    service.dateHelper = dateHelper;
    when(userService.getCurrentUser()).thenReturn(new User());
    when(dateHelper.getDate()).thenReturn(new Date());
  }

  private FileReference eligibleFileRef() {
    DataObject parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    FileReference fileRef = new FileReference();
    fileRef.setName("TPS 3D pointclouds.0");
    fileRef.setDataObject(parent);
    ShepardFile file = new ShepardFile();
    file.setFilename("TPS 3D pointclouds.0");
    fileRef.setFile(file);
    return fileRef;
  }

  @Test
  void rejectsBlankAppId() {
    assertThrows(BadRequestException.class, () -> service.promote("  "));
  }

  @Test
  void rejectsUnknownFileReference() {
    when(singletonFileReferenceService.getByAppId(FILE_REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.promote(FILE_REF_APP_ID));
  }

  @Test
  void rejectsIneligibleFile() {
    DataObject parent = new DataObject();
    parent.setAppId(DO_APP_ID);
    FileReference fileRef = new FileReference();
    fileRef.setName("report.pdf");
    fileRef.setDataObject(parent);
    ShepardFile file = new ShepardFile();
    file.setFilename("report.pdf");
    fileRef.setFile(file);
    when(singletonFileReferenceService.getByAppId(FILE_REF_APP_ID)).thenReturn(fileRef);

    assertThrows(BadRequestException.class, () -> service.promote(FILE_REF_APP_ID));
  }

  @Test
  void mintsContainerAndReferenceForEligibleFile() {
    when(singletonFileReferenceService.getByAppId(FILE_REF_APP_ID)).thenReturn(eligibleFileRef());
    when(spatialDataContainerDAO.findBySourceFileReferenceAppId(FILE_REF_APP_ID)).thenReturn(null);
    when(spatialDataContainerDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(spatialDataReferenceDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    SpatialPromoteService.PromoteResult result = service.promote(FILE_REF_APP_ID);

    assertTrue(result.created());
    assertEquals("spatial", result.io().getKind());
    assertEquals("pending", result.io().getPayload().get("promotionState"));
    verify(permissionsService).createPermissions(any(), any(), any());
  }

  @Test
  void isIdempotentWhenAlreadyPromoted() {
    FileReference fileRef = eligibleFileRef();
    when(singletonFileReferenceService.getByAppId(FILE_REF_APP_ID)).thenReturn(fileRef);

    SpatialDataContainer existing = new SpatialDataContainer(7L);
    existing.setSourceFileReferenceAppId(FILE_REF_APP_ID);
    when(spatialDataContainerDAO.findBySourceFileReferenceAppId(FILE_REF_APP_ID)).thenReturn(existing);

    SpatialDataReference existingRef = new SpatialDataReference();
    existingRef.setName("TPS 3D pointclouds.0");
    existingRef.setDataObject(fileRef.getDataObject());
    existingRef.setSpatialDataContainer(existing);
    when(spatialDataReferenceDAO.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(existingRef));

    SpatialPromoteService.PromoteResult result = service.promote(FILE_REF_APP_ID);

    assertFalse(result.created());
    assertEquals("spatial", result.io().getKind());
    verify(spatialDataContainerDAO, never()).createOrUpdate(any());
  }
}
