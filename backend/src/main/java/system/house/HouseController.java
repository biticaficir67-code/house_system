package system.house;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;

@RestController
@RequestMapping("/api/houses")
public class HouseController {
    private final HouseService service;

    public HouseController(HouseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('house:read')")
    public ApiResponse<?> list(@ModelAttribute HouseModels.Search search) {
        return ApiResponse.success(service.search(search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('house:read')")
    public ApiResponse<?> detail(@PathVariable long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('house:write')")
    public ApiResponse<?> create(@Valid @RequestBody HouseModels.Write body) {
        return ApiResponse.success(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('house:write')")
    public ApiResponse<?> update(
            @PathVariable long id, @Valid @RequestBody HouseModels.Write body) {
        return ApiResponse.success(service.update(id, body));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('house:write')")
    public ApiResponse<?> status(
            @PathVariable long id, @Valid @RequestBody HouseModels.StatusChange body) {
        return ApiResponse.success(service.status(id, body));
    }

    @PutMapping("/{id}/verification")
    @PreAuthorize("hasAuthority('house:verify')")
    public ApiResponse<?> verify(
            @PathVariable long id, @Valid @RequestBody HouseModels.Verification body) {
        return ApiResponse.success(service.verify(id, body));
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasAuthority('house:assign')")
    public ApiResponse<?> assign(
            @PathVariable long id, @Valid @RequestBody HouseModels.Assignment body) {
        return ApiResponse.success(service.assign(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('house:write')")
    public ApiResponse<?> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/status-logs")
    @PreAuthorize("hasAuthority('house:read')")
    public ApiResponse<?> logs(@PathVariable long id) {
        return ApiResponse.success(service.logs(id));
    }
}
