package de.dlr.shepard.v2.template.io;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for
 * {@code POST /v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}}.
 *
 * <p>All fields are optional. When {@code name} is absent or blank the
 * server falls back to: template name + {@code "-"} + current millis.
 *
 * <p>{@code attributes} is the form-submit leg (BTKVS-B2 / doc 125 §5.2):
 * caller-supplied values merge <em>over</em> the template body's default
 * attributes (caller wins per key) and the merged map is what the SHACL
 * shape validates — so a form rendered from the
 * {@code GET /v2/templates/{appId}/form} descriptor can round-trip user
 * input through the same V2CONV-B2 validation seam.
 */
@Data
@NoArgsConstructor
@Schema(name = "TemplateInstantiateRequest")
public class TemplateInstantiateRequestIO {

  @Schema(
    required = false,
    nullable = true,
    description = "Human-readable name for the new DataObject. Defaults to template name + timestamp suffix when omitted."
  )
  private String name;

  @Schema(
    required = false,
    nullable = true,
    description = "Caller-supplied attribute values (form input). Merged over the template body's " +
    "default attributes (caller wins per key) before SHACL validation. Keys are the descriptor's " +
    "fields[].attributeKey values."
  )
  private Map<String, String> attributes;
}
