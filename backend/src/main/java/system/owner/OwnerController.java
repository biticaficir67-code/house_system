package system.owner;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.*;

@RestController
@RequestMapping("/api/owners")
public class OwnerController {
    private final OwnerService service;
    private final FollowUpService follow;

    public OwnerController(OwnerService service, FollowUpService follow) {
        this.service = service;
        this.follow = follow;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('owner:read')")
    public ApiResponse<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('owner:read')")
    public ApiResponse<?> detail(@PathVariable long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> create(@Valid @RequestBody OwnerModels.Write b) {
        return ApiResponse.success(service.create(b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody OwnerModels.Write b) {
        return ApiResponse.success(service.update(id, b));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/follow-ups")
    @PreAuthorize("hasAuthority('owner:read')")
    public ApiResponse<?> follow(@PathVariable long id) {
        return ApiResponse.success(follow.list("owner", id));
    }

    @PostMapping("/{id}/follow-ups")
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> follow(
            @PathVariable long id, @Valid @RequestBody FollowUpService.Write b) {
        return ApiResponse.success(follow.create("owner", id, b));
    }

    @GetMapping("/{id}/entrustments")
    @PreAuthorize("hasAuthority('owner:read')")
    public ApiResponse<?> entrustments(@PathVariable long id) {
        return ApiResponse.success(service.entrustments(id));
    }

    @PostMapping("/{id}/entrustments")
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> entrust(
            @PathVariable long id, @Valid @RequestBody OwnerModels.Entrustment b) {
        return ApiResponse.success(service.entrust(id, b));
    }

    @PutMapping("/{id}/entrustments/{entrustmentId}/end")
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> end(
            @PathVariable long id,
            @PathVariable long entrustmentId,
            @Valid @RequestBody OwnerModels.End b) {
        service.end(id, entrustmentId, b);
        return ApiResponse.success(null);
    }

    @GetMapping("/reminders")
    @PreAuthorize("hasAuthority('owner:read')")
    public ApiResponse<?> reminders() {
        return ApiResponse.success(service.reminders());
    }

    @PutMapping("/reminders/{id}/read")
    @PreAuthorize("hasAuthority('owner:write')")
    public ApiResponse<?> read(@PathVariable long id) {
        service.readReminder(id);
        return ApiResponse.success(null);
    }
}
