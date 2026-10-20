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
