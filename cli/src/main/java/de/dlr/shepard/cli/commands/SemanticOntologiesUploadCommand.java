package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.OntologyBundle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code shepard-admin semantic ontologies upload --file=...
 * --id=... --iri-prefix=... [--canonical-url=...] --license=...}
 * — N1c2.
 *
 * <p>Calls {@code POST /v2/admin/semantic/ontologies} (multipart)
 * on the backend: {@code file=<bytes>} part + {@code metadata=}
 * JSON part. Server SHA-256s the bytes, writes them under
 * {@code shepard.semantic.internal.user-bundles-dir}, persists the
 * {@code :UserOntologyBundle} catalogue row. The bundle joins the
 * seed loop on next startup; run {@code semantic refresh-ontologies}
 * for an immediate import.
 */
@Command(
  name = "upload",
  mixinStandardHelpOptions = true,
  description = "Upload an operator-supplied ontology bundle (.ttl)."
)
public final class SemanticOntologiesUploadCommand extends AbstractCommand {

  static final String PATH = "/v2/admin/semantic/ontologies";

  @Option(names = { "--file" }, required = true, description = "Path to the Turtle file to upload.")
  Path file;

  @Option(names = { "--id" }, required = true, description = "Bundle id (a-z0-9_-, ≤ 64 chars).")
  String bundleId;

  @Option(names = { "--name" }, description = "Human-readable name (default: id).")
  String name;

  @Option(names = { "--iri-prefix" }, required = true, description = "Canonical IRI prefix the ontology mints terms under.")
  String iriPrefix;

  @Option(names = { "--canonical-url" }, description = "Optional canonical refresh URL.")
  String canonicalUrl;

  @Option(names = { "--license", "--licence" }, required = true, description = "SPDX-ish licence label.")
  String license;

  @Override
  protected Integer run() {
    if (file == null) {
      throw new AdminCliException("--file is required");
    }
    byte[] payload;
    try {
      payload = Files.readAllBytes(file);
    } catch (IOException e) {
      throw new AdminCliException("Could not read " + file + ": " + e.getMessage(), e);
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("id", bundleId);
    if (name != null && !name.isBlank()) metadata.put("name", name);
    metadata.put("iriPrefix", iriPrefix);
    if (canonicalUrl != null && !canonicalUrl.isBlank()) metadata.put("canonicalUrl", canonicalUrl);
    metadata.put("license", license);

    String metadataJson;
    try {
      metadataJson = new ObjectMapper().writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      throw new AdminCliException("Could not serialise metadata: " + e.getMessage(), e);
    }

    ShepardHttpClient client = buildClient();
    String filename = file.getFileName() == null ? "upload.ttl" : file.getFileName().toString();
    OntologyBundle saved = client.postMultipart(
      PATH,
      "file",
      filename,
      payload,
      "metadata",
      metadataJson,
      new TypeReference<OntologyBundle>() {}
    );

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(saved));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise upload result to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "Uploaded bundle '" + saved.getId() + "' (source=" + saved.getSource() + ", " +
      saved.getByteSize() + " bytes, sha256=" + (saved.getSha256() == null ? "-" : saved.getSha256()) +
      "). It will join the seed loop on the next restart; or run `shepard-admin semantic refresh-ontologies --bundles=" +
      saved.getId() + "` for an immediate import."
    );
    return 0;
  }
}
