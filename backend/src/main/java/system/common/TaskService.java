package system.common;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.security.AccessScope;

import java.time.*;
import java.util.*;

@Service
public class TaskService {
    private final DataStore db;
    private final AccessScope scope;

    public TaskService(DataStore db, AccessScope scope) {
        this.db = db;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String kind, int page, int size) {
        var q = new PageQuery(page, size);
        var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var p =
                args(
                        "today",
                        today.atStartOfDay(),
                        "tomorrow",
                        today.plusDays(1).atStartOfDay(),
                        "now",
                        LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
                        "offset",
                        q.offset(),
                        "size",
                        q.size());
        String table, extra, date;
        switch (kind) {
            case "OWNER_TODAY", "OWNER_OVERDUE", "CUSTOMER_TODAY", "CUSTOMER_OVERDUE" -> {
                table = kind.startsWith("OWNER") ? "owner" : "customer";
                date = "r.next_contact_at";
                extra =
                        (table.equals("customer") ? " AND r.status='ACTIVE'" : "")
                                + (kind.endsWith("TODAY")
                                        ? " AND r.next_contact_at>=#{p.today} AND"
                                                + " r.next_contact_at<#{p.tomorrow}"
                                        : " AND r.next_contact_at<#{p.today}");
            }
            case "SHOWING_TODAY", "SHOWING_OVERDUE", "SHOWING_UNREVIEWED", "SHOWING_RECONTACT" -> {
                table = "house_show";
                date = "COALESCE(r.completed_time,r.appointment_time)";
                extra =
                        " AND r.data_origin='BUSINESS'"
                                + switch (kind) {
                                    case "SHOWING_TODAY" ->
                                            " AND r.status='SCHEDULED' AND"
                                                    + " r.appointment_time>=#{p.today} AND"
                                                    + " r.appointment_time<#{p.tomorrow}";
                                    case "SHOWING_OVERDUE" ->
                                            " AND r.status='SCHEDULED' AND"
                                                    + " r.appointment_time<#{p.now}";
                                    case "SHOWING_RECONTACT" ->
                                            " AND r.status='COMPLETED' AND"
                                                + " r.next_contact_at<#{p.tomorrow}";
                                    default ->
                                            " AND r.status='COMPLETED' AND NOT EXISTS(SELECT 1 FROM"
                                                + " showing_follow_up f WHERE f.showing_id=r.id)";
                                };
            }
            case "HOUSE_VERIFICATION" -> {
                table = "house";
                extra = " AND r.status='OFF' AND r.verification_status='PENDING'";
                date = "r.id";
            }
            case "DEAL_VERIFICATION" -> {
                table = "house_deal";
                extra = " AND r.status='CONFIRMED' AND r.verification_status='PENDING'";
                date = "r.id";
            }
            case "REGISTRATION" -> {
                if (!scope.user().administrator()) throw new BusinessException(403, "仅管理员可查看注册申请");
                String from = " FROM sys_user WHERE account_status='PENDING'";
                return args(
                        "total",
                        db.count("SELECT COUNT(*) AS value" + from, p),
                        "page",
                        page,
                        "size",
                        size,
                        "records",
                        db.list(
                                "SELECT"
                                    + " id,username,display_name,phone,account_status,created_at,version"
                                        + from
                                        + " ORDER BY id LIMIT #{p.size} OFFSET #{p.offset}",
                                p));
            }
            default -> throw new BusinessException(400, "待办类型无效");
        }
        String where = " FROM " + table + " r WHERE r.deleted=0 AND " + scope.sql("r", p) + extra;
        return args(
                "total",
                db.count("SELECT COUNT(*) AS value" + where, p),
                "page",
                page,
                "size",
                size,
                "records",
                db.list(
                        "SELECT r.*"
                                + where
                                + " ORDER BY "
                                + date
                                + ",r.id LIMIT #{p.size} OFFSET #{p.offset}",
                        p));
    }

    public Object summary() {
        var result = new LinkedHashMap<String, Object>();
        var kinds =
                new ArrayList<>(
                        List.of(
                                "OWNER_TODAY",
                                "OWNER_OVERDUE",
                                "CUSTOMER_TODAY",
                                "CUSTOMER_OVERDUE",
                                "SHOWING_TODAY",
                                "SHOWING_OVERDUE",
                                "SHOWING_UNREVIEWED",
                                "SHOWING_RECONTACT"));
        if (scope.user().administrator()) kinds.add("REGISTRATION");
        for (String kind : kinds) result.put(kind, list(kind, 1, 5));
        return result;
    }
}
