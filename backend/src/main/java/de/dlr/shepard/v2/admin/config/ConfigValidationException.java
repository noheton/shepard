package de.dlr.shepard.v2.admin.config;

/**
 * V2CONV-A4 — thrown by {@link ConfigDescriptor#patch} when a field value
 * fails validation. The generic {@link AdminConfigRest} handler converts this
 * to a 400 RFC 7807 problem-JSON response.
 */
public class ConfigValidationException extends Exception {

  private final String problemType;
  private final String title;
  private final String detail;

  public ConfigValidationException(String problemType, String title, String detail) {
    super(title + ": " + detail);
    this.problemType = problemType;
    this.title = title;
    this.detail = detail;
  }

  public String getProblemType() {
    return problemType;
  }

  public String getTitle() {
    return title;
  }

  public String getDetail() {
    return detail;
  }
}
