package de.dlr.shepard.context.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ExportServiceQuarkusTest {

  @Inject
  CollectionService collectionService;

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  FileReferenceService fileReferenceService;

  @Inject
  VersionDAO versionDAO;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  ExportService exportService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  StructuredDataService structuredDataService;

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectReferenceService dataObjectReferenceService;

  @Inject
  VersionService versionService;

  @Inject
  CollectionReferenceService collectionReferenceService;

  private Collection collection;
  private User user;
  private DataObject dataObject;
  private DataObject childDataObject;
  private LabJournalEntry labJournalEntry;
  private FileContainer fileContainer;
  private FileReference fileReference;
  private DataObjectReference dataObjectReference;
  private ShepardFile shepardFile;
  private TimeseriesTuple timeseries;
  private TimeseriesReference timeseriesReference;
  private TimeseriesContainer timeseriesContainer;
  private final String userName = "user_name";
  private final String collectionName = "c1";
  private final String dataObjectName = "c1do1";
  private final String childDataObjectName = "childDO";
  private final String labJournalEntryContent = "LabJournalContent";
  private final String dataObjectReferenceName = "doReference";
  private final String fileReferenceName = "fileReference";
  private final String fileName = "myFile";
  private final String fileContent = "test file content";
  private final String fileContainerName = "fileContainer";
  private final String timeseriesReferenceName = "timeseriesReference";
  private final String timeseriesContainerName = "timeseriesContainer";

  private DataObject createDataObject(DataObjectIO dataObjectToCreate) {
    return dataObjectService.createDataObject(collection.getId(), dataObjectToCreate);
  }

  @BeforeEach
  public void setup() {
    if (user == null) {
      //create user
      user = new User(userName);
      user.setEmail("user@dlr.de");
      userService.createOrUpdateUser(user);
      authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));

      //create collection
      CollectionIO collectionIO = new CollectionIO();
      collectionIO.setName(collectionName);
      collection = collectionService.createCollection(collectionIO);

      //create dataObject
      DataObjectIO dataObjectIO = new DataObjectIO();
      dataObjectIO.setName(dataObjectName);
      dataObject = createDataObject(dataObjectIO);

      //create childDataObject
      DataObjectIO childDataObjectIO = new DataObjectIO();
      childDataObjectIO.setName(childDataObjectName);
      childDataObjectIO.setParentId(dataObject.getShepardId());
      childDataObject = createDataObject(childDataObjectIO);

      //create dataObjectReference
      DataObjectReferenceIO dataObjectReferenceIO = new DataObjectReferenceIO();
      dataObjectReferenceIO.setName(dataObjectReferenceName);
      dataObjectReferenceIO.setReferencedDataObjectId(dataObject.getShepardId());
      dataObjectReference = dataObjectReferenceService.createReference(
        collection.getShepardId(),
        childDataObject.getShepardId(),
        dataObjectReferenceIO
      );

      //create labJournalEntry
      labJournalEntry = labJournalEntryService.createLabJournalEntry(dataObject.getShepardId(), labJournalEntryContent);

      //create FileContainer
      FileContainerIO fileContainerIO = new FileContainerIO();
      fileContainerIO.setName(fileContainerName);
      fileContainer = fileContainerService.createContainer(fileContainerIO);

      //create and store (shepard)file
      File file;
      try {
        file = new File(getClass().getClassLoader().getResource("test.txt").toURI());
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(fileContent);
        writer.close();
        FileInputStream fileInputStream = new FileInputStream(file);
        shepardFile = fileContainerService.createFile(fileContainer.getId(), fileName, fileInputStream);
      } catch (Exception e) {
        e.printStackTrace();
      }

      //create fileReference
      FileReferenceIO fileReferenceIO = new FileReferenceIO();
      fileReferenceIO.setName(fileReferenceName);
      fileReferenceIO.setFileContainerId(fileContainer.getId());
      String[] oids = { shepardFile.getOid() };
      fileReferenceIO.setFileOids(oids);
      fileReference = fileReferenceService.createReference(
        collection.getShepardId(),
        dataObject.getShepardId(),
        fileReferenceIO
      );

      //create timerseriesContainer
      TimeseriesContainerIO timeseriesContainerIO = new TimeseriesContainerIO();
      timeseriesContainerIO.setName(timeseriesContainerName);
      timeseriesContainer = timeseriesContainerService.createContainer(timeseriesContainerIO);

      //create timeseries
      TimeseriesDataPoint dataPoint1 = new TimeseriesDataPoint(1l, true);
      TimeseriesDataPoint dataPoint2 = new TimeseriesDataPoint(2l, true);
      TimeseriesDataPoint dataPoint3 = new TimeseriesDataPoint(3l, true);
      List<TimeseriesDataPoint> points = new ArrayList<TimeseriesDataPoint>();
      points.add(dataPoint1);
      points.add(dataPoint2);
      points.add(dataPoint3);
      timeseries = new TimeseriesTuple("m", "d", "l", "s", "f");
      ArrayList<TimeseriesTuple> timeseriesList = new ArrayList<TimeseriesTuple>();
      timeseriesList.add(timeseries);
      timeseriesService.saveDataPoints(timeseriesContainer.getId(), timeseries, points);

      //create timeseriesReference
      TimeseriesReferenceIO timeseriesReferenceIO = new TimeseriesReferenceIO();
      timeseriesReferenceIO.setName(timeseriesReferenceName);
      timeseriesReferenceIO.setStart(1l);
      timeseriesReferenceIO.setEnd(2l);
      timeseriesReferenceIO.setTimeseriesContainerId(timeseriesContainer.getId());
      timeseriesReferenceIO.setTimeseries(timeseriesList);
      timeseriesReference = timeseriesReferenceService.createReference(
        collection.getShepardId(),
        dataObject.getShepardId(),
        timeseriesReferenceIO
      );
    }
  }

  private String readZipFile(ZipInputStream zipInputStream) {
    byte[] buffer = new byte[2048];
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    int len;
    try {
      while ((len = zipInputStream.read(buffer)) > 0) {
        bos.write(buffer, 0, len);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    byte[] zipFileBytes = bos.toByteArray();
    String fileContent = new String(zipFileBytes);
    return fileContent;
  }

  @Test
  @Transactional
  public void testExport() {
    InputStream exportStream = null;
    ZipInputStream exportZipStream = null;
    ArrayList<String> filenames = new ArrayList<String>();
    Hashtable<String, String> roCrateEntries = new Hashtable<String, String>();
    try {
      exportStream = exportService.exportCollectionByShepardId(collection.getShepardId());
      exportZipStream = new ZipInputStream(exportStream);
      ZipEntry zipEntry = exportZipStream.getNextEntry();
      while (zipEntry != null) {
        filenames.add(zipEntry.getName());
        String zipContent = readZipFile(exportZipStream);
        roCrateEntries.put(zipEntry.getName(), zipContent);
        zipEntry = exportZipStream.getNextEntry();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    //test presence of all expected files in export
    assertThat(filenames).containsExactlyInAnyOrder(
      collection.getId() + ".json",
      childDataObject.getId() + ".json",
      labJournalEntry.getId() + ".json",
      dataObject.getId() + ".json",
      dataObjectReference.getId() + ".json",
      fileReference.getId() + ".json",
      shepardFile.getOid(),
      timeseriesReference.getId() + ".json",
      timeseriesReference.getId() + ".csv",
      "ro-crate-metadata.json"
    );

    assertThat(roCrateEntries.get(collection.getId() + ".json")).containsSubsequence("\"id\" : " + collection.getId());
    //look into labJournalContent
    assertThat(roCrateEntries.get(labJournalEntry.getId() + ".json")).containsSubsequence(
      "\"journalContent\" : \"" + labJournalEntryContent + "\""
    );
    //test existence of parent entry in child export
    assertThat(roCrateEntries.get(childDataObject.getId() + ".json")).containsSubsequence(
      "\"parentId\" : " + dataObject.getId()
    );
    //look into export of filePayload
    assertEquals(true, roCrateEntries.get(shepardFile.getOid()).equals(fileContent));
    //look into export of timeseriesReferencePayload
    assertThat(roCrateEntries.get(timeseriesReference.getId() + ".csv")).containsSubsequence(
      "DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE"
    );
    assertThat(roCrateEntries.get(timeseriesReference.getId() + ".csv")).containsSubsequence("d,f,l,m,s,1,true");
    assertThat(roCrateEntries.get(timeseriesReference.getId() + ".csv")).containsSubsequence("d,f,l,m,s,2,true");
    assertEquals(false, roCrateEntries.get(childDataObject.getId() + ".json").contains("d,f,l,m,s,3,true"));
  }
}
