package de.dlr.shepard.v2.aas.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * IDTA AAS Registry {@code ShellDescriptor} payload per IDTA-01002 §3.2.
 *
 * <p>POSTed to {@code {registryUrl}/shell-descriptors} by
 * {@code AasRegistryClient} (AAS1-reg). The descriptor tells registry
 * clients where to find this shell and which asset it represents.
 *
 * <p>Scope: one {@code EndpointIO} per interface; AAS1-reg ships
 * {@code AAS-3.1} only (the full AAS Repository interface). Submodel
 * descriptors are out of scope for AAS1-reg.
 */
public record AasShellDescriptorIO(
  String id,
  String idShort,
  AssetInformationIO assetInformation,
  List<EndpointIO> endpoints
) {
  /**
   * Minimal IDTA asset information block.
   *
   * @param assetKind      always {@code "Instance"} for Collection-backed shells
   * @param globalAssetId  {@code urn:shepard:asset:{collectionAppId}}
   */
  public record AssetInformationIO(String assetKind, String globalAssetId) {}

  /**
   * One endpoint entry per AAS interface type.
   *
   * @param interfaceName        IDTA interface string (e.g. {@code "AAS-3.1"})
   * @param protocolInformation  transport details
   */
  public record EndpointIO(
    @JsonProperty("interface") String interfaceName,
    ProtocolInformationIO protocolInformation
  ) {}

  /**
   * Transport-level endpoint details per IDTA-01002.
   *
   * @param href                    fully-qualified URL; shell ID is percent-encoded
   * @param endpointProtocol        always {@code "HTTP"}
   * @param endpointProtocolVersion list of supported HTTP versions
   */
  public record ProtocolInformationIO(
    String href,
    String endpointProtocol,
    List<String> endpointProtocolVersion
  ) {}
}
