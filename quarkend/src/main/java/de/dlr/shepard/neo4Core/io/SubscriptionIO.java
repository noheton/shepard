package de.dlr.shepard.neo4Core.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.util.RequestMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "Subscription")
public class SubscriptionIO {

  @Schema(accessMode = AccessMode.READ_ONLY)
  private Long id;

  @NotBlank
  @Schema(nullable = true)
  private String name;

  @Pattern(
    regexp = "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"
  )
  @Schema(nullable = true)
  private String callbackURL;

  @NotBlank
  @Schema(nullable = true)
  private String subscribedURL;

  @NotNull
  @Schema(nullable = true)
  private RequestMethod requestMethod;

  @Schema(accessMode = AccessMode.READ_ONLY)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(accessMode = AccessMode.READ_ONLY)
  private Date createdAt;

  public SubscriptionIO(Subscription sub) {
    this.id = sub.getId();
    this.name = sub.getName();
    this.callbackURL = sub.getCallbackURL();
    this.subscribedURL = sub.getSubscribedURL();
    this.requestMethod = sub.getRequestMethod();
    this.createdAt = sub.getCreatedAt();
    this.createdBy = sub.getCreatedBy() != null ? sub.getCreatedBy().getUsername() : null;
  }
}
