package de.dlr.shepard.v2.publish.resources;

import jakarta.ws.rs.core.UriInfo;

final class PublishRestUtils {

  private PublishRestUtils() {}

  /**
   * Build a fully-qualified URL at the supplied application-relative path
   * using the request's own scheme + host + port.
   */
  static String absoluteUrl(UriInfo uriInfo, String applicationPath) {
    if (uriInfo == null) return applicationPath;
    var base = uriInfo.getBaseUri();
    String scheme = base.getScheme();
    String host = base.getHost();
    int port = base.getPort();
    StringBuilder sb = new StringBuilder();
    sb.append(scheme).append("://").append(host);
    if (port > 0 && !((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
      sb.append(":").append(port);
    }
    sb.append(applicationPath.startsWith("/") ? applicationPath : "/" + applicationPath);
    return sb.toString();
  }
}
