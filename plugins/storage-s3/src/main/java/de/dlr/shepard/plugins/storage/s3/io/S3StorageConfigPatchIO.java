package de.dlr.shepard.plugins.storage.s3.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * FS1b — request body for
 * {@code PATCH /v2/admin/storage/s3/config} (RFC 7396
 * merge-patch).
 *
 * <p>RFC 7396 semantics:
 *
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "remove / clear the field"
 *       (where the schema allows).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>Credential fields ({@code accessKeyId} in the credential sense,
 * {@code secretAccessKeyCipher}, {@code secretAccessKeyHash}) are
 * read-only via this path — touching any of them raises
 * {@code storage.s3.config.read-only-field}. Operators set
 * credentials via {@code POST .../credential}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class S3StorageConfigPatchIO {

  private Boolean enabled;

  private String endpointUrl;
  private boolean endpointUrlTouched;

  private String region;
  private boolean regionTouched;

  private String bucket;
  private boolean bucketTouched;

  private String bucketPrefix;
  private boolean bucketPrefixTouched;

  private Boolean forcePathStyle;

  private String sseAlgorithm;
  private boolean sseAlgorithmTouched;

  private Long multipartThresholdBytes;
  private Integer connectionTimeoutSeconds;
  private Integer requestTimeoutSeconds;

  /** Sentinel — when {@code true}, the call is rejected. */
  private boolean accessKeyIdTouched;
  /** Sentinel — when {@code true}, the call is rejected. */
  private boolean secretAccessKeyCipherTouched;
  /** Sentinel — when {@code true}, the call is rejected. */
  private boolean secretAccessKeyHashTouched;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getEndpointUrl() {
    return endpointUrl;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setEndpointUrl(String endpointUrl) {
    this.endpointUrl = endpointUrl;
    this.endpointUrlTouched = true;
  }

  public boolean isEndpointUrlTouched() {
    return endpointUrlTouched;
  }

  public String getRegion() {
    return region;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setRegion(String region) {
    this.region = region;
    this.regionTouched = true;
  }

  public boolean isRegionTouched() {
    return regionTouched;
  }

  public String getBucket() {
    return bucket;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setBucket(String bucket) {
    this.bucket = bucket;
    this.bucketTouched = true;
  }

  public boolean isBucketTouched() {
    return bucketTouched;
  }

  public String getBucketPrefix() {
    return bucketPrefix;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setBucketPrefix(String bucketPrefix) {
    this.bucketPrefix = bucketPrefix;
    this.bucketPrefixTouched = true;
  }

  public boolean isBucketPrefixTouched() {
    return bucketPrefixTouched;
  }

  public Boolean getForcePathStyle() {
    return forcePathStyle;
  }

  public void setForcePathStyle(Boolean forcePathStyle) {
    this.forcePathStyle = forcePathStyle;
  }

  public String getSseAlgorithm() {
    return sseAlgorithm;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setSseAlgorithm(String sseAlgorithm) {
    this.sseAlgorithm = sseAlgorithm;
    this.sseAlgorithmTouched = true;
  }

  public boolean isSseAlgorithmTouched() {
    return sseAlgorithmTouched;
  }

  public Long getMultipartThresholdBytes() {
    return multipartThresholdBytes;
  }

  public void setMultipartThresholdBytes(Long multipartThresholdBytes) {
    this.multipartThresholdBytes = multipartThresholdBytes;
  }

  public Integer getConnectionTimeoutSeconds() {
    return connectionTimeoutSeconds;
  }

  public void setConnectionTimeoutSeconds(Integer connectionTimeoutSeconds) {
    this.connectionTimeoutSeconds = connectionTimeoutSeconds;
  }

  public Integer getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
  }

  public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds) {
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }

  public boolean isAccessKeyIdTouched() {
    return accessKeyIdTouched;
  }

  public boolean isSecretAccessKeyCipherTouched() {
    return secretAccessKeyCipherTouched;
  }

  public boolean isSecretAccessKeyHashTouched() {
    return secretAccessKeyHashTouched;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("accessKeyId")
  public void setAccessKeyId(String ignored) {
    this.accessKeyIdTouched = true;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("secretAccessKeyCipher")
  public void setSecretAccessKeyCipher(String ignored) {
    this.secretAccessKeyCipherTouched = true;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("secretAccessKeyHash")
  public void setSecretAccessKeyHash(String ignored) {
    this.secretAccessKeyHashTouched = true;
  }

  /** Catch-all setter — also rejects attempts to patch secret key directly. */
  @JsonProperty("secretKey")
  public void setSecretKey(String ignored) {
    this.secretAccessKeyCipherTouched = true;
  }
}
