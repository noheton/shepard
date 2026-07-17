package de.dlr.shepard.v2.references.handlers;

import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.HttpRangeUtil;
import de.dlr.shepard.common.util.SafeImageIO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * V2CONV-A2 / APISIMP-KIND-DISCRIMINATOR — in-tree {@link ReferenceKindHandler}
 * for {@code kind=file} (FR1b singletons). Delegates to
 * {@link SingletonFileReferenceService}.
 *
 * <p>Sets {@code referenceShape="singleton"} and surfaces {@code fileKind}.
 *
 * <p>Option C two-step file creation (APISIMP-KIND-DISCRIMINATOR):
 * <ol>
 *   <li>{@code POST /v2/references?kind=file} with JSON body {@code {name}}
 *       calls {@link #create} here, which creates a metadata-only node (no bytes).
 *   <li>{@code PUT /v2/references/{appId}/content} calls {@link #uploadContent},
 *       which stores the binary payload and attaches it to the node.
 * </ol>
 *
 * <p>The legacy {@code POST /v2/files} multipart entry point remains active
 * for backwards-compatibility until slice 2 retires it.
 */
@RequestScoped
public class FileReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  SingletonFileReferenceService singletonService;

  @Override
  public String kind() {
    return "file";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof FileReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return singletonService.getByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    FileReference ref = (FileReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.setReferenceShape("singleton");
    io.setFileKind(ref.getFileKind());
    io.put("file", ref.getFile());
    return io;
  }

  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    // APISIMP-KIND-DISCRIMINATOR Option C phase 1: create the metadata node.
    // The binary payload is attached in a follow-up PUT …/content call.
    Object nameVal = body != null ? body.get("name") : null;
    if (nameVal == null || nameVal.toString().isBlank()) {
      throw new BadRequestException("kind=file create body must include a non-blank 'name' field");
    }
    FileReference created = singletonService.createSingletonMetadata(dataObjectAppId, nameVal.toString().trim());
    return toIO(created);
  }

  @Override
  public ReferenceV2IO uploadContent(String appId, InputStream input, String filename, long declaredSize) {
    FileReference updated = singletonService.attachContent(appId, filename, input, declaredSize);
    return toIO(updated);
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    FileReference updated = singletonService.patchSingleton(appId, patch);
    return toIO(updated);
  }

  @Override
  public void delete(String appId) {
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) throw new NotFoundException("No singleton FileReference with appId " + appId);
    singletonService.deleteSingleton(appId);
  }

  /**
   * FILEREF-CONTENT-DOWNLOAD-MISSING-2026-06-30 — range-aware binary download for
   * singleton file references. Mirrors the {@code VideoStreamReferenceKindHandler}
   * shape: resolves {@code :SingletonFileReference → ShepardFile.oid →
   * fileStorageService.getPayload(...)} via {@link SingletonFileReferenceService},
   * honours a {@code Range: bytes=START-END} header for 206 Partial Content
   * (browser-native scrubbing / large URDF streaming).
   *
   * <p>Unblocks {@code GET /v2/references/{appId}/content} for the SceneGraphPlay
   * URDF download path ({@code useUrdfReferenceBlob.resolve(...)}).
   */
  @Override
  public Response downloadContent(String appId, String rangeHeader) {
    NamedInputStream payload;
    try {
      payload = singletonService.getPayload(appId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND)
        .type("application/problem+json")
        .entity(new de.dlr.shepard.common.exceptions.ProblemJson(
          "urn:shepard:error:not-found",
          Response.Status.NOT_FOUND.getReasonPhrase(),
          Response.Status.NOT_FOUND.getStatusCode(),
          nfe.getMessage(),
          null))
        .build();
    }

    // Resolve metadata for the response. The FileReference holds the canonical
    // ShepardFile + name; NamedInputStream carries the GridFS name + size.
    FileReference ref = singletonService.getByAppId(appId);
    String filename = payload.getName() != null && !payload.getName().isBlank()
      ? payload.getName()
      : (ref != null ? ref.getName() : appId);
    String contentType = MediaType.APPLICATION_OCTET_STREAM;
    if (ref != null && ref.getFile() != null) {
      // ShepardFile doesn't track mime-type today; the singleton file path stays
      // octet-stream + filename-driven content-negotiation downstream. The
      // VideoStreamReference handler diverges only because video refs carry an
      // explicit mimeType field.
      String fname = filename != null ? filename.toLowerCase() : "";
      if (fname.endsWith(".urdf") || fname.endsWith(".xml")) contentType = MediaType.APPLICATION_XML;
      else if (fname.endsWith(".json")) contentType = MediaType.APPLICATION_JSON;
      else if (fname.endsWith(".txt") || fname.endsWith(".log")) contentType = MediaType.TEXT_PLAIN;
    }
    Long total = payload.getSize();

    if (rangeHeader == null || rangeHeader.isBlank()) {
      Response.ResponseBuilder rb = Response.ok(payload.getInputStream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes");
      if (total != null) rb.header("Content-Length", total);
      return rb.build();
    }

    if (total == null || total <= 0) {
      return Response.ok(payload.getInputStream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes")
        .build();
    }

    long[] range = HttpRangeUtil.parseRange(rangeHeader, total);
    if (range == null) {
      try { if (payload.getInputStream() != null) payload.getInputStream().close(); } catch (IOException ignored) { /* close best-effort */ }
      return Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
        .header("Content-Range", "bytes */" + total)
        .header("Accept-Ranges", "bytes")
        .build();
    }
    long start = range[0];
    long end = range[1];
    long length = end - start + 1;
    StreamingOutput ranged = HttpRangeUtil.sliceStream(payload.getInputStream(), start, length);
    return Response.status(Response.Status.PARTIAL_CONTENT)
      .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
      .header("Content-Length", length)
      .header("Content-Range", "bytes " + start + "-" + end + "/" + total)
      .header("Accept-Ranges", "bytes")
      .entity(ranged)
      .type(contentType)
      .build();
  }

  /**
   * TIFF-PREVIEW-SUPPORT — honours {@code ?rendition=png}. Browsers cannot
   * render TIFF natively, so the file-reference detail page's inline
   * quick-look preview requests this transcode for {@code fileKind="image"}
   * singletons whose backing file is a TIFF. Any other rendition value, a
   * non-TIFF source, or a transcode failure (corrupt/unsupported TIFF, or a
   * source that exceeds {@link SafeImageIO#DEFAULT_MAX_PIXELS}) falls back
   * to the unmodified raw-bytes {@link #downloadContent(String, String)} —
   * additive, zero behaviour change for every existing caller.
   */
  @Override
  public Response downloadContent(String appId, String rangeHeader, String prefer, String rendition) {
    if ("png".equalsIgnoreCase(rendition)) {
      Response pngResponse = tryTiffPngRendition(appId);
      if (pngResponse != null) {
        return pngResponse;
      }
    }
    return downloadContent(appId, rangeHeader);
  }

  /**
   * Best-effort PNG transcode for a TIFF-backed singleton. Returns
   * {@code null} (never throws) whenever the rendition isn't applicable or
   * fails, so the caller falls back to the raw-bytes response — the source
   * of truth stays "TIFF bytes on disk/storage"; {@code ?rendition=png} is a
   * best-effort convenience projection on top.
   */
  private Response tryTiffPngRendition(String appId) {
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) {
      return null;
    }

    String filename = null;
    if (ref.getFile() != null && ref.getFile().getFilename() != null) {
      filename = ref.getFile().getFilename();
    }
    if (filename == null || filename.isBlank()) {
      filename = ref.getName();
    }
    if (!isTiffFilename(filename)) {
      return null;
    }

    NamedInputStream payload;
    try {
      payload = singletonService.getPayload(appId);
    } catch (NotFoundException nfe) {
      // Let the normal downloadContent(appId, rangeHeader) 404 handling own this.
      return null;
    }

    try (InputStream in = payload.getInputStream()) {
      BufferedImage rgb = SafeImageIO.readCappedAsRgb8(in, SafeImageIO.DEFAULT_MAX_PIXELS);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      if (!ImageIO.write(rgb, "PNG", baos)) {
        Log.warnf("TIFF-PREVIEW-SUPPORT: ImageIO.write(PNG) returned false for appId=%s", appId);
        return null;
      }
      byte[] png = baos.toByteArray();
      String pngName = stripExtension(filename) + ".png";
      return Response.ok(png, "image/png")
        .header("Content-Disposition", "inline; filename=\"" + pngName + "\"")
        .header("Content-Length", png.length)
        .build();
    } catch (IOException e) {
      Log.warnf(
        "TIFF-PREVIEW-SUPPORT: PNG rendition failed for appId=%s filename=%s — falling back to raw bytes: %s",
        appId, filename, e.getMessage()
      );
      return null;
    }
  }

  private static boolean isTiffFilename(String filename) {
    if (filename == null) {
      return false;
    }
    String lc = filename.toLowerCase(Locale.ROOT);
    return lc.endsWith(".tif") || lc.endsWith(".tiff");
  }

  private static String stripExtension(String filename) {
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<FileReference> refs = singletonService.listByDataObject(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (FileReference ref : refs) {
      if (ref == null || ref.isDeleted()) continue;
      // V2CONV-A2 fileKind sub-filter: when subKind is supplied, only
      // singletons whose fileKind matches pass through.
      if (subKind != null && !subKind.isBlank() && !subKind.equals(ref.getFileKind())) continue;
      out.add(toIO(ref));
    }
    return out;
  }

  /**
   * APISIMP-REFS-INMEM-PAGING — delegates COUNT to Neo4j rather than loading
   * all rows and calling {@code .size()} in Java.
   */
  @Override
  public int countByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    return singletonService.countByDataObject(dataObjectAppId, subKind);
  }

  /**
   * APISIMP-REFS-INMEM-PAGING — pushes SKIP/LIMIT to Neo4j; never loads the
   * full list then subList-slices in Java.
   */
  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind, int skip, int limit) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<FileReference> refs = singletonService.listByDataObject(dataObjectAppId, subKind, skip, limit);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (FileReference ref : refs) {
      if (ref != null) out.add(toIO(ref));
    }
    return out;
  }
}
