package system.system;

import jakarta.validation.constraints.*;

import java.util.List;

public final class SystemModels {
    private SystemModels() {}

    public record StoreWrite(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String address,
            @NotNull Boolean enabled) {}

    public record UserCreate(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}") String username,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 32) String phone,
            @NotNull @Positive Long storeId,
            Long brokerId,
            @NotEmpty @Size(max = 1) List<@NotNull @Positive Long> roleIds) {}

    public record UserUpdate(
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 32) String phone,
            @NotNull @Positive Long storeId,
            @NotNull Boolean enabled,
            @NotEmpty @Size(max = 1) List<@NotNull @Positive Long> roleIds,
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 500) String reason) {}

    public record ResetPassword(@NotBlank @Size(min = 10, max = 72) String password) {}
}
