package system.owner;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class OwnerModels {
    private OwnerModels() {}

    public record Write(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 32) String phone,
            @Size(max = 255) String address,
            @Size(max = 5000) String remark,
            Long storeId,
            Long brokerId,
            Long version) {}

    public record Entrustment(
            @NotNull @Positive Long houseId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull Boolean exclusive,
            @DecimalMin("0") @DecimalMax("1") BigDecimal commissionRate,
            @Size(max = 5000) String remark) {}

    public record End(@NotBlank @Size(max = 500) String reason) {}
}
