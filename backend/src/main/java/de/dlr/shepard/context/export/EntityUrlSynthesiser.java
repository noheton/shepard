package de.dlr.shepard.context.export;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;

/**
 * Produces the canonical relative URL the user would have hit to receive subscription
 * notifications for a given exported entity. The path segments come from {@link Constants} so a
 * rename in the JAX-RS resources flows through automatically.
 *
 * <p>The synthesised URLs are <em>relative</em> (e.g. {@code /collections/15/dataObjects/25}),
 * matching the path portion of the absolute URL the runtime {@link
 * de.dlr.shepard.common.filters.SubscriptionFilter} matches against. Subscription regexes that
 * embed scheme and host (e.g. {@code http://example.org/collections/15}) need to use a
 * permissive prefix (e.g. {@code .*?/collections/15}) to match exports — this is the same
 * trade-off documented for the export feature in {@code ExportSelection.Metadata}.
 */
public final class EntityUrlSynthesiser {

  private EntityUrlSynthesiser() {}

  /** {@code /collections/{id}}. */
  public static String urlFor(Collection collection) {
    if (collection == null) return null;
    return "/" + Constants.COLLECTIONS + "/" + collection.getShepardId();
  }

  /** {@code /collections/{cid}/dataObjects/{id}}. */
  public static String urlFor(DataObject dataObject) {
    if (dataObject == null) return null;
    Collection parent = dataObject.getCollection();
    if (parent == null) return null;
    return urlFor(parent) + "/" + Constants.DATA_OBJECTS + "/" + dataObject.getShepardId();
  }

  /**
   * Reference URL keyed by the reference's runtime type:
   * {@code /collections/{cid}/dataObjects/{did}/{kindSegment}/{rid}}.
   *
   * <p>The {@code kindSegment} is one of
   * {@link Constants#FILE_REFERENCES}, {@link Constants#TIMESERIES_REFERENCES},
   * {@link Constants#STRUCTURED_DATA_REFERENCES}, {@link Constants#URI_REFERENCES} or
   * {@link Constants#BASIC_REFERENCES} — chosen by the same mapping the JAX-RS resources use.
   */
  public static String urlFor(BasicReference reference) {
    if (reference == null) return null;
    DataObject dataObject = reference.getDataObject();
    if (dataObject == null) return null;
    String parentUrl = urlFor(dataObject);
    if (parentUrl == null) return null;
    return parentUrl + "/" + segmentFor(reference.getType()) + "/" + reference.getShepardId();
  }

  /** {@code /labJournalEntries/{id}}. */
  public static String urlFor(LabJournalEntry entry) {
    if (entry == null) return null;
    return "/" + Constants.LAB_JOURNAL_ENTRIES + "/" + entry.getId();
  }

  private static String segmentFor(String referenceType) {
    if (referenceType == null) return Constants.BASIC_REFERENCES;
    return switch (referenceType) {
      case "FileReference" -> Constants.FILE_REFERENCES;
      case "TimeseriesReference" -> Constants.TIMESERIES_REFERENCES;
      case "StructuredDataReference" -> Constants.STRUCTURED_DATA_REFERENCES;
      case "URIReference" -> Constants.URI_REFERENCES;
      default -> Constants.BASIC_REFERENCES;
    };
  }
}
