package system.system;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final SystemService service;

    public SystemController(SystemService service) {
        this.service = service;
    }

    @GetMapping("/stores")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<?> stores() {
        return ApiResponse.success(service.stores());
    }

    @PostMapping("/stores")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> createStore(@Valid @RequestBody SystemModels.StoreWrite b) {
        return ApiResponse.success(service.createStore(b));
    }

    @PutMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> store(
            @PathVariable long id, @Valid @RequestBody SystemModels.StoreWrite b) {
        service.updateStore(id, b);
        return ApiResponse.success(null);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> roles() {
        return ApiResponse.success(service.roles());
    }

    @GetMapping("/workbench")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<?> workbench() {
        return ApiResponse.success(service.workbench());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.users(keyword, page, size));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> user(@Valid @RequestBody SystemModels.UserCreate b) {
        return ApiResponse.success(service.createUser(b));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> user(
            @PathVariable long id, @Valid @RequestBody SystemModels.UserUpdate b) {
        service.updateUser(id, b);
        return ApiResponse.success(null);
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> password(
            @PathVariable long id, @Valid @RequestBody SystemModels.ResetPassword b) {
        service.resetPassword(id, b.password());
        return ApiResponse.success(null);
    }

    @GetMapping("/brokers")
    @PreAuthorize("hasAnyAuthority('system:read','house:write','showing:write')")
    public ApiResponse<?> brokers(@RequestParam(required = false) Long storeId) {
        return ApiResponse.success(service.brokers(storeId));
    }
}
