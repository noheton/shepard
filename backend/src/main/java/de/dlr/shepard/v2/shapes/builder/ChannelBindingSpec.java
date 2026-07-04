package de.dlr.shepard.v2.shapes.builder;

/**
 * V2CONV-B8 — one channel binding entry in a {@link ViewRecipeSpec}. Maps
 * onto the {@code channelBindings[]} array in a {@code VIEW_RECIPE} template
 * body as consumed by {@code POST /v2/shapes/render}.
 *
 * @param role            human-readable role name ({@code "x"}, {@code "y"},
 *                        {@code "temperature"}, …) used by the renderer to
 *                        assign the resolved channel to the right visual slot
 * @param channelSelector JSON-encoded selector object that identifies the
 *                        timeseries channel; serialised as a string so the body
 *                        remains renderer-agnostic and the 5-tuple → appId
 *                        migration (aidocs/platform/87) can swap the inner
 *                        format without reshaping the outer body
 * @param unit            optional QUDT unit IRI, e.g.
 *                        {@code "http://qudt.org/vocab/unit/MilliM"}; null = no
 *                        unit constraint
 * @param required        when {@code true} the renderer must surface a
 *                        MISSING status when the channel cannot be resolved
 */
public record ChannelBindingSpec(
  String role,
  String channelSelector,
  String unit,
  boolean required
) {
  /** Convenience factory for a required binding with no unit constraint. */
  public static ChannelBindingSpec required(String role, String channelSelector) {
    return new ChannelBindingSpec(role, channelSelector, null, true);
  }

  /** Convenience factory for an optional binding with no unit constraint. */
  public static ChannelBindingSpec optional(String role, String channelSelector) {
    return new ChannelBindingSpec(role, channelSelector, null, false);
  }
}
