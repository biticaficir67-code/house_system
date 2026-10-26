package system.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasAuthority('system:read')")
public class BusinessImportController {
    private final BusinessImportService service;

    public BusinessImportController(BusinessImportService service) {
        this.service = service;
    }

    public record Confirm(@NotBlank @Size(max = 80) String previewToken) {}

    @GetMapping("/catalog")
    public ApiResponse<?> catalog() {
        return ApiResponse.success(service.catalog());
    }

    @GetMapping("/quality")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<?> quality() {
        return ApiResponse.success(service.quality());
    }

    @GetMapping("/{kind}/template")
    public ResponseEntity<byte[]> template(
            @PathVariable String kind, @RequestParam(defaultValue = "xlsx") String format) {
        byte[] data = service.template(kind, format);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=import-"
                                + kind.toLowerCase(java.util.Locale.ROOT)
                                + "."
                                + format)
                .contentType(
                        MediaType.parseMediaType(
                                format.equals("csv")
                                        ? "text/csv;charset=UTF-8"
                                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping(path = "/{kind}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> preview(
            @PathVariable String kind,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long brokerId) {
        return ApiResponse.success(service.preview(kind, file, storeId, brokerId));
    }

    @PostMapping("/{kind}/commit")
    public ApiResponse<?> commit(@PathVariable String kind, @Valid @RequestBody Confirm body) {
        return ApiResponse.success(service.commit(kind, body.previewToken()));
    }
}
