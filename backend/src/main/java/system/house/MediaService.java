package system.house;

import static system.common.DataStore.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import system.common.*;
import system.security.AccessScope;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class MediaService {
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final Path root;

    public MediaService(
            DataStore db,
            ResourceAccess access,
            AccessScope scope,
            @Value("${app.upload.directory:./uploads}") String directory) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public Object upload(long houseId, MultipartFile file) throws IOException {
        access.get("house", houseId, true);
        Checks.require(
                !file.isEmpty() && file.getSize() <= 50L * 1024 * 1024, "文件不能为空，且不得超过 50 MB");
        byte[] header;
        try (InputStream stream = file.getInputStream()) {
            header = stream.readNBytes(16);
        }
        String extension;
        if (header.length >= 3
                && (header[0] & 255) == 255
                && (header[1] & 255) == 216
                && (header[2] & 255) == 255) extension = "jpg";
        else if (header.length >= 8
                && Arrays.equals(
                        Arrays.copyOf(header, 8),
                        new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) extension = "png";
        else if (header.length >= 12
                && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                        .equals("RIFF")
                && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                        .equals("WEBP")) extension = "webp";
        else if (header.length >= 12
                && new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII)
                        .equals("ftyp")) extension = "mp4";
        else throw new BusinessException(400, "仅支持 JPEG、PNG、WebP 图片和 MP4 视频");
        Files.createDirectories(root);
        String name = UUID.randomUUID() + "." + extension;
        Path path = root.resolve(name);
        String original =
                Optional.ofNullable(file.getOriginalFilename())
                        .orElse("media")
                        .replaceAll("[\\\\/\r\n]", "_");
        Checks.require(original.length() <= 255, "文件名过长");
        try (InputStream stream = file.getInputStream()) {
            Files.copy(stream, path);
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED)
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                    }
                });
        long id =
                db.insert(
                        "house_media",
                        args(
                                "house_id",
                                houseId,
                                "media_type",
                                extension.equals("mp4") ? "VIDEO" : "IMAGE",
                                "file_name",
                                name,
                                "original_name",
                                original,
                                "content_type",
                                extension.equals("mp4")
                                        ? "video/mp4"
                                        : extension.equals("jpg")
                                                ? "image/jpeg"
                                                : "image/" + extension,
                                "file_size",
                                file.getSize(),
                                "uploaded_by",
                                scope.user().id(),
                                "deleted",
                                false));
        return metadata(houseId, id);
    }

    public Map<String, Object> metadata(long house, long media) {
        access.get("house", house, false);
        return db.one(
                "SELECT * FROM house_media WHERE id=#{p.id} AND house_id=#{p.house} AND deleted=0",
                args("id", media, "house", house));
    }

    public Resource resource(Map<String, Object> media) throws IOException {
        Path path = root.resolve((String) media.get("fileName")).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new BusinessException(404, "媒体文件不存在");
        return new UrlResource(path.toUri());
    }

    @Transactional
    public void delete(long house, long media) {
        metadata(house, media);
        db.update("house_media", media, args("deleted", true));
    }
}
