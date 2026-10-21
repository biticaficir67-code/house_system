package system.deal;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;

@RestController
@RequestMapping("/api/deals")
public class DealController {
    private final DealService service;

    public DealController(DealService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('deal:read')")
    public ApiResponse<?> list(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(houseId, verified, verificationStatus, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('deal:read')")
    public ApiResponse<?> detail(@PathVariable long id) {
        return ApiResponse.success(service.detail(id));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('deal:read')")
    public ApiResponse<?> logs(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.logs(id, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('deal:write')")
    public ApiResponse<?> create(@Valid @RequestBody DealModels.Create b) {
        return ApiResponse.success(service.create(b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('deal:write')")
    public ApiResponse<?> edit(@PathVariable long id, @Valid @RequestBody DealModels.Edit b) {
        return ApiResponse.success(service.edit(id, b));
    }

    @PutMapping("/{id}/verification")
    @PreAuthorize("hasAuthority('deal:verify')")
    public ApiResponse<?> verify(
            @PathVariable long id, @Valid @RequestBody DealModels.Verification b) {
        return ApiResponse.success(service.verify(id, b));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('deal:write')")
    public ApiResponse<?> cancel(@PathVariable long id, @Valid @RequestBody DealModels.Cancel b) {
        return ApiResponse.success(service.cancel(id, b));
    }
}
