package de.dlr.shepard.data.file.thumbnail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import jakarta.enterprise.inject.Instance;
import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ThumbnailServiceTest {

  @Mock
  ThumbnailCache cache;

  @Mock
  ThumbnailGenerationQueue queue;

  @Mock
  FileContainerService fileContainerService;

  @Mock
  Instance<ThumbnailProvider> providerInstance;

  @Mock
  ThumbnailProvider provider;

  private ThumbnailService service;

  private static final String CONTAINER_APP_ID = "container-1";
  private static final String OID = "oid-abc";
  private static final int SIZE = 200;
  private static final byte[] PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ThumbnailService();
    service.cache = cache;
    service.queue = queue;
    service.fileContainerService = fileContainerService;
    service.providers = providerInstance;
  }

  @Test
  void cacheHitSkipsGeneration() {
    when(cache.get(CONTAINER_APP_ID, OID, SIZE)).thenReturn(PNG);

    byte[] result = service.getThumbnail(CONTAINER_APP_ID, OID, SIZE);

    assertArrayEquals(PNG, result);
    verify(fileContainerService, never()).getContainerByAppId(anyString());
  }

  @Test
  void invalidSizeThrows() {
    assertThrows(IllegalArgumentException.class,
      () -> service.getThumbnail(CONTAINER_APP_ID, OID, 999));
  }

  @Test
  void noProviderReturnsNull() {
    when(cache.get(anyString(), anyString(), anyInt())).thenReturn(null);
    when(fileContainerService.getContainerByAppId(CONTAINER_APP_ID))
      .thenReturn(containerWith(OID, "file.xyz"));
    when(providerInstance.iterator()).thenReturn(List.<ThumbnailProvider>of().iterator());

    byte[] result = service.getThumbnail(CONTAINER_APP_ID, OID, SIZE);

    assertNull(result);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cacheHitOnExtensionMatch() throws Exception {
    when(cache.get(anyString(), anyString(), anyInt())).thenReturn(null);
    when(fileContainerService.getContainerByAppId(CONTAINER_APP_ID))
      .thenReturn(containerWith(OID, "image.png"));

    when(provider.supportedMimeTypes()).thenReturn(Set.of("image/png"));
    when(provider.supportedExtensions()).thenReturn(Set.of("png"));
    when(providerInstance.iterator()).thenReturn(List.of(provider).iterator());

    NamedInputStream named = new NamedInputStream(
      OID, new ByteArrayInputStream(PNG), "image.png", (long) PNG.length);
    when(fileContainerService.getFile(anyLong(), eq(OID))).thenReturn(named);
    when(queue.submit(any(Callable.class))).thenReturn(PNG);

    byte[] result = service.getThumbnail(CONTAINER_APP_ID, OID, SIZE);

    assertArrayEquals(PNG, result);
    verify(cache, times(1)).put(CONTAINER_APP_ID, OID, SIZE, PNG);
  }

  @Test
  void fileNotInContainerReturnsNull() {
    when(cache.get(anyString(), anyString(), anyInt())).thenReturn(null);
    // container with no matching oid
    FileContainer container = new FileContainer();
    container.setFiles(List.of());
    when(fileContainerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);

    byte[] result = service.getThumbnail(CONTAINER_APP_ID, OID, SIZE);

    assertNull(result);
  }

  private static FileContainer containerWith(String oid, String filename) {
    FileContainer container = new FileContainer();
    ShepardFile file = new ShepardFile(oid, new Date(), filename, null);
    container.setFiles(List.of(file));
    return container;
  }
}
