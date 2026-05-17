package de.dlr.shepard.v2.template.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for
 * {@code POST /v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}}.
 *
 * <p>All fields are optional. When {@code name} is absent or blank the
 * server falls back to: template name + {@code "-"} + current millis.
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
}
