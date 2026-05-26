package de.dlr.shepard.v2.vocabularies.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-014 — request body for {@code POST /v2/vocabularies/personal}.
 *
 * <p>The caller supplies a short {@code name} (used as the last segment of the
 * personal vocabulary URI: {@code urn:shepard:personal:<userAppId>:<name>}) and
 * an optional human-readable {@code description}.
 *
 * <p>Name constraints enforced by the REST layer:
 * <ul>
 *   <li>Non-null, non-blank.</li>
 *   <li>Matches {@code [a-z0-9][a-z0-9_-]{0,63}} (URL-safe slug).</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@Schema(name = "PersonalVocabularyRequest")
public class PersonalVocabularyRequestIO {

  @Schema(
    description = "Short slug for the personal vocabulary (URL-safe: [a-z0-9][a-z0-9_-]{0,63}). " +
      "Used as the last segment of the canonical URI.",
    required = true,
    minLength = 1,
    maxLength = 64
  )
  private String name;

  @Schema(
    description = "Optional free-text description of the vocabulary's scope.",
    nullable = true
  )
  private String description;
}
