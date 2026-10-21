package system.auth;

import jakarta.validation.constraints.*;

public final class AuthModels {
    private AuthModels() {}

    public record Register(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}") String username,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank String confirmPassword,
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 32) String phone) {}

    public record Reapply(
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 32) String phone,
            @Size(min = 10, max = 72) String password,
            String confirmPassword,
            @NotNull @Min(0) Long version) {}

    public record Profile(
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 32) String phone,
            @NotNull @Min(0) Long version) {}

    public record Review(
            @NotNull Boolean approved,
            @Positive Long storeId,
            @Positive Long brokerId,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {}

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
