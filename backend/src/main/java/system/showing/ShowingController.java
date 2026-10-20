package system.showing;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/showings")
public class ShowingController {
    private final ShowingService service;

    public ShowingController(ShowingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('showing:read')")
    public ApiResponse<?> list(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(houseId, customerId, status, from, to, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('showing:read')")
    public ApiResponse<?> detail(@PathVariable long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('showing:write')")
    public ApiResponse<?> create(@Valid @RequestBody ShowingModels.Create b) {
        return ApiResponse.success(service.create(b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('showing:write')")
    public ApiResponse<?> reschedule(
            @PathVariable long id, @Valid @RequestBody ShowingModels.Reschedule b) {
        return ApiResponse.success(service.reschedule(id, b));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('showing:write')")
    public ApiResponse<?> complete(
            @PathVariable long id, @Valid @RequestBody ShowingModels.Complete b) {
        return ApiResponse.success(service.complete(id, b));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('showing:write')")
    public ApiResponse<?> cancel(
            @PathVariable long id, @Valid @RequestBody ShowingModels.Cancel b) {
        service.cancel(id, b);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/follow-ups")
    @PreAuthorize("hasAuthority('showing:write')")
    public ApiResponse<?> feedback(
            @PathVariable long id, @Valid @RequestBody ShowingModels.Feedback b) {
        return ApiResponse.success(service.feedback(id, b));
    }
}
