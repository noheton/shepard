package de.dlr.shepard.plugins.ai.services;

import de.dlr.shepard.plugins.ai.daos.AiCapabilityConfigDAO;
import de.dlr.shepard.plugins.ai.entities.AiCapabilityConfig;
import de.dlr.shepard.plugins.ai.io.AiCapabilityConfigIO;
import de.dlr.shepard.spi.ai.AiCapability;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI1 — service layer for {@code :AiCapabilityConfig} nodes.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li><b>Get-or-seed.</b> {@link #getConfig(AiCapability)} returns the
 *       capability's config, seeding a disabled skeleton node if none
 *       exists yet. This gives admins something to PATCH against
 *       without requiring a separate create endpoint.</li>
 *   <li><b>Merge-patch.</b> {@link #upsertConfig(AiCapability, AiCapabilityConfigIO)}
 *       creates or merge-patches the config for the named capability.
 *       RFC 7396 semantics — null fields in the IO are left alone;
 *       explicit values are written.</li>
 *   <li><b>Availability check.</b> {@link #isEnabled(AiCapability)}
 *       returns {@code true} iff a config node exists and its
 *       {@code enabled} flag is {@code Boolean.TRUE}.</li>
 * </ol>
 */
@ApplicationScoped
public class AiCapabilityConfigService {

  @Inject
  AiCapabilityConfigDAO dao;

  /**
   * Return the config for the given capability, seeding a disabled
   * skeleton if none exists yet.
   *
   * @param capability the capability slot to look up
   * @return the (possibly freshly-seeded) config
   */
  public AiCapabilityConfig getConfig(AiCapability capability) {
    Optional<AiCapabilityConfig> found = dao.findByCapability(capability.name());
    if (found.isPresent()) {
      return found.get();
    }
    return seedSkeleton(capability);
  }

  /**
   * Return the config for the given capability, or
   * {@link Optional#empty()} if not yet configured. Unlike
   * {@link #getConfig(AiCapability)} this does NOT seed a skeleton —
   * it is used by the provider to check availability without side effects.
   */
  public Optional<AiCapabilityConfig> findConfig(AiCapability capability) {
    return dao.findByCapability(capability.name());
  }

  /**
   * Create or merge-patch the config for the given capability.
   * RFC 7396 semantics: null fields in the IO patch are ignored;
   * explicit values (including an explicit {@code ""} empty string
   * for optional text fields) are written.
   *
   * <p>The API key special case: if the patch carries {@code "***"}
   * (the masked sentinel returned by GET), the stored key is left
   * unchanged; any other non-null, non-empty value replaces it.
   *
   * @param capability the capability slot to update
   * @param patch      the merge-patch IO object
   * @return the saved entity
   */
  public synchronized AiCapabilityConfig upsertConfig(AiCapability capability, AiCapabilityConfigIO patch) {
    AiCapabilityConfig cfg = getConfig(capability);

    if (patch.endpointUrl != null) {
      cfg.setEndpointUrl(patch.endpointUrl.isBlank() ? null : patch.endpointUrl.strip());
    }
    if (patch.model != null) {
      cfg.setModel(patch.model.isBlank() ? null : patch.model.strip());
    }
    if (patch.apiKey != null && !patch.apiKey.equals("***")) {
      cfg.setApiKey(patch.apiKey.isBlank() ? null : patch.apiKey.strip());
    }
    if (patch.transport != null) {
      cfg.setTransport(patch.transport.isBlank() ? null : patch.transport.strip());
    }
    if (patch.guardrailsPrefix != null) {
      cfg.setGuardrailsPrefix(patch.guardrailsPrefix.isBlank() ? null : patch.guardrailsPrefix);
    }
    if (patch.guardrailsSuffix != null) {
      cfg.setGuardrailsSuffix(patch.guardrailsSuffix.isBlank() ? null : patch.guardrailsSuffix);
    }
    if (patch.maxTokens != null) {
      cfg.setMaxTokens(patch.maxTokens);
    }
    if (patch.temperature != null) {
      cfg.setTemperature(patch.temperature);
    }
    if (patch.enabled != null) {
      cfg.setEnabled(patch.enabled);
    }

    AiCapabilityConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "AI1: :AiCapabilityConfig patched (capability=%s, enabled=%s, model=%s, endpointSet=%s)",
      capability.name(),
      saved.getEnabled(),
      saved.getModel(),
      saved.getEndpointUrl() != null
    );
    return saved;
  }

  /**
   * {@code true} iff a config node for this capability exists and its
   * {@code enabled} flag is {@code Boolean.TRUE}.
   */
  public boolean isEnabled(AiCapability capability) {
    return dao.findByCapability(capability.name())
      .map(cfg -> Boolean.TRUE.equals(cfg.getEnabled()))
      .orElse(false);
  }

  /**
   * Return configs for all known {@link AiCapability} values, seeding
   * skeleton nodes for any that don't exist yet.
   */
  public List<AiCapabilityConfig> getAllConfigs() {
    List<AiCapabilityConfig> result = new ArrayList<>();
    for (AiCapability cap : AiCapability.values()) {
      result.add(getConfig(cap));
    }
    return result;
  }

  // ─── private helpers ─────────────────────────────────────────────

  private AiCapabilityConfig seedSkeleton(AiCapability capability) {
    AiCapabilityConfig skeleton = new AiCapabilityConfig();
    skeleton.setCapability(capability.name());
    skeleton.setEnabled(Boolean.FALSE);
    skeleton.setTransport("OPENAI_COMPAT");
    AiCapabilityConfig saved = dao.createOrUpdate(skeleton);
    Log.infof(
      "AI1: seeded :AiCapabilityConfig skeleton (capability=%s, appId=%s)",
      capability.name(),
      saved.getAppId()
    );
    return saved;
  }
}
