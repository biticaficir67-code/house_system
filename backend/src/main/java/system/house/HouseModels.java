package system.house;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class HouseModels {
    private HouseModels() {}

    public record Write(
            @NotBlank @Size(max = 32) String city,
            @NotBlank @Size(max = 32) String district,
            @Size(max = 128) String businessArea,
            @NotBlank @Size(max = 128) String communityName,
            Long communityId,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
                    BigDecimal buildingAreaSqm,
            @Min(1) @Max(50) Integer rooms,
            @Min(0) @Max(50) Integer halls,
            @Min(0) @Max(50) Integer bathrooms,
            @NotBlank String floorLevel,
            @NotNull @Min(1) @Max(300) Integer totalFloors,
            @NotEmpty List<String> orientation,
            @NotBlank String decoration,
            Boolean hasElevator,
            @NotBlank String propertyUsage,
            String buildingType,
            String layoutStructure,
            String constructionStructure,
            String ownershipType,
            @DecimalMin(value = "0", inclusive = false) BigDecimal internalAreaSqm,
            LocalDate listingDate,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 2)
                    BigDecimal listingPriceYuan,
            @Size(max = 20000) String coreSelling,
            @Size(max = 20000) String communityIntro,
            @Size(max = 20000) String surrounding,
            @Size(max = 64) String sourceListingId,
            Long storeId,
            Long brokerId,
            @Min(0) Long version) {}

    public record Search(
            Integer page,
            Integer size,
            String district,
            String communityName,
            String status,
            Long storeId,
            Long brokerId,
            Integer rooms,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minArea,
            BigDecimal maxArea) {}

    public record StatusChange(
            @NotBlank String status,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {}

    public record Verification(
            @NotNull Boolean approved,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {}

    public record Assignment(
            @NotNull @Positive Long storeId,
            @NotNull @Positive Long brokerId,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {}

    public record Changed(long houseId, long actorId) {}
}
