package system.house;

import system.common.ApiResponse;
import system.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/houses")
public class HouseController {
    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @GetMapping
    public ApiResponse<PageResponse<HouseSummary>> search(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String communityName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(houseService.search(district, communityName, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<HouseSummary> getById(@PathVariable long id) {
        return ApiResponse.success(houseService.findById(id));
    }
}
