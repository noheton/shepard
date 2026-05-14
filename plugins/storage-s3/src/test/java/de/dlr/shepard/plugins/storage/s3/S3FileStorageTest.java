package de.dlr.shepard.plugins.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.StorageProviderUnavailableException;
import de.dlr.shepard.storage.StoragePutRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * FS1b — unit tests for {@link S3FileStorage}. Uses Mockito to mock
 * the {@link S3Client} so no real S3 endpoint is needed.
 *
 * <p>The strategy: mock {@code S3StorageConfigService} + inject a
 * spy S3FileStorage that overrides {@link S3FileStorage#buildClient}
 * to return the mock client.
 */
class S3FileStorageTest {

  private S3StorageConfigService configService;
  private S3Client s3Client;
  private S3FileStorage storage;
  private S3StorageConfig activeConfig;

  @BeforeEach
  void setUp() {
    configService = mock(S3StorageConfigService.class);
    s3Client = mock(S3Client.class);

    activeConfig = new S3StorageConfig();
    activeConfig.setEnabled(true);
    activeConfig.setBucket("test-bucket");
    activeConfig.setAccessKeyId("AKIA123");
    activeConfig.setSecretAccessKeyCipher("gcm1:fake");
    activeConfig.setRegion("us-east-1");
    activeConfig.setForcePathStyle(true);
    activeConfig.setConnectionTimeoutSeconds(10);
    activeConfig.setRequestTimeoutSeconds(30);

    when(configService.getActive()).thenReturn(activeConfig);
    when(configService.resolvePlaintextSecret()).thenReturn("secret-key");

    // Use an anonymous subclass that overrides buildClient to return the mock.
    storage = new S3FileStorage() {
      @Override
      S3Client buildClient(S3StorageConfig cfg) throws StorageProviderUnavailableException {
        return s3Client;
      }
    };
    storage.configService = configService;
  }

  // ─── id + isEnabled ─────────────────────────────────────────────────────

  @Test
  void id_returnsS3() {
    assertThat(storage.id()).isEqualTo("s3");
  }

  @Test
  void isEnabled_trueWhenActiveConfigPresent() {
    assertThat(storage.isEnabled()).isTrue();
  }

  @Test
  void isEnabled_falseWhenNoActiveConfig() {
    when(configService.getActive()).thenReturn(null);
    assertThat(storage.isEnabled()).isFalse();
  }

  // ─── put ────────────────────────────────────────────────────────────────

  @Test
  void put_returnsLocatorWithBucketAndKey() throws StorageException {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());

    InputStream bytes = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
    StoragePutRequest request = new StoragePutRequest("my-container", "test-file.txt", "text/plain", bytes, 5L);

    StorageLocator locator = storage.put(request);

    assertThat(locator.providerId()).isEqualTo("s3");
    assertThat(locator.locator()).startsWith("test-bucket:");
    assertThat(locator.locator()).contains("test-file.txt");
  }

  @Test
  void put_usesConfiguredBucketWhenContainerHasSlash() throws StorageException {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());

    // Container with a slash/colon looks like a locator — fall back to config bucket
    InputStream bytes = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
    StoragePutRequest request = new StoragePutRequest("container/with/slashes", "file.dat", bytes);

    StorageLocator locator = storage.put(request);

    // Should use the configured default bucket
    assertThat(locator.locator()).startsWith("test-bucket:");
  }

  @Test
  void put_usesContainerAsBucketWhenSimpleName() throws StorageException {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());

    InputStream bytes = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
    StoragePutRequest request = new StoragePutRequest("custom-bucket", "file.dat", bytes);

    StorageLocator locator = storage.put(request);

    assertThat(locator.locator()).startsWith("custom-bucket:");
  }

  @Test
  void put_throwsWhenNotActive() {
    when(configService.getActive()).thenReturn(null);
    InputStream bytes = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
    StoragePutRequest request = new StoragePutRequest("bucket", "file.txt", bytes);

    assertThatThrownBy(() -> storage.put(request))
      .isInstanceOf(StorageProviderUnavailableException.class);
  }

  // ─── get ────────────────────────────────────────────────────────────────

  @Test
  void get_returnsStreamAndMetadata() throws StorageException {
    HeadObjectResponse head = HeadObjectResponse.builder()
      .contentLength(42L)
      .contentType("text/plain")
      .build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);

    InputStream fakeStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
    ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
      GetObjectResponse.builder().build(),
      AbortableInputStream.create(fakeStream)
    );
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

    StorageLocator locator = new StorageLocator("s3", "test-bucket:container/test-file.txt");
    StorageGetResponse response = storage.get(locator);

    assertThat(response.providerId()).isEqualTo("s3");
    assertThat(response.fileName()).isEqualTo("test-file.txt");
    assertThat(response.contentType()).isEqualTo("text/plain");
    assertThat(response.sizeBytes()).isEqualTo(42L);
    assertThat(response.stream()).isNotNull();
  }

  @Test
  void get_throwsStorageNotFoundWhenNoSuchKey() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
      .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());

    StorageLocator locator = new StorageLocator("s3", "test-bucket:missing/key.txt");

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageNotFoundException.class);
  }

  @Test
  void get_throwsWhenWrongProvider() {
    StorageLocator locator = new StorageLocator("gridfs", "somecontainer:someoid");

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageNotFoundException.class)
      .hasMessageContaining("gridfs");
  }

  @Test
  void get_throwsMalformedLocator() {
    StorageLocator locator = new StorageLocator("s3", "no-separator-here");

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageNotFoundException.class)
      .hasMessageContaining("Malformed");
  }

  // ─── delete ─────────────────────────────────────────────────────────────

  @Test
  void delete_callsS3DeleteObject() throws StorageException {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
      .thenReturn(DeleteObjectResponse.builder().build());

    StorageLocator locator = new StorageLocator("s3", "test-bucket:container/file.txt");
    storage.delete(locator);

    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void delete_isIdempotentWhenNoSuchKey() throws StorageException {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
      .thenThrow(NoSuchKeyException.builder().message("No such key").build());

    // Should not throw — idempotent
    StorageLocator locator = new StorageLocator("s3", "test-bucket:container/missing.txt");
    storage.delete(locator);
  }

  @Test
  void delete_throwsWhenWrongProvider() {
    StorageLocator locator = new StorageLocator("gridfs", "containerMongoId:fileOid");

    assertThatThrownBy(() -> storage.delete(locator))
      .isInstanceOf(StorageNotFoundException.class);

    verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  // ─── locator helpers ────────────────────────────────────────────────────

  @Test
  void buildKey_combinesContainerAndFileName() {
    assertThat(S3FileStorage.buildKey("container-id", "myfile.txt")).isEqualTo("container-id/myfile.txt");
  }

  @Test
  void buildKey_stripsLeadingSlash() {
    assertThat(S3FileStorage.buildKey("/container", "/file.txt")).isEqualTo("container/file.txt");
  }

  @Test
  void splitLocator_splitsBucketAndKey() throws StorageNotFoundException {
    String[] parts = S3FileStorage.splitLocator("my-bucket:path/to/file.txt");
    assertThat(parts[0]).isEqualTo("my-bucket");
    assertThat(parts[1]).isEqualTo("path/to/file.txt");
  }

  @Test
  void splitLocator_throwsOnMissingSeparator() {
    assertThatThrownBy(() -> S3FileStorage.splitLocator("no-separator-here"))
      .isInstanceOf(StorageNotFoundException.class);
  }

  @Test
  void extractFileName_extractsLastSegment() {
    assertThat(S3FileStorage.extractFileName("container/sub/myfile.dat")).isEqualTo("myfile.dat");
    assertThat(S3FileStorage.extractFileName("myfile.dat")).isEqualTo("myfile.dat");
  }

  @Test
  void extractFileName_fallsBackWhenBlank() {
    assertThat(S3FileStorage.extractFileName("")).isEqualTo("file");
    assertThat(S3FileStorage.extractFileName(null)).isEqualTo("file");
  }

  @Test
  void resolveBucket_usesContainerAsOverrideWhenSimpleName() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setBucket("default-bucket");

    assertThat(S3FileStorage.resolveBucket(cfg, "custom-bucket")).isEqualTo("custom-bucket");
  }

  @Test
  void resolveBucket_usesDefaultBucketWhenContainerHasSlash() {
    S3StorageConfig cfg = new S3StorageConfig();
    cfg.setBucket("default-bucket");

    assertThat(S3FileStorage.resolveBucket(cfg, "container/with/path")).isEqualTo("default-bucket");
  }
}
