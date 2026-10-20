package system.showing;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public final class ShowingModels {
    private ShowingModels() {}

    public record Create(
            @NotNull @Positive Long houseId,
            @NotNull @Positive Long customerId,
            @NotNull @Positive Long brokerId,
            @NotNull @Future LocalDateTime appointmentTime,
            @Size(max = 5000) String remark) {}

    public record Reschedule(
            @NotNull @Future LocalDateTime appointmentTime,
            @NotNull @Min(0) Long version,
            @Size(max = 5000) String remark) {}

    public record Complete(
            @NotNull @PastOrPresent LocalDateTime actualTime,
            @NotNull @Min(0) Long version,
            @Size(max = 5000) String remark) {}

    public record Cancel(@NotBlank @Size(max = 500) String reason, @NotNull @Min(0) Long version) {}

    public record Feedback(
            @NotBlank @Size(max = 5000) String content,
            @NotBlank String intention,
            @Future LocalDateTime nextContactAt,
            @NotNull @Min(0) Long version) {}
}
