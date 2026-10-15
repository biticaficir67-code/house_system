package cn.csu.housesystem.house.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record HouseUpsertRequest(
        Long ownerId,
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 32) String city,
        @Size(max = 32) String district,
        @NotBlank @Size(max = 128) String communityName,
        @Size(max = 128) String businessArea,
        @NotNull @DecimalMin(value = "0.01") BigDecimal buildingAreaSqm,
        @NotNull @Min(1) @Max(20) Integer rooms,
        @NotNull @Min(0) @Max(20) Integer halls,
        @NotNull @Min(0) @Max(20) Integer bathrooms,
        @Size(max = 16) String floorLevel,
        @Min(1) @Max(200) Integer totalFloors,
        List<String> orientation,
        @Size(max = 16) String decoration,
        @Size(max = 32) String buildingType,
        Boolean hasElevator,
        @Size(max = 32) String propertyUsage,
        @DecimalMin(value = "0.00") BigDecimal listingPriceYuan,
        @Size(max = 4000) String coreSelling,
        @Size(max = 4000) String communityIntro,
        @Size(max = 4000) String surrounding
) {
}
