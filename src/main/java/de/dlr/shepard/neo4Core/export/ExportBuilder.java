package de.dlr.shepard.neo4Core.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.AbstractDataObjectIO;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import edu.kit.datamanager.ro_crate.RoCrate.RoCrateBuilder;
import edu.kit.datamanager.ro_crate.entities.contextual.PersonEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity.FileEntityBuilder;
import lombok.AccessLevel;
import lombok.Getter;

public class ExportBuilder {

	private ObjectMapper objectMapper = new ObjectMapper();

	@Getter(value = AccessLevel.PROTECTED)
	private RoCrateBuilder builder;
	private ByteArrayOutputStream baos;
	private ZipOutputStream zos;

	public ExportBuilder(Collection collection) throws IOException {
		baos = new ByteArrayOutputStream();
		zos = new ZipOutputStream(baos);

		var roCrateName = collection.getName() + " Research Object Crate";
		var roCrateDescription = "Research Object Crate representing the shepard Collection " + collection.getName();
		builder = new RoCrateBuilder(roCrateName, roCrateDescription);

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

	public ExportBuilder addPayload(InputStream payload, String filename, String name, String encodingFormat)
			throws IOException {
		zos.putNextEntry(new ZipEntry(filename));
		zos.write(payload.readAllBytes());

		builder.addDataEntity(createFileEntityBuilder(filename, name, encodingFormat).build());
		return this;
	}

	public ExportBuilder addPayload(InputStream payload, String filename, String name) throws IOException {
		zos.putNextEntry(new ZipEntry(filename));
		zos.write(payload.readAllBytes());

		builder.addDataEntity(createFileEntityBuilder(filename, name).build());
		return this;
	}

	public ExportBuilder addPayload(byte[] payload, String filename, String name, String encodingFormat)
			throws IOException {
		zos.putNextEntry(new ZipEntry(filename));
		zos.write(payload);

		builder.addDataEntity(createFileEntityBuilder(filename, name, encodingFormat).build());
		return this;
	}

	private void addPersonEntity(User user) {
		if (user == null)
			return;

		var entity = new PersonEntity.PersonEntityBuilder().setId(user.getUsername()).setEmail(user.getEmail())
				.setGivenName(user.getFirstName()).setFamilyName(user.getLastName()).build();

		builder.addContextualEntity(entity);
	}

	public InputStream build() throws IOException {
		var roCrate = builder.build();
		zos.putNextEntry(new ZipEntry(ExportConstants.ROCRATE_METADATA));
		Object jsonObject = objectMapper.readValue(roCrate.getJsonMetadata(), Object.class);
		zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonObject));
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
		zos.putNextEntry(new ZipEntry(filename));
		zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entity));
	}

	private static FileEntityBuilder createFileEntityBuilder(String filename, String name) {
		// We need a dummy file since the FileEntityBuilder cannot work without a source
		// file. We only use the RoCrate lib just for creating the
		// ro-crate-metadata.json, so there is no file needed at this point.
		var dummyFile = new File("dummy.txt");
		return new FileEntity.FileEntityBuilder().setId(filename).setSource(dummyFile)
				.addProperty(ExportConstants.NAME_PROP, name);
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
		fileEntityBuilder.addAuthor(entity.getCreatedBy()).addProperty(ExportConstants.CREATED_PROP, createdAt)
				.addProperty(ExportConstants.TYPE_PROP, type);

		if (entity.getUpdatedAt() != null)
			fileEntityBuilder.addProperty(ExportConstants.UPDATED_PROP, convertToIsoDate(entity.getUpdatedAt()));

		return fileEntityBuilder;
	}

	private static String convertToIsoDate(Date dateToConvert) {
		var localDateTime = LocalDateTime.ofInstant(dateToConvert.toInstant(), ZoneId.systemDefault());
		return DateTimeFormatter.ISO_DATE_TIME.format(localDateTime);
	}

}