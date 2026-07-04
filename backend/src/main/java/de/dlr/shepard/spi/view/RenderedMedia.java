package de.dlr.shepard.spi.view;

/**
 * V2CONV-A1 — a renderer's binary/media output for content-negotiated
 * {@code POST /v2/shapes/render}.
 *
 * <p>When a caller's {@code Accept} header asks for a non-JSON media type that
 * a matched {@link ViewRecipeRenderer} declares in {@link
 * ViewRecipeRenderer#producibleMedia()}, the dispatcher calls
 * {@link ViewRecipeRenderer#renderMedia(RenderRequest, String)} and returns this
 * payload verbatim with {@code Content-Type: mediaType}. This is the seam that
 * moves per-format rendering (thermography heatmap PNG, glTF, URDF/USD export)
 * onto the single generic render endpoint — no per-format REST paths.
 *
 * @param mediaType the concrete IANA media type of {@code bytes}, e.g.
 *                  {@code image/png}, {@code model/gltf+json}
 * @param bytes     the rendered payload; never null (use the JSON view-model
 *                  path instead of an empty media payload)
 */
public record RenderedMedia(String mediaType, byte[] bytes) {}
