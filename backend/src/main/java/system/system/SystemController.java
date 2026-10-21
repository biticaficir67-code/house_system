package system.system;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.auth.*;
import system.common.*;
import system.common.ApiResponse;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final SystemService service;
    private final RegistrationService registration;
    private final TaskService tasks;

    public SystemController(
            SystemService service, RegistrationService registration, TaskService tasks) {
        this.service = service;
        this.registration = registration;
        this.tasks = tasks;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<?> tasks(
            @RequestParam String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tasks.list(kind, page, size));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> user(@PathVariable long id) {
        return ApiResponse.success(service.user(id));
    }

    @GetMapping("/users/{id}/logs")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> logs(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.logs(id, page, size));
    }

    @PutMapping("/users/{id}/review")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> review(@PathVariable long id, @Valid @RequestBody AuthModels.Review b) {
        return ApiResponse.success(registration.review(id, b));
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
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.users(keyword, storeId, accountStatus, page, size));
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
    public ApiResponse<?> brokers(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Boolean unbound) {
        return ApiResponse.success(service.brokers(storeId, unbound));
    }
}
