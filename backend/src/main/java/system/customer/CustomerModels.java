package system.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CustomerModels {
    private CustomerModels() {}

    public record StatusChange(
            @NotBlank String status,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {}

    public record Write(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 32) String phone,
            @Size(max = 30) List<@NotBlank @Size(max = 32) String> tags,
            @Size(max = 5000) String remark,
            Long storeId,
            Long brokerId,
            Long version,
            @Valid Demand demand) {}

    public record Demand(
            @Size(max = 20) List<@NotBlank @Size(max = 32) String> districts,
            @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal minPrice,
            @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal maxPrice,
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minArea,
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal maxArea,
            @Min(1) @Max(50) Integer minRooms,
            @Min(1) @Max(50) Integer maxRooms,
            String decoration,
            LocalDate expectedPurchaseDate,
            @Size(max = 5000) String notes,
            @Min(0) Long version) {}
}
