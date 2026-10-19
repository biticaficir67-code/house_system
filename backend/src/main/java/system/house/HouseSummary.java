package system.house;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HouseSummary(
        Long id,
        String city,
        String district,
        String businessArea,
        String communityName,
        BigDecimal buildingAreaSqm,
        Integer rooms,
        Integer halls,
        Integer bathrooms,
        String floorLevel,
        Integer totalFloors,
        String orientation,
        String decoration,
        Boolean hasElevator,
        BigDecimal listingPriceYuan,
        LocalDate listingDate,
        String status,
        Long brokerId
) {
}
