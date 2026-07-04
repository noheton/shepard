package de.dlr.shepard.spi.view;

import java.io.IOException;
import java.io.InputStream;

/**
 * V2CONV-A1b (E3) — focus-byte resolution seam handed to a
 * {@link ViewRecipeRenderer} on the {@link RenderRequest}.
 *
 * <p>Renderers are ServiceLoader POJOs (not CDI beans) so they cannot
 * {@code @Inject SingletonFileReferenceService} to reach a focus
 * FileReference's bytes. A byte-rooted renderer (the thermography OTvis
 * frame/heatmap renderers) therefore needs the dispatcher — which <i>is</i>
 * a CDI bean — to pass it a narrow handle that opens the focus
 * FileReference's content stream by appId. This functional interface is
 * that handle.
 *
 * <p>The implementation lives in the dispatcher
 * ({@code ShapesRenderRest}) and delegates to
 * {@code SingletonFileReferenceService.getPayload(appId).getInputStream()}.
 * It is permission-gated upstream of the renderer: the dispatcher only
 * builds + supplies the resolver after the caller has passed the
 * reference-rooted Read check, so a renderer that calls
 * {@link #open(String)} cannot escalate beyond what the request already
 * authorised.
 *
 * <p>Kept to {@link InputStream} (not {@code NamedInputStream}) so the
 * SPI package pulls no {@code common.mongoDB} dependency — a plugin
 * compiling against the SPI sees only {@code java.io}.
 */
@FunctionalInterface
public interface FocusPayloadResolver {
  /**
   * Open the byte stream for the FileReference identified by {@code appId}.
   * The caller owns the returned stream and must close it.
   *
   * @param appId the singleton FileReference appId
   * @return an open input stream over the reference's content
   * @throws IOException when the reference cannot be resolved or its
   *         bytes cannot be opened
   */
  InputStream open(String appId) throws IOException;
}
