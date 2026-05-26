package de.dlr.shepard.context.export;

import de.dlr.shepard.common.exceptions.ShepardException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in {@link PayloadExportHandler} for {@code FileReference} rows.
 *
 * <p>Writes the reference JSON sidecar and all associated file payloads.
 * Supports per-payload OID selection from {@link ExportContext}.
 */
@ApplicationScoped
public class FileReferenceExportHandler implements PayloadExportHandler {

  @Inject
  FileBundleReferenceService fileReferenceService;

  @Override
  public boolean handles(String referenceType) {
    return "FileReference".equals(referenceType);
  }

  @Override
  public void export(ExportBuilder builder, BasicReference reference, ExportContext ctx) throws IOException {
    FileBundleReference fileRef = fileReferenceService.getReference(
      ctx.collectionId(),
      ctx.dataObjectId(),
      reference.getShepardId(),
      null
    );
    builder.addReference(new FileReferenceIO(fileRef), fileRef.getCreatedBy());

    List<String> requestedOids = ctx.perPayload() == null ? null : ctx.perPayload().fileOids();
    if (requestedOids == null || requestedOids.isEmpty()) {
      // No per-payload pick — fetch everything in bulk.
      List<NamedInputStream> payloads = Collections.emptyList();
      try {
        payloads = fileReferenceService.getAllPayloads(
          ctx.collectionId(),
          ctx.dataObjectId(),
          reference.getShepardId()
        );
      } catch (ShepardException e) {
        Log.warn("Cannot access file payload during export");
      }
      for (var nis : payloads) {
        if (nis.getInputStream() != null) writeFilePayload(builder, nis);
      }
      return;
    }

    // Per-payload pick: fetch only the requested OIDs. Stale OIDs are silently
    // skipped unless strict mode is active.
    Set<String> knownOids = new HashSet<>();
    for (ShepardFile f : fileRef.getFiles()) {
      if (f.getOid() != null) knownOids.add(f.getOid());
    }
    List<String> stale = new ArrayList<>();
    List<String> hits = new ArrayList<>();
    for (String oid : requestedOids) {
      if (knownOids.contains(oid)) hits.add(oid);
      else stale.add(oid);
    }
    if (!stale.isEmpty()) {
      if (ctx.strict()) {
        throw new BadRequestException(
          "Unknown file OID(s) for FileReference id=" + reference.getShepardId() + ": " + stale
        );
      }
      builder.addSelectionWarning(
        "FileReference id=" + reference.getShepardId() + ": stale file OIDs skipped " + stale
      );
    }
    for (String oid : hits) {
      try {
        var nis = fileReferenceService.getPayload(
          ctx.collectionId(),
          ctx.dataObjectId(),
          reference.getShepardId(),
          oid,
          null
        );
        if (nis != null && nis.getInputStream() != null) writeFilePayload(builder, nis);
      } catch (ShepardException e) {
        Log.warnf("Cannot access file payload oid=%s during export", oid);
      }
    }
  }

  private static void writeFilePayload(ExportBuilder builder, NamedInputStream nis) throws IOException {
    var nameSplitted = nis.getName().split("\\.", 2);
    var filename = nameSplitted.length > 1 ? nis.getOid() + "." + nameSplitted[1] : nis.getOid();
    builder.addPayload(nis.getInputStream().readAllBytes(), filename, nis.getName());
  }
}
