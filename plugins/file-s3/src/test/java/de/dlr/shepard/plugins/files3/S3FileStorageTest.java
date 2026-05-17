package de.dlr.shepard.plugins.files3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.StorageProviderUnavailableException;
import de.dlr.shepard.storage.StoragePutRequest;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Unit tests for {@link S3FileStorage} — structural invariants and
 * mocked happy/error paths exercised without a live S3 endpoint.
 *
 * <p>The mocked {@link S3Client} and {@link S3Presigner} are injected
 * via reflection (the fields are private-volatile, assigned in
 * {@code @PostConstruct init()}). The test bypasses {@code init()} and
 * sets the {@code enabled} flag directly so each test can assume an
 * already-configured adapter.
 *
 * <p>Integration tests against a real Garage container are deferred
 * (FS1b acceptance suite, tracked in aidocs/16 §FS1b test column).
 */
class S3FileStorageTest {

  private static final String TEST_BUCKET = "test-bucket";
  private static final String TEST_CONTAINER = "container123";
  private static final String TEST_FILE = "test.csv";
  private static final String TEST_KEY = TEST_CONTAINER + "/some-uuid";
  private static final String PROVIDER_ID = "s3";

  private S3Client s3;
  private S3Presigner presigner;
  private S3FileStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    s3 = mock(S3Client.class);
    presigner = mock(S3Presigner.class);
    storage = new S3FileStorage();
    setField(storage, "s3", s3);
    setField(storage, "presigner", presigner);
    setField(storage, "bucket", TEST_BUCKET);
    setField(storage, "enabled", true);
  }

  // ── structural invariants ──────────────────────────────────────────

  @Test
  void idIsS3() {
    assertThat(storage.id()).isEqualTo("s3");
  }

  @Test
  void isDisabledByDefault() {
    S3FileStorage fresh = new S3FileStorage();
    assertThat(fresh.isEnabled()).isFalse();
  }

  @Test
  void isEnabledAfterSetup() {
    assertThat(storage.isEnabled()).isTrue();
  }

  // ── put ───────────────────────────────────────────────────────────

  @Test
  void put_returnsS3Locator() throws StorageException {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());

    StoragePutRequest req = new StoragePutRequest(
      TEST_CONTAINER, TEST_FILE,
      "text/csv", new ByteArrayInputStream("data".getBytes()), 4L, null
    );

    StorageLocator locator = storage.put(req);

    assertThat(locator.providerId()).isEqualTo(PROVIDER_ID);
    assertThat(locator.locator()).startsWith(TEST_CONTAINER + "/");
    // key fragment after the container prefix must be a valid UUID
    String uuid = locator.locator().substring((TEST_CONTAINER + "/").length());
    assertThat(uuid).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void put_withAssignedObjectKey_usesProvidedKey() throws StorageException {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());

    StoragePutRequest req = new StoragePutRequest(
      TEST_CONTAINER, TEST_FILE, null,
      new ByteArrayInputStream(new byte[0]), 0L, "my-migration-key"
    );

    StorageLocator locator = storage.put(req);

    assertThat(locator.locator()).isEqualTo(TEST_CONTAINER + "/my-migration-key");
  }

  @Test
  void put_throwsStorageExceptionOnS3Error() {
    S3Exception s3ex = buildS3Exception("Forbidden");
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenThrow(s3ex);

    StoragePutRequest req = new StoragePutRequest(TEST_CONTAINER, TEST_FILE,
      new ByteArrayInputStream(new byte[0]));

    assertThatThrownBy(() -> storage.put(req))
      .isInstanceOf(StorageException.class)
      .hasMessageContaining(TEST_CONTAINER);
  }

  @Test
  void put_throwsWhenDisabled() {
    setFieldUnchecked(storage, "enabled", false);
    StoragePutRequest req = new StoragePutRequest(TEST_CONTAINER, TEST_FILE,
      new ByteArrayInputStream(new byte[0]));

    assertThatThrownBy(() -> storage.put(req))
      .isInstanceOf(StorageProviderUnavailableException.class);
  }

  @Test
  void put_setsContentDispositionHeader() throws StorageException {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());
    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

    StoragePutRequest req = new StoragePutRequest(TEST_CONTAINER, "my file.csv",
      "text/csv", new ByteArrayInputStream("x".getBytes()), 1L, null);
    storage.put(req);

    verify(s3).putObject(captor.capture(), any(RequestBody.class));
    assertThat(captor.getValue().contentDisposition()).contains("my file.csv");
  }

  // ── get ───────────────────────────────────────────────────────────

  @Test
  void get_returnsStorageGetResponse() throws StorageException {
    byte[] payload = "hello".getBytes();
    GetObjectResponse meta = GetObjectResponse.builder()
      .contentLength((long) payload.length)
      .contentType("text/plain")
      .contentDisposition("attachment; filename=\"" + TEST_FILE + "\"")
      .build();
    @SuppressWarnings("unchecked")
    ResponseInputStream<GetObjectResponse> stream =
      mock(ResponseInputStream.class);
    when(stream.response()).thenReturn(meta);
    when(s3.getObject(any(GetObjectRequest.class))).thenReturn(stream);

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);
    StorageGetResponse resp = storage.get(locator);

    assertThat(resp.providerId()).isEqualTo(PROVIDER_ID);
    assertThat(resp.fileName()).isEqualTo(TEST_FILE);
    assertThat(resp.contentType()).isEqualTo("text/plain");
    assertThat(resp.sizeBytes()).isEqualTo((long) payload.length);
    assertThat(resp.stream()).isSameAs(stream);
  }

  @Test
  void get_throwsStorageNotFoundOnNoSuchKey() {
    when(s3.getObject(any(GetObjectRequest.class)))
      .thenThrow(NoSuchKeyException.builder().message("not found").build());

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageNotFoundException.class);
  }

  @Test
  void get_throwsStorageProviderUnavailableOnS3Error() {
    S3Exception s3ex = buildS3Exception("ServiceUnavailable");
    when(s3.getObject(any(GetObjectRequest.class))).thenThrow(s3ex);

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageProviderUnavailableException.class);
  }

  @Test
  void get_throwsOnWrongProvider() {
    StorageLocator locator = new StorageLocator("gridfs", TEST_KEY);

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageException.class)
      .hasMessageContaining("gridfs");
  }

  @Test
  void get_throwsWhenDisabled() {
    setFieldUnchecked(storage, "enabled", false);
    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);

    assertThatThrownBy(() -> storage.get(locator))
      .isInstanceOf(StorageProviderUnavailableException.class);
  }

  // ── delete ────────────────────────────────────────────────────────

  @Test
  void delete_callsDeleteObjectRequest() throws StorageException {
    ArgumentCaptor<DeleteObjectRequest> captor =
      ArgumentCaptor.forClass(DeleteObjectRequest.class);

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);
    storage.delete(locator);

    verify(s3).deleteObject(captor.capture());
    assertThat(captor.getValue().key()).isEqualTo(TEST_KEY);
    assertThat(captor.getValue().bucket()).isEqualTo(TEST_BUCKET);
  }

  @Test
  void delete_isIdempotentOnNoSuchKey() throws StorageException {
    when(s3.deleteObject(any(DeleteObjectRequest.class)))
      .thenThrow(NoSuchKeyException.builder().message("gone").build());

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);
    // Should NOT throw — idempotent contract
    storage.delete(locator);
  }

  @Test
  void delete_throwsStorageProviderUnavailableOnS3Error() {
    S3Exception s3ex = buildS3Exception("InternalError");
    when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3ex);

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);

    assertThatThrownBy(() -> storage.delete(locator))
      .isInstanceOf(StorageProviderUnavailableException.class);
  }

  @Test
  void delete_throwsWhenDisabled() {
    setFieldUnchecked(storage, "enabled", false);
    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);

    assertThatThrownBy(() -> storage.delete(locator))
      .isInstanceOf(StorageProviderUnavailableException.class);
  }

  // ── presignedUploadUrl ───────────────────────────────────────────

  @Test
  void presignedUploadUrl_returnsPresentOptional() throws Exception {
    URI presignedUri = URI.create("https://s3.example.com/bucket/key?X-Amz-Signature=abc");
    PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
    when(presignedPut.url()).thenReturn(presignedUri.toURL());
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);

    Optional<de.dlr.shepard.storage.FileStorage.PresignedPut> result =
      storage.presignedUploadUrl(TEST_CONTAINER, TEST_FILE, Duration.ofMinutes(15));

    assertThat(result).isPresent();
    assertThat(result.get().uploadUrl()).isEqualTo(presignedUri);
    // assignedOid should be a UUID
    assertThat(result.get().assignedOid())
      .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(result.get().expiresAt()).isNotNull();
  }

  @Test
  void presignedUploadUrl_throwsWhenDisabled() {
    setFieldUnchecked(storage, "enabled", false);

    assertThatThrownBy(
      () -> storage.presignedUploadUrl(TEST_CONTAINER, TEST_FILE, Duration.ofMinutes(5))
    ).isInstanceOf(StorageProviderUnavailableException.class);
  }

  // ── presignedDownloadUrl ─────────────────────────────────────────

  @Test
  void presignedDownloadUrl_returnsPresentOptional() throws Exception {
    URI presignedUri = URI.create("https://s3.example.com/bucket/key?X-Amz-Signature=xyz");
    PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
    when(presignedGet.url()).thenReturn(presignedUri.toURL());
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);
    Optional<URI> result =
      storage.presignedDownloadUrl(locator, TEST_FILE, Duration.ofMinutes(5));

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(presignedUri);
  }

  @Test
  void presignedDownloadUrl_throwsWhenDisabled() {
    setFieldUnchecked(storage, "enabled", false);
    StorageLocator locator = new StorageLocator(PROVIDER_ID, TEST_KEY);

    assertThatThrownBy(
      () -> storage.presignedDownloadUrl(locator, TEST_FILE, Duration.ofMinutes(5))
    ).isInstanceOf(StorageProviderUnavailableException.class);
  }

  // ── presignedExportUrl ───────────────────────────────────────────

  @Test
  void presignedExportUrl_returnsPresignedGetUri() throws Exception {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());

    URI presignedUri = URI.create("https://s3.example.com/bucket/exports/key?X-Amz-Signature=abc");
    PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
    when(presignedGet.url()).thenReturn(presignedUri.toURL());
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

    Optional<URI> result = storage.presignedExportUrl(
      "export-uuid", new byte[]{1, 2, 3}, "export.zip", Duration.ofMinutes(30));

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(presignedUri);
  }

  @Test
  void presignedExportUrl_keysObjectUnderExportsPrefix() throws Exception {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().build());
    PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
    when(presignedGet.url())
      .thenReturn(URI.create("https://s3/exports/k?sig=x").toURL());
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);
    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

    storage.presignedExportUrl("mykey", new byte[1], "x.zip", Duration.ofHours(1));

    verify(s3).putObject(captor.capture(), any(RequestBody.class));
    assertThat(captor.getValue().key()).isEqualTo("exports/mykey");
    assertThat(captor.getValue().contentType()).isEqualTo("application/zip");
  }

  @Test
  void presignedExportUrl_throwsWhenDisabled() {
    setFieldUnchecked(storage, "enabled", false);

    assertThatThrownBy(
      () -> storage.presignedExportUrl("key", new byte[]{1, 2, 3}, "export.zip", Duration.ofMinutes(30))
    ).isInstanceOf(StorageProviderUnavailableException.class);
  }

  // ── helpers ───────────────────────────────────────────────────────

  private static void setField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Could not set field " + name, e);
    }
  }

  private static void setFieldUnchecked(Object target, String name, Object value) {
    setField(target, name, value);
  }

  private static S3Exception buildS3Exception(String errorCode) {
    return S3Exception.builder()
      .message(errorCode)
      .awsErrorDetails(AwsErrorDetails.builder()
        .errorCode(errorCode)
        .errorMessage(errorCode)
        .build())
      .build();
  }
}
