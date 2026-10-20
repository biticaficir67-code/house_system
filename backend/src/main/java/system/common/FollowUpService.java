package system.common;

import static system.common.DataStore.*;

import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.security.AccessScope;

import java.time.LocalDateTime;

@Service
public class FollowUpService {
    public record Write(
            @NotBlank String type,
            @NotBlank @Size(max = 5000) String content,
            @Future LocalDateTime nextContactAt) {}

    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;

    public FollowUpService(DataStore db, ResourceAccess access, AccessScope scope) {
        this.db = db;
        this.access = access;
        this.scope = scope;
    }

    public Object list(String kind, long id) {
        access.get(kind, id, false);
        return db.list(
                "SELECT * FROM "
                        + kind
                        + "_follow_up WHERE "
                        + kind
                        + "_id=#{p.id} ORDER BY id DESC",
                args("id", id));
    }

    @Transactional
    public Object create(String kind, long id, Write body) {
        var parent = access.get(kind, id, true);
        Checks.choice(body.type(), "PHONE", "VISIT", "WECHAT", "OTHER");
        long rowId =
                db.insert(
                        kind + "_follow_up",
                        args(
                                kind + "_id",
                                id,
                                "contact_type",
                                body.type(),
                                "content",
                                body.content(),
                                "next_contact_at",
                                body.nextContactAt(),
                                "created_by",
                                scope.user().id(),
                                "store_id",
                                parent.get("storeId")));
        return db.one("SELECT * FROM " + kind + "_follow_up WHERE id=#{p.id}", args("id", rowId));
    }
}
