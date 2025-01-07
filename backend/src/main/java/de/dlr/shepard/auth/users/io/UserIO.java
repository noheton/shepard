package de.dlr.shepard.auth.users.io;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "User")
public class UserIO {

  @Schema(readOnly = true, required = true)
  private String username;

  private String firstName;

  private String lastName;

  private String email;

  @Schema(readOnly = true, required = true)
  private Long[] subscriptionIds;

  @Schema(readOnly = true, required = true)
  private UUID[] apiKeyIds;

  public UserIO(User user) {
    this.username = user.getUsername();
    this.firstName = user.getFirstName();
    this.lastName = user.getLastName();
    this.email = user.getEmail();
    this.subscriptionIds = user.getSubscriptions().stream().map(Subscription::getId).toArray(Long[]::new);
    this.apiKeyIds = user.getApiKeys().stream().map(ApiKey::getUid).toArray(UUID[]::new);
  }
}
