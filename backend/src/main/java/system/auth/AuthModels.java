package system.auth;

import jakarta.validation.constraints.*;

public final class AuthModels {
    private AuthModels() {}

    public record Login(
            @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {}

    public record Bootstrap(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}") String username,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 64) String displayName,
            @NotBlank @Size(max = 64) String storeName) {}

    public record ChangePassword(
            @NotBlank @Size(max = 72) String oldPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {}
}
