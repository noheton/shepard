package de.dlr.shepard.v2.references.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * URDF-FILEREF-PICKER-SEARCHABLE — the compact wire shape for one entry in the
 * instance-wide accessible-URDF list ({@code GET /v2/references/urdf}).
 *
 * <p>Unlike the polymorphic {@link ReferenceV2IO}, this shape carries the parent
 * Collection identity so the frontend picker can render a disambiguating
 * "{@code <name> — <collection>}" label — the same {@code .urdf} name can appear
 * in several collections, and the user chooses across them by human-facing name,
 * never by pasting a UUID.
 *
 * @param appId            UUID v7 of the URDF singleton FileReference (the value the
 *                         picker resolves to; feeds {@code ?urdfFileAppId=} on the
 *                         render page).
 * @param name             the reference's human-facing name (e.g. {@code kr210-r2700-urdf}).
 * @param dataObjectAppId  UUID v7 of the parent DataObject.
 * @param collectionAppId  UUID v7 of the parent Collection (nullable if unresolved).
 * @param collectionName   the parent Collection's display name (nullable).
 */
@Schema(
  name = "AccessibleUrdf",
  description =
    "One accessible URDF singleton FileReference for the searchable picker: appId + name " +
    "plus parent DataObject/Collection identity for a disambiguating label."
)
public record AccessibleUrdfIO(
  @Schema(description = "UUID v7 appId of the URDF singleton FileReference.") String appId,
  @Schema(description = "Human-facing reference name.") String name,
  @Schema(description = "UUID v7 appId of the parent DataObject.") String dataObjectAppId,
  @Schema(description = "UUID v7 appId of the parent Collection.", nullable = true) String collectionAppId,
  @Schema(description = "Parent Collection display name.", nullable = true) String collectionName
) {}
