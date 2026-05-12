package de.dlr.shepard.aas.services;

import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.v2.aas.io.AasServerSelfDescriptionIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>{@code endpoints} — currently always empty; populated by
 *       AAS1a when the Shell- / Submodel-repository surface lands</li>
 *   <li>{@code supportedSubmodelTemplates} — names of non-retired
 *       {@code ShepardTemplate} rows with
 *       {@code templateKind=AAS_SUBMODEL_TEMPLATE}</li>
 *   <li>{@code shellCount} — always 0 until AAS1a ships an
 *       {@code :AssetAdministrationShell} entity</li>
 *   <li>{@code registryRegistrations} — empty until AAS1-reg lands</li>
 * </ul>
 */
@RequestScoped
public class AasServerSelfDescriptionService {

  static final String AAS_SUBMODEL_TEMPLATE_KIND = "AAS_SUBMODEL_TEMPLATE";
  static final String DEFAULT_API_PROFILE = "Submodel-Repository-Read-3.1";

  @Inject
  ShepardTemplateDAO templateDAO;

  @ConfigProperty(name = "shepard.aas.enabled", defaultValue = "false")
  boolean enabled;

  @ConfigProperty(name = "shepard.aas.api-profile", defaultValue = DEFAULT_API_PROFILE)
  String apiProfile;

  public AasServerSelfDescriptionIO describe() {
    Map<String, String> endpoints = new LinkedHashMap<>();
    // AAS1a will fill these in when Shell / Submodel repository surfaces ship.
    // Today: only the well-known endpoint itself exists, but advertising it
    // back is redundant (the client already knows it — they just fetched it).

    List<String> supportedTemplates = templateDAO
      .list(AAS_SUBMODEL_TEMPLATE_KIND, false)
      .stream()
      .map(t -> t.getName())
      .distinct()
      .sorted()
      .toList();

    return new AasServerSelfDescriptionIO(
      enabled,
      apiProfile,
      endpoints,
      supportedTemplates,
      // shellCount: 0 until AAS1a ships :AssetAdministrationShell.
      0L,
      // AAS1-reg fills this in once the outbox is wired.
      List.of()
    );
  }
}
