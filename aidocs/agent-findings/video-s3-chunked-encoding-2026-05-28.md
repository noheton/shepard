---
stage: feature-defined
last-stage-change: 2026-05-28
audience: contributor
---

# Video upload via S3FileStorage → Garage — chunked-encoding signature mismatch

**Date:** 2026-05-28 (PM)
**Branch:** main (source fix); deploy gated on backend rebuild (Jandex)
**Severity:** MAJOR — LUMEN seed's video reference is the only seeded VideoStreamReference and it never lands; the SPI works for files (hero images, run artefacts) but fails for videos.

---

## Symptom

`POST /v2/data-objects/{appId}/video-stream-references` returns HTTP 500. Backend log:

```
ERROR de.dlr.shepa.v2.video.resou.VideoStreamReferenceV2Rest:
  VID1a upload: storage error — S3 put failed for key '_shepard_videos/<uuid>': null
```

The `null` is `e.awsErrorDetails().errorMessage()` returning empty — the AWS SDK couldn't parse Garage's error body into AWS's XML shape.

Garage log shows the real error:

```
PUT /shepard-files/_shepard_videos/<uuid>
Response: error 400 Bad Request, Bad request: Invalid content sha256 hash:
  Invalid character 'S' at position 0
```

The `'S' at position 0` is the literal `STREAMING-AWS4-HMAC-SHA256-PAYLOAD` token the AWS SDK sends in the `x-amz-content-sha256` header when chunked encoding (aws-chunked) is enabled. Garage v1.0 doesn't accept chunked-signed payloads of that token form.

## Why file uploads work but videos don't

The same `S3FileStorage.put(...)` method handles both — but it branches on whether `StoragePutRequest.sizeBytes()` is known:

```java
if (request.sizeBytes() != null) {
  body = RequestBody.fromInputStream(request.bytes(), request.sizeBytes());  // streams ⇒ chunked
} else {
  body = RequestBody.fromBytes(request.bytes().readAllBytes());               // buffers ⇒ single SigV4 hash
}
```

The seed's hero image path supplies a null `sizeBytes` (it just hands over an InputStream from a temp file), so the SDK uses `fromBytes(readAllBytes())` — the SDK pre-hashes the full body and sends the actual SHA-256 in `x-amz-content-sha256`. Garage accepts that fine.

Videos go through the multipart upload path (`VideoStreamReferenceV2Rest`), and Quarkus' `FileUpload` provides the multipart's temp-file size to the service, which forwards it as `contentLength`. That non-null `sizeBytes` triggers `RequestBody.fromInputStream(stream, sizeBytes)`, which lets the SDK enable aws-chunked + streaming signatures. Garage rejects the `STREAMING-AWS4-HMAC-SHA256-PAYLOAD` token.

## Fix

In `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`,
add `chunkedEncodingEnabled(false)` to both the `S3Client` and `S3Presigner` `S3Configuration` builders:

```java
S3ClientBuilder builder = S3Client.builder()
  .httpClientBuilder(UrlConnectionHttpClient.builder())
  .region(Region.of(resolvedRegion))
  .serviceConfiguration(S3Configuration.builder()
    .pathStyleAccessEnabled(pathStyleAccess)
    .chunkedEncodingEnabled(false)                  // ← added
    .build());

S3Presigner.Builder presignerBuilder = S3Presigner.builder()
  .region(Region.of(resolvedRegion))
  .serviceConfiguration(S3Configuration.builder()
    .pathStyleAccessEnabled(pathStyleAccess)
    .chunkedEncodingEnabled(false)                  // ← added
    .build());
```

Disabling chunked encoding makes the SDK pre-hash the body and send a single SigV4 signature, which Garage accepts unconditionally. Real AWS S3 also accepts this form — it's the OPTIONAL form, not Garage-specific.

## Deploy

Source fix committed on `main`. The fix **cannot be hot-patched on the live nuclide instance** because Quarkus' AOT augmentation step bakes the `@ApplicationScoped` bean class into `/deployments/quarkus/transformed-bytecode.jar` at build time:

```
de/dlr/shepard/plugins/files3/S3FileStorage.class       # 23157 B — augmented w/ CDI hooks
de/dlr/shepard/plugins/files3/S3FileStorage_Bean.class  # CDI metadata
de/dlr/shepard/plugins/files3/S3FileStorage_ClientProxy.class  # CDI client proxy
```

The runtime class loader picks the augmented `S3FileStorage.class` out of `transformed-bytecode.jar` BEFORE looking at `/deployments/lib/main/de.dlr.shepard.plugins.shepard-plugin-file-s3-...jar`. Replacing my fresh-built (non-augmented) class into either the plugin JAR or the `lib/main` JAR has no effect — verified by JAR swap + restart producing the same Garage 400. Replacing the class IN `transformed-bytecode.jar` directly with the non-augmented bytes makes the backend restart-loop with class-verification errors (the CDI augmentation hooks are missing).

Deploy is therefore gated on a backend image rebuild. The Jandex hang
(`org.jboss.jandex.CompositeIndex.getClassByName` inside `quarkus-arc AnnotationsTransformer.apply()`) prevents that today.

## Test data

Bare `aws s3 cp` against Garage works (key path + bucket + creds all correct):

```
upload: ../test.mp4 to s3://shepard-files/_shepard_videos/test-bare-put.mp4
```

Bare `aws s3 cp` with a non-ASCII Content-Disposition (en-dash) fails with `InvalidHeaderValue` — a separate issue worth noting for the LUMEN seed's `name=` query parameter, but NOT the cause of the seed's video failure (the seed sends `filename="lumen-hotfire.mp4"` clean ASCII in the multipart, and the en-dash is only in the query-param `name=` which becomes the `:VideoStreamReference.name` field on Neo4j, not the S3 Content-Disposition).

## Cross-references

- `aidocs/63 ADR-0024` — Garage chosen over MinIO; this is the first known compat gap.
- `aidocs/agent-findings/backend-jandex-hang-investigation-2026-05-28.md` — the deploy blocker.
- AWS SDK v2 docs: `S3Configuration.Builder.chunkedEncodingEnabled(Boolean)` —
  https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/migration-client-configuration.html
- Garage S3 protocol notes — Garage's signature validation predates the AWS chunked-signature spec; `UNSIGNED-PAYLOAD` and full-body SHA-256 are accepted; `STREAMING-AWS4-HMAC-SHA256-PAYLOAD` is not.
