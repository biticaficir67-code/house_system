package system.customer;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CustomerModels {
    private CustomerModels() {}

    public record Write(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 32) String phone,
            @Size(max = 30) List<@NotBlank @Size(max = 32) String> tags,
            @Size(max = 5000) String remark,
            Long storeId,
            Long brokerId,
            Long version) {}

    public record Demand(
            @Size(max = 20) List<@NotBlank @Size(max = 32) String> districts,
            @DecimalMin("0") BigDecimal minPrice,
            @DecimalMin("0") BigDecimal maxPrice,
            @DecimalMin("0") BigDecimal minArea,
            @DecimalMin("0") BigDecimal maxArea,
            @Min(1) Integer minRooms,
            @Min(1) Integer maxRooms,
            String decoration,
            LocalDate expectedPurchaseDate,
            @Size(max = 5000) String notes) {}
}
