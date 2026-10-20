package system.ai;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;

@RestController
@RequestMapping("/api/ai/houses/{houseId}")
public class AiController {
    private final AiService service;

    public AiController(AiService service) {
        this.service = service;
    }

    @GetMapping("/text-analysis")
    @PreAuthorize("hasAuthority('house:read')")
    public ApiResponse<?> text(@PathVariable long houseId) {
        return ApiResponse.success(service.text(houseId));
    }

    @PostMapping("/text-analysis")
    @PreAuthorize("hasAuthority('ai:use')")
    public ApiResponse<?> analyze(@PathVariable long houseId) {
        return ApiResponse.success(service.analyze(houseId));
    }

    @PostMapping("/price-predictions")
    @PreAuthorize("hasAuthority('ai:use')")
    public ApiResponse<?> predict(@PathVariable long houseId) {
        return ApiResponse.success(service.predict(houseId));
    }

    @GetMapping("/price-predictions")
    @PreAuthorize("hasAuthority('house:read')")
    public ApiResponse<?> history(@PathVariable long houseId) {
        return ApiResponse.success(service.history(houseId));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('ai:use')")
    public ApiResponse<?> requests(@PathVariable long houseId) {
        return ApiResponse.success(service.requests(houseId));
    }
}
