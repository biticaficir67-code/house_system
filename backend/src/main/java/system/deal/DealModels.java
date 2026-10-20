package system.deal;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DealModels {
    private DealModels() {}

    public record Create(
            @NotNull @Positive Long houseId,
            @NotNull @Positive Long customerId,
            @NotNull @Positive Long brokerId,
            @NotNull @PastOrPresent LocalDateTime dealTime,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 2)
                    BigDecimal transactionPriceYuan,
            @DecimalMin("0") BigDecimal commissionYuan,
            @Size(max = 5000) String remark) {}

    public record Edit(
            @NotNull @PastOrPresent LocalDateTime dealTime,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 2)
                    BigDecimal transactionPriceYuan,
            @DecimalMin("0") BigDecimal commissionYuan,
            @Size(max = 5000) String remark,
            @NotNull @Min(0) Long version) {}

    public record Verification(
            @NotNull Boolean approved,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {}

    public record Cancel(@NotBlank @Size(max = 500) String reason, @NotNull @Min(0) Long version) {}
}
