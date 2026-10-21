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
    private final HouseService houses;

    public MediaService(
            DataStore db,
            ResourceAccess access,
            AccessScope scope,
            @Value("${app.upload.directory:./uploads}") String directory,
            HouseService houses) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.houses = houses;
    }

    @Transactional
    public Object upload(long houseId, MultipartFile file) throws IOException {
        var house = access.get("house", houseId, true);
        Checks.require(!"DEAL".equals(house.get("status")), "已成交房源不能调整媒体");
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
        String type = extension.equals("mp4") ? "VIDEO" : "IMAGE";
        long count =
                db.count(
                        "SELECT COUNT(*) AS value FROM house_media WHERE house_id=#{p.id} AND"
                                + " deleted=0 AND media_type=#{p.type}",
                        args("id", houseId, "type", type));
        Checks.require(count < (type.equals("IMAGE") ? 20 : 3), "每套房源最多20张图片、3个视频");
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
        } catch (IOException failure) {
            Files.deleteIfExists(path);
            throw failure;
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
                                false,
                                "is_cover",
                                type.equals("IMAGE") && count == 0,
                                "sort_order",
                                0));
        long version = houses.mediaChanged(houseId, "MEDIA_UPLOAD");
        var result = metadata(houseId, id);
        result.put("houseVersion", version);
        return result;
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
        access.get("house", house, true);
        var old = metadata(house, media);
        db.update("house_media", media, args("deleted", true));
        if (truth(old.get("isCover"))) {
            var next =
                    db.optional(
                            "SELECT id FROM house_media WHERE house_id=#{p.house} AND deleted=0 AND"
                                    + " media_type='IMAGE' ORDER BY sort_order,id LIMIT 1",
                            args("house", house));
            next.ifPresent(row -> db.update("house_media", id(row, "id"), args("is_cover", true)));
        }
        houses.mediaChanged(house, "MEDIA_DELETE");
    }

    @Transactional
    public Object cover(long house, long media, HouseModels.MediaCover b) {
        var row = access.get("house", house, true);
        HouseService.version(row, b.version());
        var item = metadata(house, media);
        Checks.require("IMAGE".equals(item.get("mediaType")), "封面必须是图片");
        db.execute(
                "UPDATE house_media SET is_cover=FALSE WHERE house_id=#{p.house}",
                args("house", house));
        db.update("house_media", media, args("is_cover", true));
        return args("houseVersion", houses.mediaChanged(house, "MEDIA_COVER"));
    }

    @Transactional
    public Object reorder(long house, HouseModels.MediaOrder b) {
        var row = access.get("house", house, true);
        HouseService.version(row, b.version());
        var existing =
                db.list(
                        "SELECT id FROM house_media WHERE house_id=#{p.house} AND deleted=0",
                        args("house", house));
        var expected = new HashSet<Long>();
        existing.forEach(x -> expected.add(id(x, "id")));
        Checks.require(
                expected.size() == b.mediaIds().size()
                        && expected.equals(new HashSet<>(b.mediaIds())),
                "排序须包含全部有效媒体且不能重复");
        for (int i = 0; i < b.mediaIds().size(); i++)
            db.update("house_media", b.mediaIds().get(i), args("sort_order", i));
        return args("houseVersion", houses.mediaChanged(house, "MEDIA_ORDER"));
    }
}
