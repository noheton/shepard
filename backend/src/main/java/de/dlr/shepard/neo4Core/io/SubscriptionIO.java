package de.dlr.shepard.neo4Core.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.util.RequestMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "Subscription")
public class SubscriptionIO {

  @Schema(readOnly = true)
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

  @Schema(readOnly = true)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
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
