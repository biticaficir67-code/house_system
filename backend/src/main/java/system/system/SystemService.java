package system.system;

import static system.common.DataStore.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.auth.AuthService;
import system.common.*;
import system.security.*;
import system.security.Permissions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class SystemService {
    private final DataStore db;
    private final AccessScope scope;
    private final ResourceAccess access;
    private final PasswordEncoder passwords;
    private final AuthService auth;
    private final AuditService audit;
    private final TaskService tasks;

    public SystemService(
            DataStore db,
            AccessScope scope,
            ResourceAccess access,
            PasswordEncoder passwords,
            AuthService auth,
            AuditService audit,
            TaskService tasks) {
        this.db = db;
        this.scope = scope;
        this.access = access;
        this.passwords = passwords;
        this.auth = auth;
        this.audit = audit;
        this.tasks = tasks;
    }

    public List<Map<String, Object>> stores() {
        if (scope.user().administrator()) return db.list("SELECT * FROM store ORDER BY id", args());
        return db.list("SELECT * FROM store WHERE id=#{p.id}", args("id", scope.user().storeId()));
    }

    @Transactional
    public long createStore(SystemModels.StoreWrite body) {
        return db.insert(
                "store",
                args("name", body.name(), "address", body.address(), "enabled", body.enabled()));
    }

    @Transactional
    public void updateStore(long id, SystemModels.StoreWrite body) {
        db.one("SELECT id FROM store WHERE id=#{p.id} FOR UPDATE", args("id", id));
        if (!body.enabled()) {
            Checks.require(!Objects.equals(scope.user().storeId(), id), "不能停用当前登录账号所在门店");
        }
        db.update(
                "store",
                id,
                args("name", body.name(), "address", body.address(), "enabled", body.enabled()));
        db.execute(
                "UPDATE broker SET dept=#{p.name} WHERE store_id=#{p.id}",
                args("name", body.name(), "id", id));
    }

    public List<Map<String, Object>> roles() {
        var rows =
                db.list(
                        "SELECT * FROM sys_role WHERE code IN ('ADMIN','BROKER') ORDER BY id",
                        args());
        rows.forEach(
                row -> {
                    boolean admin = "ADMIN".equals(row.get("code"));
                    row.put("name", admin ? "管理员" : "经纪人");
                    row.put("dataScope", admin ? "ALL" : "OWN");
                    row.put("permissions", admin ? Permissions.ALL : Permissions.BROKER);
                });
        return rows;
    }

    public Object users(String keyword, Long storeId, String accountStatus, int page, int size) {
        PageQuery query = new PageQuery(page, size);
        var p =
                args(
                        "keyword",
                        Checks.search(keyword),
                        "offset",
                        query.offset(),
                        "size",
                        query.size());
        String filter =
                p.get("keyword") == null
                        ? ""
                        : " WHERE (username LIKE CONCAT('%',#{p.keyword},'%') OR display_name LIKE"
                                + " CONCAT('%',#{p.keyword},'%'))";
        if (filter.isEmpty()) filter = " WHERE 1=1";
        if (storeId != null) {
            p.put("store", storeId);
            filter += " AND store_id=#{p.store}";
        }
        if (accountStatus != null) {
            Checks.choice(accountStatus, "PENDING", "ACTIVE", "REJECTED", "DISABLED");
            p.put("status", accountStatus);
            filter += " AND account_status=#{p.status}";
        }
        var rows =
                db.list(
                        "SELECT"
                            + " id,username,display_name,phone,store_id,broker_id,enabled,account_status,review_reason,reviewed_by,reviewed_at,version,created_at"
                            + " FROM sys_user"
                                + filter
                                + " ORDER BY id DESC LIMIT #{p.size} OFFSET #{p.offset}",
                        p);
        rows.forEach(
                row ->
                        row.put(
                                "roles",
                                db.list(
                                        "SELECT r.id,r.code,r.name FROM sys_role r JOIN"
                                                + " sys_user_role ur ON r.id=ur.role_id WHERE"
                                                + " ur.user_id=#{p.id} AND r.enabled=1"
                                                + " AND r.code IN ('ADMIN','BROKER')",
                                        args("id", row.get("id")))));
        return new PageResponse<>(
                db.count("SELECT COUNT(*) AS value FROM sys_user" + filter, p), page, size, rows);
    }

    public Map<String, Object> user(long id) {
        var row =
                db.one(
                        "SELECT"
                            + " id,username,display_name,phone,store_id,broker_id,enabled,account_status,review_reason,reviewed_by,reviewed_at,version,created_at"
                            + " FROM sys_user WHERE id=#{p.id}",
                        args("id", id));
        row.put(
                "roles",
                db.list(
                        "SELECT r.id,r.code,r.name FROM sys_role r JOIN sys_user_role ur ON"
                                + " ur.role_id=r.id WHERE ur.user_id=#{p.id} AND r.code IN"
                                + " ('ADMIN','BROKER')",
                        args("id", id)));
        return row;
    }

    public Object logs(long id, int page, int size) {
        user(id);
        return audit.list("user", id, page, size);
    }

    private String roleCode(List<Long> roles) {
        Checks.require(
                roles != null && roles.size() == 1 && roles.get(0) != null, "每个账号必须选择一个管理员或经纪人身份");
        var row =
                db.optional(
                        "SELECT code FROM sys_role WHERE id=#{p.id} AND enabled=1"
                                + " AND code IN ('ADMIN','BROKER')",
                        args("id", roles.get(0)));
        Checks.require(row.isPresent(), "只能选择已启用的管理员或经纪人身份");
        return (String) row.orElseThrow().get("code");
    }

    private void assignRoles(long userId, List<Long> roles) {
        roleCode(roles);
        db.execute("DELETE FROM sys_user_role WHERE user_id=#{p.id}", args("id", userId));
        db.execute(
                "INSERT INTO sys_user_role(user_id,role_id) VALUES(#{p.user},#{p.role})",
                args("user", userId, "role", roles.get(0)));
    }

    @Transactional
    public long createUser(SystemModels.UserCreate body) {
        boolean brokerRole = "BROKER".equals(roleCode(body.roleIds()));
        Checks.require(brokerRole || body.brokerId() == null, "新建管理员无需绑定经纪人档案");
        access.store(body.storeId());
        var store = db.one("SELECT name FROM store WHERE id=#{p.id}", args("id", body.storeId()));
        Long broker = body.brokerId();
        if (brokerRole && broker == null)
            broker =
                    db.insert(
                            "broker",
                            args(
                                    "broker_name",
                                    body.displayName(),
                                    "phone",
                                    body.phone(),
                                    "dept",
                                    store.get("name"),
                                    "store_id",
                                    body.storeId()));
        else if (broker != null) {
            var row =
                    db.one("SELECT * FROM broker WHERE id=#{p.id} FOR UPDATE", args("id", broker));
            Checks.require(
                    row.get("storeId") != null && id(row, "storeId") == body.storeId(),
                    "经纪人档案不属于该门店");
            Checks.require(row.get("userId") == null, "该经纪人已绑定登录账号");
        }
        long userId =
                db.insert(
                        "sys_user",
                        args(
                                "username",
                                body.username(),
                                "password_hash",
                                passwords.encode(Checks.password(body.password())),
                                "display_name",
                                body.displayName(),
                                "phone",
                                body.phone(),
                                "store_id",
                                body.storeId(),
                                "broker_id",
                                broker,
                                "enabled",
                                true));
        if (broker != null) db.update("broker", broker, args("user_id", userId));
        assignRoles(userId, body.roleIds());
        audit.record("user", userId, "CREATE", "管理员开通内部账号", null, user(userId));
        return userId;
    }

    @Transactional
    public void updateUser(long id, SystemModels.UserUpdate body) {
        db.one("SELECT * FROM sys_bootstrap WHERE id=1 FOR UPDATE", args());
        var old = db.one("SELECT * FROM sys_user WHERE id=#{p.id} FOR UPDATE", args("id", id));
        system.house.HouseService.version(old, body.version());
        Checks.require(
                Set.of("ACTIVE", "DISABLED").contains(old.get("accountStatus")),
                "待审核或已拒绝的账号必须通过审核接口处理");
        var before = user(id);
        boolean brokerRole = "BROKER".equals(roleCode(body.roleIds()));
        access.store(body.storeId());
        long adminRole = id(db.one("SELECT id FROM sys_role WHERE code='ADMIN'", args()), "id");
        boolean wasAdmin =
                db.count(
                                "SELECT COUNT(*) AS value FROM sys_user_role WHERE user_id=#{p.id}"
                                        + " AND role_id=#{p.role}",
                                args("id", id, "role", adminRole))
                        > 0;
        if (wasAdmin && (!body.enabled() || !body.roleIds().contains(adminRole))) {
            Checks.require(
                    db.count(
                                    "SELECT COUNT(*) AS value FROM sys_user u JOIN sys_user_role ur"
                                            + " ON ur.user_id=u.id WHERE ur.role_id=#{p.role} AND"
                                            + " u.enabled=1 AND u.account_status='ACTIVE' AND"
                                            + " u.id<>#{p.id}",
                                    args("role", adminRole, "id", id))
                            > 0,
                    "必须保留至少一个启用的管理员");
        }
        Checks.require(id != scope.user().id() || body.enabled(), "不能停用自己的账号");
        if (!Objects.equals(old.get("storeId"), body.storeId()) && old.get("brokerId") != null) {
            for (String table : List.of("house", "owner", "customer", "house_show", "house_deal")) {
                String unfinished =
                        switch (table) {
                            case "house" -> " AND status<>'DEAL'";
                            case "customer" -> " AND status='ACTIVE'";
                            case "house_show" -> " AND status='SCHEDULED'";
                            case "house_deal" ->
                                    " AND status='CONFIRMED' AND verification_status<>'APPROVED'";
                            default -> "";
                        };
                Checks.require(
                        db.count(
                                        "SELECT COUNT(*) AS value FROM "
                                                + table
                                                + " WHERE broker_id=#{p.broker} AND deleted=0"
                                                + unfinished,
                                        args("broker", old.get("brokerId")))
                                == 0,
                        "经纪人仍有业务记录，须先交接后再调整门店");
            }
        }
        Long broker = old.get("brokerId") == null ? null : id(old, "brokerId");
        if (brokerRole && broker == null)
            broker =
                    db.insert(
                            "broker",
                            args(
                                    "broker_name",
                                    body.displayName(),
                                    "phone",
                                    body.phone(),
                                    "store_id",
                                    body.storeId(),
                                    "dept",
                                    db.one(
                                                    "SELECT name FROM store WHERE id=#{p.id}",
                                                    args("id", body.storeId()))
                                            .get("name"),
                                    "user_id",
                                    id));
        db.update(
                "sys_user",
                id,
                args(
                        "display_name",
                        body.displayName(),
                        "phone",
                        body.phone(),
                        "store_id",
                        body.storeId(),
                        "broker_id",
                        broker,
                        "enabled",
                        body.enabled(),
                        "account_status",
                        body.enabled() ? "ACTIVE" : "DISABLED",
                        "version",
                        id(old, "version") + 1));
        if (broker != null)
            db.update(
                    "broker",
                    broker,
                    args(
                            "broker_name",
                            body.displayName(),
                            "phone",
                            body.phone(),
                            "user_id",
                            id,
                            "store_id",
                            body.storeId(),
                            "dept",
                            db.one(
                                            "SELECT name FROM store WHERE id=#{p.id}",
                                            args("id", body.storeId()))
                                    .get("name")));
        assignRoles(id, body.roleIds());
        auth.revokeUser(id);
        audit.record("user", id, "UPDATE", body.reason(), before, user(id));
    }

    @Transactional
    public void resetPassword(long id, String password) {
        db.one("SELECT id FROM sys_user WHERE id=#{p.id}", args("id", id));
        db.update(
                "sys_user", id, args("password_hash", passwords.encode(Checks.password(password))));
        auth.revokeUser(id);
        audit.record("user", id, "RESET_PASSWORD", "管理员重置密码", null, null);
    }

    public Object brokers(Long storeId, Boolean unbound) {
        var p = args("store", storeId);
        String condition = scope.sql("b", p);
        if (Boolean.TRUE.equals(unbound)) {
            if (!scope.user().administrator()) throw new BusinessException(403, "仅管理员可查询待绑定档案");
            return db.list(
                    "SELECT b.id,b.broker_name,b.phone,b.dept,b.store_id,b.user_id FROM broker b"
                        + " WHERE b.user_id IS NULL"
                            + (storeId == null ? "" : " AND b.store_id=#{p.store}")
                            + " ORDER BY b.id",
                    p);
        }
        // broker owns itself; AccessScope's broker_id column is provided by this derived table.
        return db.list(
                "SELECT b.id,b.broker_name,b.phone,b.dept,b.store_id,b.user_id FROM "
                        + "(SELECT broker.*,id AS broker_id FROM broker) b WHERE "
                        + condition
                        + " AND EXISTS (SELECT 1 FROM sys_user u JOIN"
                        + " sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id"
                        + " WHERE u.id=b.user_id AND u.broker_id=b.id AND u.enabled=1 AND"
                        + " u.account_status='ACTIVE' AND r.code='BROKER' AND r.enabled=1)"
                        + (storeId == null ? "" : " AND b.store_id=#{p.store}")
                        + " ORDER BY b.id",
                p);
    }

    @Transactional(readOnly = true)
    public Object workbench() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var p =
                args(
                        "today",
                        today.atStartOfDay(),
                        "tomorrow",
                        today.plusDays(1).atStartOfDay(),
                        "todayDate",
                        today,
                        "dueDate",
                        today.plusDays(7));
        String own = scope.sql("r", p);
        String houses = " FROM house r WHERE r.deleted=0 AND " + own;
        String customers = " FROM customer r WHERE r.deleted=0 AND " + own;
        String showings =
                " FROM house_show r WHERE r.deleted=0 AND r.data_origin='BUSINESS' AND " + own;
        String deals = " FROM house_deal r WHERE r.deleted=0 AND " + own;
        String entrustments =
                " FROM owner_entrustment r WHERE r.deleted=0 AND r.status='ACTIVE' AND"
                        + " r.end_date>=#{p.todayDate} AND r.end_date<=#{p.dueDate} AND "
                        + own;
        String pendingHouses = houses + " AND r.status='OFF' AND r.verification_status='PENDING'";
        String pendingDeals =
                deals + " AND r.status='CONFIRMED' AND r.verification_status='PENDING'";
        String unread =
                " FROM owner_reminder m JOIN owner_entrustment r ON r.id=m.entrustment_id WHERE"
                    + " r.deleted=0 AND r.status IN ('ACTIVE','EXPIRED') AND m.read_status='UNREAD'"
                    + " AND "
                        + own;
        var counts =
                args(
                        "onSaleHouses",
                                db.count(
                                        "SELECT COUNT(*) AS value"
                                                + houses
                                                + " AND r.status='ON_SALE'",
                                        p),
                        "pendingHouseVerifications",
                                db.count("SELECT COUNT(*) AS value" + pendingHouses, p),
                        "activeCustomers",
                                db.count(
                                        "SELECT COUNT(*) AS value"
                                                + customers
                                                + " AND r.status='ACTIVE'",
                                        p),
                        "todayScheduledShowings",
                                db.count(
                                        "SELECT COUNT(*) AS value"
                                                + showings
                                                + " AND r.status='SCHEDULED' AND"
                                                + " r.appointment_time>=#{p.today} AND"
                                                + " r.appointment_time<#{p.tomorrow}",
                                        p),
                        "pendingDealVerifications",
                                db.count("SELECT COUNT(*) AS value" + pendingDeals, p),
                        "expiringEntrustments",
                                db.count("SELECT COUNT(*) AS value" + entrustments, p),
                        "unreadReminders", db.count("SELECT COUNT(*) AS value" + unread, p));
        var result =
                args(
                        "role",
                        scope.user().administrator() ? "ADMIN" : "BROKER",
                        "dataScope",
                        scope.user().dataScope(),
                        "date",
                        today,
                        "canVerify",
                        scope.user().administrator(),
                        "counts",
                        counts,
                        "pendingHouses",
                        db.list(
                                "SELECT r.id,r.community_name,r.store_id,r.broker_id,r.version"
                                        + pendingHouses
                                        + " ORDER BY r.id LIMIT 5",
                                p),
                        "pendingDeals",
                        db.list(
                                "SELECT"
                                    + " r.id,r.house_id,r.customer_id,r.broker_id,r.transaction_price_yuan,r.version"
                                        + pendingDeals
                                        + " ORDER BY r.id LIMIT 5",
                                p),
                        "todayShowings",
                        db.list(
                                "SELECT"
                                    + " r.id,r.house_id,r.customer_id,r.broker_id,r.appointment_time,r.version"
                                        + showings
                                        + " AND r.status='SCHEDULED' AND"
                                        + " r.appointment_time>=#{p.today} AND"
                                        + " r.appointment_time<#{p.tomorrow} ORDER BY"
                                        + " r.appointment_time,r.id LIMIT 5",
                                p),
                        "expiringEntrustments",
                        db.list(
                                "SELECT r.id,r.owner_id,r.house_id,r.end_date"
                                        + entrustments
                                        + " ORDER BY r.end_date,r.id LIMIT 5",
                                p));
        result.put("tasks", tasks.summary());
        return result;
    }
}
