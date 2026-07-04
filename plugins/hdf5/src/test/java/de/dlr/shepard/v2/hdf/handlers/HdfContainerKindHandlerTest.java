package de.dlr.shepard.v2.hdf.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient.ExportResponse;
import de.dlr.shepard.data.hdf.services.HdfContainerService;
import de.dlr.shepard.v2.containers.spi.ContainerFileDownload;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PLUGIN-CONTAINER-HANDLER-HDF — unit tests for {@link HdfContainerKindHandler}.
 * Covers {@link HdfContainerKindHandler#kind()},
 * {@link HdfContainerKindHandler#owns(de.dlr.shepard.common.neo4j.entities.BasicContainer)},
 * and the migrated {@link HdfContainerKindHandler#downloadFile(String, String)}
 * raw-HDF5 download (V2CONV-A7-HDF).
 */
class HdfContainerKindHandlerTest {

  HdfContainerKindHandler handler;
  HdfContainerDAO dao;
  HdfContainerService service;

  @BeforeEach
  void setUp() {
    handler = new HdfContainerKindHandler();
    dao = mock(HdfContainerDAO.class);
    service = mock(HdfContainerService.class);
    handler.dao = dao;
    handler.service = service;
  }

  @Test
  void kindIsHdf() {
    assertEquals("hdf", handler.kind());
  }

  @Test
  void ownsHdfContainer() {
    assertTrue(handler.owns(new HdfContainer()));
  }

  @Test
  void doesNotOwnOtherContainer() {
    assertFalse(handler.owns(new FileContainer()));
  }

  // ─── downloadFile (V2CONV-A7-HDF) ──────────────────────────────────────────

  @Test
  void downloadFileReturnsEmptyWhenContainerMissing() {
    when(dao.findByAppId("ghost")).thenReturn(null);
    assertTrue(handler.downloadFile("ghost", null).isEmpty());
  }

  @Test
  void downloadFileReturnsEmptyWhenContainerDeleted() {
    var c = new HdfContainer(1L);
    c.setAppId("app-1");
    c.setDeleted(true);
    when(dao.findByAppId("app-1")).thenReturn(c);
    assertTrue(handler.downloadFile("app-1", null).isEmpty());
  }

  @Test
  void downloadFileStreamsHdf5WithDotH5Name() {
    var c = new HdfContainer(1L);
    c.setAppId("app-1");
    c.setName("primary");
    when(dao.findByAppId("app-1")).thenReturn(c);
    var export = new ExportResponse(200, InputStream.nullInputStream(), 9L, null, "bytes");
    when(service.downloadFile(eq(c), isNull())).thenReturn(export);

    Optional<ContainerFileDownload> out = handler.downloadFile("app-1", null);
    assertTrue(out.isPresent());
    var dl = out.get();
    assertEquals(200, dl.status());
    assertEquals("application/x-hdf5", dl.mediaType());
    assertEquals("primary.h5", dl.fileName());
    assertEquals(9L, dl.contentLength());
  }

  @Test
  void downloadFileForwardsRangeMetadata() {
    var c = new HdfContainer(2L);
    c.setAppId("app-2");
    c.setName("run-data");
    when(dao.findByAppId("app-2")).thenReturn(c);
    var export = new ExportResponse(206, InputStream.nullInputStream(), 4L, "bytes 0-3/9", "bytes");
    when(service.downloadFile(eq(c), eq("bytes=0-3"))).thenReturn(export);

    var dl = handler.downloadFile("app-2", "bytes=0-3").orElseThrow();
    assertEquals(206, dl.status());
    assertEquals("bytes 0-3/9", dl.contentRange());
    assertEquals("bytes", dl.acceptRanges());
  }
}
