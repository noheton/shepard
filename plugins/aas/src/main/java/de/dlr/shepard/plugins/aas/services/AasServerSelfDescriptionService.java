package de.dlr.shepard.plugins.aas.services;

import de.dlr.shepard.plugins.aas.daos.AasRegistrationDAO;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.plugins.aas.v2.io.AasServerSelfDescriptionIO;
import de.dlr.shepard.plugins.aas.v2.io.AasServerSelfDescriptionIO.RegistryRegistration;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Assembles the {@code /v2/aas/.well-known/aas-server} self-description
 * payload per {@code aidocs/52 §4a.5}. Pure-read, no side effects, no
 * auth — the document only reflects capability flags + counts.
 *
 * <p>v1 reports:
 * <ul>
 *   <li>{@code enabled} from {@code shepard.aas.enabled}</li>
 *   <li>{@code aasApiProfile} from {@code shepard.aas.api-profile}
 *       (default {@code Submodel-Repository-Read-3.1})</li>
 *   <li>{@code endpoints} — {@code /v2/aas/shells} (AAS1a)</li>
 *   <li>{@code supportedSubmodelTemplates} — names of non-retired
 *       {@code ShepardTemplate} rows with
 *       {@code templateKind=AAS_SUBMODEL_TEMPLATE}</li>
 *   <li>{@code shellCount} — total non-deleted Collection count
 *       (permission-agnostic; one Shell per Collection per AAS1a)</li>
 *   <li>{@code registryRegistrations} — synced external IDTA AAS
 *       Registry URLs from the {@code :AasRegistration} outbox
 *       (AAS1-reg); empty when no registry is configured or no
 *       registration has succeeded yet</li>
 * </ul>
 *
 * <p>AAS1l: {@link #configService} provides the runtime-mutable
 * registryUrl for the fallback registration entry when the outbox
 * has no synced rows. Null-safe — falls back to the deploy-time
 * {@code @ConfigProperty} when the service is not available.
 */
@RequestScoped
public class AasServerSelfDescriptionService {

  public static final String AAS_SUBMODEL_TEMPLATE_KIND = "AAS_SUBMODEL_TEMPLATE";
  static final String DEFAULT_API_PROFILE = "Submodel-Repository-Read-3.1";
  static final String IDTA_REGISTRY_KIND = "idta-registry";

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  AasRegistrationDAO registrationDAO;

  /** AAS1l: runtime-mutable config singleton. Null-safe. */
  @Inject
  AasConfigService configService;

  @ConfigProperty(name = "shepard.aas.enabled", defaultValue = "false")
  boolean enabled;

  @ConfigProperty(name = "shepard.aas.api-profile", defaultValue = DEFAULT_API_PROFILE)
  String apiProfile;

  @ConfigProperty(name = "shepard.aas.registry.url")
  Optional<String> registryUrl;

  public AasServerSelfDescriptionIO describe() {
    Map<String, String> endpoints = new LinkedHashMap<>();
    // AAS1a: Shell repository listing endpoint.
    endpoints.put("shells", "/v2/aas/shells");

    List<String> supportedTemplates = templateDAO
      .list(AAS_SUBMODEL_TEMPLATE_KIND, false)
      .stream()
      .map(t -> t.getName())
      .distinct()
      .sorted()
      .toList();

    // AAS1-reg: report registries where ≥1 shell has been synced.
    // Fall back to the configured URL when the outbox is empty (e.g.
    // first boot before the startup sync completes) so discovery still
    // works for clients reading the well-known before sync finishes.
    List<RegistryRegistration> registrations = buildRegistrations();

    return new AasServerSelfDescriptionIO(
      enabled,
      apiProfile,
      endpoints,
      supportedTemplates,
      collectionDAO.countAll(),
      registrations
    );
  }

  /**
   * Returns the {@link RegistryRegistration} list for the well-known doc.
   *
   * <p>Strategy (in priority order):
   * <ol>
   *   <li>Query the outbox for distinct registries with ≥1 SYNCED row.</li>
   *   <li>If the outbox is empty, resolve the effective registry URL
   *       (AAS1l: runtime :AasConfig preferred over deploy-time
   *       {@code @ConfigProperty}) and advertise it with status "pending"
   *       so discovery works even before the first sync completes.</li>
   *   <li>If neither applies, return an empty list.</li>
   * </ol>
   */
  List<RegistryRegistration> buildRegistrations() {
    List<String> synced = registrationDAO.distinctSyncedRegistryUrls();
    if (!synced.isEmpty()) {
      return synced.stream().map(url -> new RegistryRegistration(url, IDTA_REGISTRY_KIND)).toList();
    }
    // AAS1l: prefer runtime :AasConfig registryUrl over deploy-time @ConfigProperty.
    Optional<String> effectiveUrl = registryUrl;
    if (configService != null) {
      try {
        AasConfig cfg = configService.current();
        if (cfg.getRegistryUrl() != null && !cfg.getRegistryUrl().isBlank()) {
          effectiveUrl = Optional.of(cfg.getRegistryUrl());
        }
      } catch (Exception e) {
        // fall through to deploy-time value
      }
    }
    // No confirmed registrations yet — advertise the configured URL if present.
    return effectiveUrl
      .filter(url -> !url.isBlank())
      .map(url -> List.of(new RegistryRegistration(url, IDTA_REGISTRY_KIND)))
      .orElse(List.of());
  }
}
