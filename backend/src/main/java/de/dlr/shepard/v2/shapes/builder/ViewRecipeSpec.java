package de.dlr.shepard.v2.shapes.builder;

import java.util.List;

/**
 * V2CONV-B8 — typed descriptor for a {@code VIEW_RECIPE} template that a
 * {@link de.dlr.shepard.spi.payload.PayloadKind} can declare via
 * {@link de.dlr.shepard.spi.payload.PayloadKind#viewShapeDescriptor()}.
 *
 * <p>When a {@code PayloadKind} returns a non-null {@code ViewRecipeSpec},
 * {@link de.dlr.shepard.v2.shapes.seeder.KindShapeSeeder} seeds (or
 * idempotently updates) a {@code ShepardTemplate} of kind
 * {@code VIEW_RECIPE} whose name is {@code "<kind.name()>-view-shape"}.
 * The seeded body satisfies {@code TemplateBodyValidator} (contains
 * {@code "renderer"} key) and can be fed directly to
 * {@code POST /v2/shapes/render}.
 *
 * @param viewRecipeShape optional IRI identifying the concrete renderer
 *                        plugin shape (dispatched via
 *                        {@link de.dlr.shepard.spi.view.ViewRecipeRendererRegistry});
 *                        null = no renderer claim (in-tree DECLARED fallback)
 * @param renderer        renderer hint string, e.g. {@code "tresjs"},
 *                        {@code "echarts"}, {@code "plotly"}; MUST be
 *                        non-null so the body passes {@code TemplateBodyValidator}
 * @param channelBindings channel binding declarations; nullable / empty produces
 *                        an empty bindings array (valid, zero-binding view)
 */
public record ViewRecipeSpec(
  String viewRecipeShape,
  String renderer,
  List<ChannelBindingSpec> channelBindings
) {}
