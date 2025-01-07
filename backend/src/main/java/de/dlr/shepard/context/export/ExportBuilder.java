package de.dlr.shepard.context.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import edu.kit.datamanager.ro_crate.RoCrate.RoCrateBuilder;
import edu.kit.datamanager.ro_crate.entities.contextual.PersonEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity.FileEntityBuilder;
import io.quarkus.logging.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.Getter;

public class ExportBuilder {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Getter(value = AccessLevel.PROTECTED)
  private RoCrateBuilder builder;

  private ByteArrayOutputStream baos;
  private ZipOutputStream zos;
  private List<String> entries;

  public ExportBuilder(Collection collection) throws IOException {
    baos = new ByteArrayOutputStream();
    zos = new ZipOutputStream(baos);
    entries = new ArrayList<>();

    var roCrateName = collection.getName() + " Research Object Crate";
    var roCrateDescription = "Research Object Crate representing the shepard Collection " + collection.getName();

    builder = new RoCrateBuilder().addName(roCrateName).addDescription(roCrateDescription);

    var collectionEntity = createFileEntity(new CollectionIO(collection));
    builder.addDataEntity(collectionEntity);
    addPersonEntity(collection.getCreatedBy());
  }

  public ExportBuilder addDataObject(DataObject dataObject) throws IOException {
    var dataObjectEntity = createFileEntity(new DataObjectIO(dataObject));
    builder.addDataEntity(dataObjectEntity);
    addPersonEntity(dataObject.getCreatedBy());
    return this;
  }

  public ExportBuilder addReference(BasicReferenceIO reference, User author) throws IOException {
    var referenceEntity = createFileEntity(reference);
    builder.addDataEntity(referenceEntity);
    addPersonEntity(author);
    return this;
  }

  public ExportBuilder addPayload(byte[] payload, String filename, String name) throws IOException {
    addToZip(filename, payload);
    builder.addDataEntity(createFileEntityBuilder(filename, name).build());
    return this;
  }

  public ExportBuilder addPayload(byte[] payload, String filename, String name, String encodingFormat)
    throws IOException {
    addToZip(filename, payload);
    builder.addDataEntity(createFileEntityBuilder(filename, name, encodingFormat).build());
    return this;
  }

  private void addPersonEntity(User user) {
    if (user == null) return;

    var entity = new PersonEntity.PersonEntityBuilder()
      .setId(user.getUsername())
      .setEmail(user.getEmail())
      .setGivenName(user.getFirstName())
      .setFamilyName(user.getLastName())
      .build();

    builder.addContextualEntity(entity);
  }

  public InputStream build() throws IOException {
    var roCrate = builder.build();
    Object jsonObject = objectMapper.readValue(roCrate.getJsonMetadata(), Object.class);
    byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonObject);
    addToZip(ExportConstants.ROCRATE_METADATA, bytes);
    zos.close();

    return new ByteArrayInputStream(baos.toByteArray());
  }

  private FileEntity createFileEntity(AbstractDataObjectIO entity) throws IOException {
    var filename = entity.getId() + ExportConstants.JSON_FILE_EXTENSION;
    writeEntityToZip(filename, entity);

    var fileEntityBuilder = createFileEntityBuilder(entity, filename);
    fileEntityBuilder.addProperty(ExportConstants.DESCRIPTION_PROP, entity.getDescription());
    return fileEntityBuilder.build();
  }

  private FileEntity createFileEntity(BasicReferenceIO entity) throws IOException {
    var filename = entity.getId() + ExportConstants.JSON_FILE_EXTENSION;
    writeEntityToZip(filename, entity);

    var fileEntityBuilder = createFileEntityBuilder(entity, filename);
    fileEntityBuilder.addProperty(ExportConstants.TYPE_PROP, entity.getType());
    return fileEntityBuilder.build();
  }

  private void writeEntityToZip(String filename, Object entity) throws IOException {
    var bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entity);
    addToZip(filename, bytes);
  }

  private static FileEntityBuilder createFileEntityBuilder(String filename, String name) {
    return new FileEntity.FileEntityBuilder().setId(filename).addProperty(ExportConstants.NAME_PROP, name);
  }

  private static FileEntityBuilder createFileEntityBuilder(String filename, String name, String encodingFormat) {
    var fileEntityBuilder = createFileEntityBuilder(filename, name);
    fileEntityBuilder.addProperty("encodingFormat", encodingFormat);
    return fileEntityBuilder;
  }

  private static FileEntityBuilder createFileEntityBuilder(BasicEntityIO entity, String filename) {
    var createdAt = convertToIsoDate(entity.getCreatedAt());
    var type = entity.getClass().getSimpleName().replace("IO", "");

    var fileEntityBuilder = createFileEntityBuilder(filename, entity.getName(), "application/json");
    fileEntityBuilder
      .addAuthor(entity.getCreatedBy())
      .addProperty(ExportConstants.CREATED_PROP, createdAt)
      .addProperty(ExportConstants.TYPE_PROP, type);

    if (entity.getUpdatedAt() != null) fileEntityBuilder.addProperty(
      ExportConstants.UPDATED_PROP,
      convertToIsoDate(entity.getUpdatedAt())
    );

    return fileEntityBuilder;
  }

  private static String convertToIsoDate(Date dateToConvert) {
    var localDateTime = LocalDateTime.ofInstant(dateToConvert.toInstant(), ZoneId.systemDefault());
    return DateTimeFormatter.ISO_DATE_TIME.format(localDateTime);
  }

  private void addToZip(String filename, byte[] bytes) throws IOException {
    if (entries.contains(filename)) {
      Log.warnf("%s already in zip file, skipping", filename);
      return;
    }

    zos.putNextEntry(new ZipEntry(filename));
    zos.write(bytes);
    zos.closeEntry();
    entries.add(filename);
  }
}
