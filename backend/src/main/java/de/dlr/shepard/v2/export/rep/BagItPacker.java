package de.dlr.shepard.v2.export.rep;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs a content map into a <strong>BagIt 1.0</strong> (RFC 8493)
 * compliant ZIP archive.
 *
 * <p>The produced bag has the following structure:
 * <pre>
 *   bagit.txt                        (tag file — BagIt declaration)
 *   bag-info.txt                     (tag file — metadata)
 *   manifest-sha256.txt              (payload manifest — checksums of data/ files)
 *   tagmanifest-sha256.txt           (tag manifest — checksums of tag files)
 *   data/
 *     ro-crate-metadata.json         (RO-Crate 1.1 root descriptor)
 *     PROV-O.jsonld                  (PROV-O JSON-LD provenance graph)
 * </pre>
 *
 * <p>Per RFC 8493 §2.1.3, the {@code Payload-Oxum} in {@code bag-info.txt}
 * is {@code <totalOctetCount>.<streamCount>}.
 *
 * <p>TPL14 — part of the Regulatory Evidence Pack feature.
 */
public class BagItPacker {

  /**
   * Pack the supplied payload files into a BagIt-compliant ZIP.
   *
   * @param payloadFiles map of file name (without {@code data/} prefix) → UTF-8 content bytes.
   *                     Typically: {@code "ro-crate-metadata.json"} and {@code "PROV-O.jsonld"}.
   * @param bagInfo      optional extra key→value pairs to include in {@code bag-info.txt}.
   *                     Pass an empty map or {@code null} for defaults only.
   * @return the raw bytes of the BagIt ZIP archive
   * @throws IOException if ZIP writing fails (in practice: OOM only — writes to byte array)
   */
  public byte[] pack(Map<String, byte[]> payloadFiles, Map<String, String> bagInfo) throws IOException {
    // --- 1. Compute payload checksums and Oxum. -----------------------
    Map<String, String> payloadManifest = new LinkedHashMap<>();
    long totalOctetCount = 0L;
    for (Map.Entry<String, byte[]> e : payloadFiles.entrySet()) {
      String dataPath = "data/" + e.getKey();
      String sha256 = sha256Hex(e.getValue());
      payloadManifest.put(dataPath, sha256);
      totalOctetCount += e.getValue().length;
    }
    int streamCount = payloadFiles.size();
    String oxum = totalOctetCount + "." + streamCount;

    // --- 2. Build tag file content. -----------------------------------
    byte[] bagitTxt = buildBagitTxt();
    byte[] bagInfoTxt = buildBagInfoTxt(oxum, bagInfo);
    byte[] manifestSha256 = buildManifest(payloadManifest);

    // --- 3. Compute tag manifest (checksums of tag files). ------------
    Map<String, String> tagManifest = new LinkedHashMap<>();
    tagManifest.put("bagit.txt", sha256Hex(bagitTxt));
    tagManifest.put("bag-info.txt", sha256Hex(bagInfoTxt));
    tagManifest.put("manifest-sha256.txt", sha256Hex(manifestSha256));
    byte[] tagManifestTxt = buildManifest(tagManifest);

    // --- 4. Write ZIP. ------------------------------------------------
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(baos)) {
      addEntry(zip, "bagit.txt", bagitTxt);
      addEntry(zip, "bag-info.txt", bagInfoTxt);
      addEntry(zip, "manifest-sha256.txt", manifestSha256);
      addEntry(zip, "tagmanifest-sha256.txt", tagManifestTxt);
      for (Map.Entry<String, byte[]> e : payloadFiles.entrySet()) {
        addEntry(zip, "data/" + e.getKey(), e.getValue());
      }
    }
    return baos.toByteArray();
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private static byte[] buildBagitTxt() {
    String content = "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n";
    return content.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] buildBagInfoTxt(String oxum, Map<String, String> extra) {
    StringBuilder sb = new StringBuilder();
    sb.append("BagIt-Profile-Identifier: https://w3id.org/ro/bagit/profile\n");
    sb.append("Bagging-Software: shepard-backend (TPL14)\n");
    sb.append("Payload-Oxum: ").append(oxum).append('\n');
    if (extra != null) {
      for (Map.Entry<String, String> e : extra.entrySet()) {
        // RFC 8493 §2.2.2: header field names are case-insensitive; emit as-is.
        sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
      }
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] buildManifest(Map<String, String> entries) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : entries.entrySet()) {
      // RFC 8493 §2.1.4: <checksum>  <path>  (two spaces between).
      sb.append(e.getValue()).append("  ").append(e.getKey()).append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void addEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    entry.setSize(data.length);
    zip.putNextEntry(entry);
    zip.write(data);
    zip.closeEntry();
  }

  static String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(data);
      StringBuilder hex = new StringBuilder(64);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed by the Java spec to be available.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
