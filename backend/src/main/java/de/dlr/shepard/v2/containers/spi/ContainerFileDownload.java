package de.dlr.shepard.v2.containers.spi;

import java.io.InputStream;

/**
 * V2CONV-A7-HDF — the kind-agnostic value object returned by
 * {@link ContainerKindHandler#downloadFile(String, String)} for container kinds
 * that expose a single downloadable file payload (today: {@code hdf}; a future
 * convergence of the file-container {@code /content} surface fits here too).
 *
 * <p>Wrapping the stream + range metadata in a core type keeps the generic
 * {@code GET /v2/containers/{appId}/file} resolver decoupled from any plugin's
 * transport type (e.g. the hdf5 plugin's {@code HsdsClient.ExportResponse}). The
 * resolver streams {@link #body()} to the caller and forwards the HTTP status +
 * range headers verbatim.
 *
 * <p>Implements {@link AutoCloseable} so the REST layer can close the underlying
 * stream after transfer.
 *
 * @param status        the HTTP status to forward (200 full, 206 partial).
 * @param body          the file bytes; the caller owns closing it (via this
 *                      object's {@link #close()}).
 * @param contentLength byte length, or a negative value when unknown.
 * @param contentRange  the {@code Content-Range} header value, or null.
 * @param acceptRanges  the {@code Accept-Ranges} header value, or null (the
 *                      resolver defaults to {@code bytes}).
 * @param fileName      the suggested download file name (used to build the
 *                      {@code Content-Disposition} header); may be null/blank.
 * @param mediaType     the response {@code Content-Type}; never null/blank.
 */
public record ContainerFileDownload(
  int status,
  InputStream body,
  long contentLength,
  String contentRange,
  String acceptRanges,
  String fileName,
  String mediaType
) implements AutoCloseable {
  @Override
  public void close() throws Exception {
    if (body != null) {
      body.close();
    }
  }
}
