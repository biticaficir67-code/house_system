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
    private final AuditService audit;

    public FollowUpService(
            DataStore db, ResourceAccess access, AccessScope scope, AuditService audit) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.audit = audit;
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
        db.update(
                kind,
                id,
                args(
                        "next_contact_at",
                        body.nextContactAt(),
                        "version",
                        id(parent, "version") + 1));
        return db.one("SELECT * FROM " + kind + "_follow_up WHERE id=#{p.id}", args("id", rowId));
    }

    public record Assignment(
            @NotNull @Positive Long brokerId,
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 500) String reason) {}

    @Transactional
    public Object assign(String kind, long id, Assignment b) {
        if (!scope.user().administrator()) throw new BusinessException(403, "仅管理员可以交接负责人");
        Checks.choice(kind, "owner", "customer");
        var old = access.get(kind, id, true);
        system.house.HouseService.version(old, b.version());
        access.broker(id(old, "storeId"), b.brokerId());
        Checks.require(!java.util.Objects.equals(old.get("brokerId"), b.brokerId()), "负责人未改变");
        db.update(kind, id, args("broker_id", b.brokerId(), "version", id(old, "version") + 1));
        if (kind.equals("owner"))
            db.execute(
                    "UPDATE owner_entrustment SET broker_id=#{p.broker} WHERE owner_id=#{p.id} AND"
                            + " status='ACTIVE'",
                    args("broker", b.brokerId(), "id", id));
        audit.record(
                kind,
                id,
                "ASSIGN",
                b.reason(),
                args("brokerId", old.get("brokerId")),
                args("brokerId", b.brokerId()));
        return access.get(kind, id, false);
    }

    public Object logs(String kind, long id, int page, int size) {
        access.get(kind, id, false);
        return audit.list(kind, id, page, size);
    }

    public Object duplicates(String kind, String phone, Long excludeId) {
        Checks.choice(kind, "owner", "customer");
        Checks.require(phone != null && !phone.isBlank() && phone.length() <= 32, "请提供联系电话");
        var p = args("phone", phone, "exclude", excludeId);
        return db.list(
                "SELECT r.id,r.name,r.phone,r.broker_id FROM "
                        + kind
                        + " r WHERE r.deleted=0 AND "
                        + scope.sql("r", p)
                        + " AND r.phone=#{p.phone}"
                        + (excludeId == null ? "" : " AND r.id<>#{p.exclude}")
                        + " ORDER BY r.id LIMIT 20",
                p);
    }
}
