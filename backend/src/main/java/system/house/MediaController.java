package system.house;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import system.common.ApiResponse;

import java.io.IOException;

@RestController
@RequestMapping("/api/houses/{houseId}/media")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('house:write')")
    public ApiResponse<?> upload(@PathVariable long houseId, @RequestParam MultipartFile file)
            throws IOException {
        return ApiResponse.success(service.upload(houseId, file));
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority('house:read')")
    public ResponseEntity<Resource> content(@PathVariable long houseId, @PathVariable long id)
            throws IOException {
        var media = service.metadata(houseId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) media.get("contentType")))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .body(service.resource(media));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('house:write')")
    public ApiResponse<?> delete(@PathVariable long houseId, @PathVariable long id) {
        service.delete(houseId, id);
        return ApiResponse.success(null);
    }
}
