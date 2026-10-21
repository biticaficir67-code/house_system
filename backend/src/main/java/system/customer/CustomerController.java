package system.customer;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    private final FollowUpService follow;

    public CustomerController(CustomerService service, FollowUpService follow) {
        this.service = service;
        this.follow = follow;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(keyword, status, page, size));
    }

    @GetMapping("/duplicates")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<?> duplicates(
            @RequestParam String phone, @RequestParam(required = false) Long excludeId) {
        return ApiResponse.success(follow.duplicates("customer", phone, excludeId));
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> assignment(
            @PathVariable long id, @Valid @RequestBody FollowUpService.Assignment b) {
        return ApiResponse.success(follow.assign("customer", id, b));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<?> logs(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(follow.logs("customer", id, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<?> detail(@PathVariable long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<?> create(@Valid @RequestBody CustomerModels.Write b) {
        return ApiResponse.success(service.create(b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<?> update(
            @PathVariable long id, @Valid @RequestBody CustomerModels.Write b) {
        return ApiResponse.success(service.update(id, b));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<?> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/demand")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<?> demand(
            @PathVariable long id, @Valid @RequestBody CustomerModels.Demand b) {
        return ApiResponse.success(service.demand(id, b));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<?> status(
            @PathVariable long id, @Valid @RequestBody CustomerModels.StatusChange b) {
        return ApiResponse.success(service.status(id, b));
    }

    @GetMapping("/{id}/matches")
    @PreAuthorize("hasAnyAuthority('customer:read') and hasAuthority('house:read')")
    public ApiResponse<?> matches(
            @PathVariable long id, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.matches(id, limit));
    }

    @GetMapping("/{id}/follow-ups")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<?> follow(@PathVariable long id) {
        return ApiResponse.success(follow.list("customer", id));
    }

    @PostMapping("/{id}/follow-ups")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<?> follow(
            @PathVariable long id, @Valid @RequestBody FollowUpService.Write b) {
        return ApiResponse.success(follow.create("customer", id, b));
    }
}
