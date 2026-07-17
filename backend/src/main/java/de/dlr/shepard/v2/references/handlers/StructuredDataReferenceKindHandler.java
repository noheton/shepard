package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.structureddata.daos.StructuredDataReferenceDAO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * APISIMP-STRUCTURED-DATA-KIND — in-tree {@link ReferenceKindHandler} for
 * {@code kind=structured-data}. Enables {@code POST /v2/references?kind=structured-data}
 * so callers can create StructuredDataReferences without touching the frozen
 * v1 {@code /shepard/api/} numeric-id surface.
 *
 * <p>Payload key set in {@link ReferenceV2IO#getPayload()}:
 * <ul>
 *   <li>{@code structuredDataContainerAppId} — UUID v7 of the backing container</li>
 *   <li>{@code structuredDataOids} — String[] of MongoDB OIDs in the container</li>
 * </ul>
 *
 * <p>Create body (slice 1): {@code {name, structuredDataContainerAppId, structuredDataOids?}}.
 * The v1 {@code /shepard/api/} paths remain frozen and unchanged.
 */
@RequestScoped
public class StructuredDataReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  StructuredDataReferenceDAO structuredDataReferenceDAO;

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  DateHelper dateHelper;

  @Inject
  UserService userService;

  @Override
  public String kind() {
    return "structured-data";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof StructuredDataReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return structuredDataReferenceDAO.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    StructuredDataReference ref = (StructuredDataReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    String containerAppId = ref.getStructuredDataContainer() != null
      ? ref.getStructuredDataContainer().getAppId()
      : null;
    io.put("structuredDataContainerAppId", containerAppId);
    String[] oids = ref.getStructuredDatas() == null
      ? new String[0]
      : ref.getStructuredDatas().stream().map(StructuredData::getOid).toArray(String[]::new);
    io.put("structuredDataOids", oids);
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    if (body == null) {
      throw new BadRequestException("create body is required for kind=structured-data");
    }
    DataObject parent = resolveParent(dataObjectAppId);

    Object nameVal = body.get("name");
    if (nameVal == null || nameVal.toString().isBlank()) {
      throw new BadRequestException(
        "kind=structured-data create body must include a non-blank 'name' field"
      );
    }

    Object containerAppIdVal = body.get("structuredDataContainerAppId");
    if (containerAppIdVal == null || containerAppIdVal.toString().isBlank()) {
      throw new BadRequestException(
        "kind=structured-data create body must include 'structuredDataContainerAppId'"
      );
    }

    StructuredDataContainer container = structuredDataContainerService.getContainerByAppId(
      containerAppIdVal.toString().trim()
    );

    StructuredDataReferenceIO io = new StructuredDataReferenceIO();
    io.setName(nameVal.toString().trim());
    io.setStructuredDataContainerId(container.getId());

    Object oidsVal = body.get("structuredDataOids");
    if (oidsVal instanceof List<?> oidsList) {
      io.setStructuredDataOids(oidsList.stream().map(Object::toString).toArray(String[]::new));
    } else {
      io.setStructuredDataOids(new String[0]);
    }

    StructuredDataReference created = structuredDataReferenceService.createReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      io
    );
    return toIO(created);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    StructuredDataReference ref = structuredDataReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      throw new NotFoundException("No StructuredDataReference with appId " + appId);
    }
    if (patch.containsKey("name")) {
      Object nameVal = patch.get("name");
      if (nameVal == null || nameVal.toString().isBlank()) {
        throw new BadRequestException("name must not be blank");
      }
      ref.setName(nameVal.toString().trim());
      ref.setUpdatedAt(dateHelper.getDate());
      ref.setUpdatedBy(userService.getCurrentUser());
      structuredDataReferenceDAO.createOrUpdate(ref);
    }
    return toIO(ref);
  }

  @Override
  public void delete(String appId) {
    StructuredDataReference ref = structuredDataReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      throw new NotFoundException("No StructuredDataReference with appId " + appId);
    }
    DataObject parent = ref.getDataObject();
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException(
        "StructuredDataReference " + appId + " has no resolvable parent DataObject"
      );
    }
    structuredDataReferenceService.deleteReference(
      parent.getCollection().getShepardId(),
      parent.getShepardId(),
      ref.getShepardId()
    );
  }

  @Override
  public ReferenceV2IO uploadContent(String appId, InputStream input, String filename, long declaredSize) {
    StructuredDataReference ref = structuredDataReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      throw new NotFoundException("No StructuredDataReference with appId " + appId);
    }
    StructuredDataContainer container = ref.getStructuredDataContainer();
    if (container == null) {
      throw new BadRequestException(
        "StructuredDataReference " + appId + " has no linked container — cannot upload content"
      );
    }
    if (input == null) {
      throw new BadRequestException("upload body is required for kind=structured-data");
    }
    String jsonPayload;
    try {
      jsonPayload = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BadRequestException("failed to read upload body: " + e.getMessage());
    }
    if (jsonPayload.isBlank()) {
      throw new BadRequestException("upload body must not be empty");
    }
    StructuredData meta = filename != null && !filename.isBlank()
      ? new StructuredData(filename.trim(), dateHelper.getDate())
      : new StructuredData();
    StructuredData created = structuredDataContainerService.createStructuredData(
      container.getId(), new StructuredDataPayload(meta, jsonPayload)
    );
    ref.addStructuredData(created);
    structuredDataReferenceDAO.createOrUpdate(ref);
    return toIO(ref);
  }

  /**
   * SDR-CONTENT-V2: stream all payload items referenced by this
   * StructuredDataReference as a JSON array.
   *
   * <p>Each element in the array is the parsed JSON document that was uploaded
   * via {@code PUT /v2/references/{appId}/content} for the given oid. Items
   * that fail to load (e.g. orphaned oid) are silently skipped with a WARN log.
   *
   * <p>Returns an empty array ({@code []}) when the reference has no items or
   * has no linked container.
   */
  @Override
  public Response downloadContent(String appId, String rangeHeader) {
    StructuredDataReference ref = structuredDataReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      throw new NotFoundException("No StructuredDataReference with appId " + appId);
    }
    StructuredDataContainer container = ref.getStructuredDataContainer();
    List<StructuredData> nodes = ref.getStructuredDatas();
    if (container == null || nodes == null || nodes.isEmpty()) {
      return Response.ok("[]", MediaType.APPLICATION_JSON).build();
    }
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode array = mapper.createArrayNode();
    for (StructuredData node : nodes) {
      try {
        StructuredDataPayload sdp = structuredDataContainerService.getStructuredData(
          container.getId(), node.getOid());
        if (sdp == null || sdp.getPayload() == null) continue;
        ObjectNode item = mapper.createObjectNode();
        item.put("oid", node.getOid());
        if (node.getName() != null) item.put("name", node.getName());
        if (node.getCreatedAt() != null) {
          item.put("createdAt", node.getCreatedAt().toInstant().toString());
        }
        JsonNode payloadNode = mapper.readTree(sdp.getPayload());
        item.set("payload", payloadNode);
        array.add(item);
      } catch (Exception e) {
        Log.warnf("SDR-CONTENT-V2: failed to load oid=%s for ref=%s: %s",
          node.getOid(), appId, e.getMessage());
      }
    }
    return Response.ok(array.toString(), MediaType.APPLICATION_JSON).build();
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    // APISIMP-SDR-LIST-APPID-PATH: use the appId-keyed DAO path directly rather
    // than resolving the DataObject numeric Neo4j id via resolveParent() + findByDataObjectNeo4jId().
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<StructuredDataReference> refs = structuredDataReferenceDAO.findByDataObjectAppId(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (StructuredDataReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }

  @Override
  public int countByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    return structuredDataReferenceDAO.countByDataObjectAppId(dataObjectAppId);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind, int skip, int limit) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<StructuredDataReference> refs = structuredDataReferenceDAO.findByDataObjectAppId(dataObjectAppId, skip, limit);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (StructuredDataReference ref : refs) {
      if (ref != null) out.add(toIO(ref));
    }
    return out;
  }

  private DataObject resolveParent(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    DataObject parent = dataObjectDAO.findByAppId(dataObjectAppId);
    if (parent == null || parent.getCollection() == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    return parent;
  }
}
