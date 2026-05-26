package de.dlr.shepard.v2.export.rep;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the Regulatory Evidence Pack (REP) export:
 * fetches the Collection + DataObjects, builds the RO-Crate and PROV-O
 * documents, packs them into a BagIt-compliant ZIP, and returns a
 * {@link RepExportIO} response body.
 *
 * <p>Bags ≤ 1 MB are returned inline (Base64-encoded); larger bags are
 * planned to go to object storage but are not yet implemented
 * (will throw 500 if the inline limit is exceeded — tracked in TPL14b).
 *
 * <p>TPL14 — Regulatory Evidence Pack feature.
 */
@RequestScoped
public class RepExportService {

  /** Maximum bag size delivered inline (Base64) without a storage round-trip. */
  static final int INLINE_LIMIT_BYTES = 1024 * 1024; // 1 MB

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  ActivityDAO activityDAO;

  @Inject
  ProvJsonLdRenderer provRenderer;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Build a BagIt REP bag for the given collection, visible to {@code caller}.
   *
   * @param collectionAppId the UUID-v7 appId of the Collection
   * @param caller          authenticated username (used for read-path permission on CollectionDAO)
   * @return the response IO populated with the bag content
   * @throws NotFoundException           when the collection does not exist or the caller
   *                                     cannot read it (404-on-no-read discipline)
   * @throws InternalServerErrorException on serialisation or packing failure
   */
  public RepExportIO buildExport(String collectionAppId, String caller) {
    // Fetch collection (CollectionDAO returns null on missing OR unreadable — 404 both).
    Collection collection = collectionDAO.findByAppId(collectionAppId, caller);
    if (collection == null) {
      throw new NotFoundException("Collection not found: " + collectionAppId);
    }

    // Build payload files.
    Map<String, byte[]> payloadFiles = new LinkedHashMap<>();

    try {
      // 1. RO-Crate metadata.
      RoCrateBuilder roCrateBuilder = new RoCrateBuilder();
      Map<String, Object> roCrateDoc = roCrateBuilder.build(collection);
      byte[] roCrateBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(roCrateDoc);
      payloadFiles.put("ro-crate-metadata.json", roCrateBytes);

      // 2. PROV-O JSON-LD.
      ProvOBuilder provOBuilder = new ProvOBuilder(activityDAO, provRenderer);
      Map<String, Object> provODoc = provOBuilder.build(collectionAppId);
      byte[] provOBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(provODoc);
      payloadFiles.put("PROV-O.jsonld", provOBytes);
    } catch (IOException e) {
      Log.errorf("REP serialisation failed for collection %s: %s", collectionAppId, e.getMessage());
      throw new InternalServerErrorException("REP serialisation failed: " + e.getMessage());
    }

    // 3. Pack BagIt bag.
    String exportId = UUID.randomUUID().toString();
    Map<String, String> bagInfoExtra = new LinkedHashMap<>();
    bagInfoExtra.put("External-Identifier", "shepard:" + collectionAppId);
    bagInfoExtra.put("Internal-Sender-Identifier", exportId);
    bagInfoExtra.put("Bagging-Date", Instant.now().toString().substring(0, 10));

    byte[] bagBytes;
    try {
      BagItPacker packer = new BagItPacker();
      bagBytes = packer.pack(payloadFiles, bagInfoExtra);
    } catch (IOException e) {
      Log.errorf("REP BagIt packing failed for collection %s: %s", collectionAppId, e.getMessage());
      throw new InternalServerErrorException("REP BagIt packing failed: " + e.getMessage());
    }

    String fileName = sanitize(collectionAppId) + "-rep.bag.zip";
    int doCount = collection.getDataObjects() == null ? 0 : collection.getDataObjects().size();

    RepExportIO io = new RepExportIO();
    io.setExportId(exportId);
    io.setStatus("READY");
    io.setFileName(fileName);
    io.setExportedAt(Instant.now());
    io.setDataObjectCount(doCount);
    io.setBagSizeBytes(bagBytes.length);

    if (bagBytes.length <= INLINE_LIMIT_BYTES) {
      io.setBagBase64(Base64.getEncoder().encodeToString(bagBytes));
    } else {
      // Large-bag storage path deferred (TPL14b). Fail fast until implemented.
      throw new InternalServerErrorException(
        "REP bag exceeds inline limit (" + bagBytes.length + " bytes). " +
        "Large-bag storage path not yet implemented (TPL14b)."
      );
    }

    return io;
  }

  private static String sanitize(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
