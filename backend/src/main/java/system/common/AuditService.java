package system.common;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;

import system.security.AccessScope;

import java.util.*;

@Service
public class AuditService {
    private final DataStore db;
    private final AccessScope scope;

    public AuditService(DataStore db, AccessScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public void record(
            String resource, long id, String event, String reason, Object before, Object after) {
        recordAs(scope.user().id(), resource, id, event, reason, before, after);
    }

    public void recordAs(
            Long actor,
            String resource,
            long id,
            String event,
            String reason,
            Object before,
            Object after) {
        db.insert(
                "operation_log",
                args(
                        "resource_type",
                        resource,
                        "resource_id",
                        id,
                        "event_type",
                        event,
                        "actor_id",
                        actor,
                        "reason",
                        reason,
                        "before_snapshot",
                        before == null ? null : db.json(before),
                        "after_snapshot",
                        after == null ? null : db.json(after)));
        if (actor != null && actor < 0) {
            db.insert(
                    "sys_oper_log",
                    args(
                            "operator_id",
                            -actor,
                            "operator_type",
                            1,
                            "operate_type",
                            event,
                            "target_id",
                            Math.abs(id),
                            "content",
                            reason));
        }
    }

    public Object list(String resource, long id, int page, int size) {
        var q = new PageQuery(page, size);
        var p = args("type", resource, "id", id, "size", q.size(), "offset", q.offset());
        String where = " FROM operation_log WHERE resource_type=#{p.type} AND resource_id=#{p.id}";
        return new PageResponse<>(
                db.count("SELECT COUNT(*) AS value" + where, p),
                page,
                size,
                db.list(
                        "SELECT *" + where + " ORDER BY id DESC LIMIT #{p.size} OFFSET #{p.offset}",
                        p));
    }
}
