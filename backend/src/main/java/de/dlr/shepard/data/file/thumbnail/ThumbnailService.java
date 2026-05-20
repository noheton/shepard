package de.dlr.shepard.data.file.thumbnail;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * TH1a — orchestrates thumbnail generation: provider selection → queue → cache.
 *
 * <p>Request flow:
 * <ol>
 *   <li>Cache hit? Return cached PNG bytes immediately.</li>
 *   <li>Resolve provider by MIME type or file extension.</li>
 *   <li>No provider? Return {@code null} (→ HTTP 404 at the REST layer).</li>
 *   <li>Submit generation to {@link ThumbnailGenerationQueue}.</li>
 *   <li>Cache result; return PNG bytes.</li>
 * </ol>
 */
@ApplicationScoped
public class ThumbnailService {

  static final Set<Integer> VALID_SIZES = Set.of(64, 200, 400);

  @Inject
  ThumbnailCache cache;

  @Inject
  ThumbnailGenerationQueue queue;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  Instance<ThumbnailProvider> providers;

  /**
   * Returns PNG thumbnail bytes, or {@code null} if no provider supports this file type.
   *
   * @throws ServiceUnavailableException on timeout or queue-full
   * @throws IllegalArgumentException if {@code sizePx} is not in {64, 200, 400}
   */
  public byte[] getThumbnail(String containerAppId, String oid, int sizePx) {
    if (!VALID_SIZES.contains(sizePx)) {
      throw new IllegalArgumentException("size must be one of 64, 200, 400; got " + sizePx);
    }

    // 1 — cache hit
    byte[] cached = cache.get(containerAppId, oid, sizePx);
    if (cached != null) return cached;

    // 2 — resolve container + file metadata
    FileContainer container = fileContainerService.getContainerByAppId(containerAppId);
    ShepardFile file = findFile(container, oid);
    if (file == null) return null;

    String filename = file.getFilename() != null ? file.getFilename() : oid;

    // 3 — select provider (extension-based; ShepardFile has no stored MIME type)
    ThumbnailProvider provider = selectProvider(null, filename);
    if (provider == null) {
      Log.debugf("ThumbnailService: no provider for filename=%s", filename);
      return null;
    }

    // 4 — generate via queue (reads file bytes from storage)
    final ThumbnailProvider chosenProvider = provider;
    final int finalSize = sizePx;
    byte[] pngBytes;
    try {
      pngBytes = queue.submit(() -> {
        de.dlr.shepard.common.mongoDB.NamedInputStream named =
          fileContainerService.getFile(container.getId(), oid);
        try (InputStream is = named.getInputStream()) {
          return chosenProvider.generate(is, filename, finalSize);
        }
      });
    } catch (TimeoutException | RejectedExecutionException e) {
      throw new ServiceUnavailableException(
        "Thumbnail generation temporarily unavailable — " + e.getClass().getSimpleName());
    } catch (IOException e) {
      Log.warnf("ThumbnailService: provider threw IOException for %s/%s: %s", containerAppId, oid, e.getMessage());
      return null;
    } catch (Exception e) {
      Log.warnf("ThumbnailService: provider failed for %s/%s: %s", containerAppId, oid, e.getMessage());
      return null;
    }

    if (pngBytes == null || pngBytes.length == 0) return null;

    // 5 — cache + return
    cache.put(containerAppId, oid, sizePx, pngBytes);
    return pngBytes;
  }

  private ShepardFile findFile(FileContainer container, String oid) {
    if (container.getFiles() == null) return null;
    for (ShepardFile f : container.getFiles()) {
      if (oid.equals(f.getOid())) return f;
    }
    return null;
  }

  private ThumbnailProvider selectProvider(String mimeType, String filename) {
    if (mimeType != null && !mimeType.isBlank() && !"application/octet-stream".equals(mimeType)) {
      for (ThumbnailProvider p : providers) {
        if (p.supportedMimeTypes().contains(mimeType)) return p;
      }
    }
    String ext = extension(filename);
    if (!ext.isEmpty()) {
      for (ThumbnailProvider p : providers) {
        if (p.supportedExtensions().contains(ext)) return p;
      }
    }
    return null;
  }

  private static String extension(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) return "";
    return filename.substring(dot + 1).toLowerCase();
  }
}
