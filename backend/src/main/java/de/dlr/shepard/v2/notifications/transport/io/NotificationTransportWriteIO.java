package de.dlr.shepard.v2.notifications.transport.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * NTF1-BACKEND-CRUD — request body for
 * {@code POST /v2/admin/notifications/transports} (create) and
 * {@code PATCH /v2/admin/notifications/transports/{appId}} (RFC 7396
 * merge-patch).
 *
 * <p>Unlike {@code NotificationTransportReadIO}, this IO DOES carry
 * the write-only secret fields {@link #smtpPassword} and
 * {@link #matrixAccessToken} — operators need to send them. The
 * compile-time guarantee (omission on the read side) still holds
 * because the two IOs are physically distinct types.
 *
 * <p><b>RFC 7396 semantics (PATCH).</b> Each field carries a
 * {@code *Touched} flag set by Jackson via
 * {@code @JsonSetter(nulls = SET)}. The REST layer uses these flags to
 * distinguish "absent" (leave alone) from "explicit null" (clear). The
 * same shape mirrors {@code JupyterConfigPatchIO}.
 *
 * <p>For POST (create), every field-touched flag that callers care
 * about is set by Jackson and the REST layer treats them as
 * "set the value to whatever was on the wire (or null)".
 */
@Schema(description = "Request body for creating or patching a notification transport; includes write-only credential fields (smtpPassword, matrixAccessToken).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class NotificationTransportWriteIO {

  private String kind;
  private boolean kindTouched;

  private String name;
  private boolean nameTouched;

  private Boolean enabled;
  private boolean enabledTouched;

  // SMTP
  private String smtpHost;
  private boolean smtpHostTouched;

  private Integer smtpPort;
  private boolean smtpPortTouched;

  private String smtpUsername;
  private boolean smtpUsernameTouched;

  private String smtpPassword;
  private boolean smtpPasswordTouched;

  private String smtpFrom;
  private boolean smtpFromTouched;

  private Boolean smtpTls;
  private boolean smtpTlsTouched;

  // Matrix
  private String matrixHomeserver;
  private boolean matrixHomeserverTouched;

  private String matrixAccessToken;
  private boolean matrixAccessTokenTouched;

  private String matrixDefaultRoom;
  private boolean matrixDefaultRoomTouched;

  // ─── getters / setters ─────────────────────────────────────────────────

  public String getKind() { return kind; }
  @JsonSetter(nulls = Nulls.SET)
  public void setKind(String v) { this.kind = v; this.kindTouched = true; }
  public boolean isKindTouched() { return kindTouched; }

  public String getName() { return name; }
  @JsonSetter(nulls = Nulls.SET)
  public void setName(String v) { this.name = v; this.nameTouched = true; }
  public boolean isNameTouched() { return nameTouched; }

  public Boolean getEnabled() { return enabled; }
  @JsonSetter(nulls = Nulls.SET)
  public void setEnabled(Boolean v) { this.enabled = v; this.enabledTouched = true; }
  public boolean isEnabledTouched() { return enabledTouched; }

  public String getSmtpHost() { return smtpHost; }
  @JsonSetter(nulls = Nulls.SET)
  public void setSmtpHost(String v) { this.smtpHost = v; this.smtpHostTouched = true; }
  public boolean isSmtpHostTouched() { return smtpHostTouched; }

  public Integer getSmtpPort() { return smtpPort; }
  @JsonSetter(nulls = Nulls.SET)
  public void setSmtpPort(Integer v) { this.smtpPort = v; this.smtpPortTouched = true; }
  public boolean isSmtpPortTouched() { return smtpPortTouched; }

  public String getSmtpUsername() { return smtpUsername; }
  @JsonSetter(nulls = Nulls.SET)
  public void setSmtpUsername(String v) { this.smtpUsername = v; this.smtpUsernameTouched = true; }
  public boolean isSmtpUsernameTouched() { return smtpUsernameTouched; }

  public String getSmtpPassword() { return smtpPassword; }
  @JsonSetter(nulls = Nulls.SET)
  public void setSmtpPassword(String v) { this.smtpPassword = v; this.smtpPasswordTouched = true; }
  public boolean isSmtpPasswordTouched() { return smtpPasswordTouched; }

  public String getSmtpFrom() { return smtpFrom; }
  @JsonSetter(nulls = Nulls.SET)
  public void setSmtpFrom(String v) { this.smtpFrom = v; this.smtpFromTouched = true; }
  public boolean isSmtpFromTouched() { return smtpFromTouched; }

  public Boolean getSmtpTls() { return smtpTls; }
  @JsonSetter(nulls = Nulls.SET)
  public void setSmtpTls(Boolean v) { this.smtpTls = v; this.smtpTlsTouched = true; }
  public boolean isSmtpTlsTouched() { return smtpTlsTouched; }

  public String getMatrixHomeserver() { return matrixHomeserver; }
  @JsonSetter(nulls = Nulls.SET)
  public void setMatrixHomeserver(String v) { this.matrixHomeserver = v; this.matrixHomeserverTouched = true; }
  public boolean isMatrixHomeserverTouched() { return matrixHomeserverTouched; }

  public String getMatrixAccessToken() { return matrixAccessToken; }
  @JsonSetter(nulls = Nulls.SET)
  public void setMatrixAccessToken(String v) { this.matrixAccessToken = v; this.matrixAccessTokenTouched = true; }
  public boolean isMatrixAccessTokenTouched() { return matrixAccessTokenTouched; }

  public String getMatrixDefaultRoom() { return matrixDefaultRoom; }
  @JsonSetter(nulls = Nulls.SET)
  public void setMatrixDefaultRoom(String v) { this.matrixDefaultRoom = v; this.matrixDefaultRoomTouched = true; }
  public boolean isMatrixDefaultRoomTouched() { return matrixDefaultRoomTouched; }
}
