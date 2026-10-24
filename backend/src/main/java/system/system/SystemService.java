package system.system;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.auth.*;
import system.common.*;
import system.security.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class SystemService {
    private final DataStore db;
    private final AccessScope scope;
    private final ResourceAccess access;
    private final AuthService auth;
    private final AuditService audit;
    private final TaskService tasks;
    private final AccountLock lock;
    private final AccountStore accounts;
    private final BrokerProfiles profiles;

    public SystemService(
            DataStore db,
            AccessScope scope,
            ResourceAccess access,
            AuthService auth,
            AuditService audit,
            TaskService tasks,
            AccountLock lock,
            AccountStore accounts,
            BrokerProfiles profiles) {
        this.db = db;
        this.scope = scope;
        this.access = access;
        this.auth = auth;
        this.audit = audit;
        this.tasks = tasks;
        this.lock = lock;
        this.accounts = accounts;
        this.profiles = profiles;
    }

    public List<Map<String, Object>> stores() {
        return scope.user().administrator()
                ? db.list("SELECT * FROM store ORDER BY id", args())
                : db.list(
                        "SELECT * FROM store WHERE id=#{p.id}", args("id", scope.user().storeId()));
    }

    @Transactional
    public long createStore(SystemModels.StoreWrite b) {
        return db.insert(
                "store", args("name", b.name(), "address", b.address(), "enabled", b.enabled()));
    }

    @Transactional
    public void updateStore(long id, SystemModels.StoreWrite b) {
        db.one("SELECT id FROM store WHERE id=#{p.id} FOR UPDATE", args("id", id));
        if (!b.enabled())
            Checks.require(!Objects.equals(scope.user().storeId(), id), "不能停用当前登录账号所在门店");
        db.update(
                "store",
                id,
                args("name", b.name(), "address", b.address(), "enabled", b.enabled()));
        db.execute(
                "UPDATE broker SET dept=#{p.name} WHERE store_id=#{p.id}",
                args("name", b.name(), "id", id));
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

    private String roleCode(List<Long> ids) {
        Checks.require(ids != null && ids.size() == 1 && ids.get(0) != null, "每个账号必须选择一个管理员或经纪人身份");
        return (String)
                db.optional(
                                "SELECT code FROM sys_role WHERE id=#{p.id} AND enabled=1 AND code"
                                    + " IN ('ADMIN','BROKER')",
                                args("id", ids.get(0)))
                        .orElseThrow(() -> new BusinessException(400, "只能选择启用的管理员或经纪人身份"))
                        .get("code");
    }

    public Object users(String keyword, Long store, String status, int page, int size) {
        return accounts.list(keyword, store, status, page, size);
    }

    public Map<String, Object> user(long key) {
        return accounts.view(accounts.get(key, false));
    }

    public Object logs(long key, int page, int size) {
        var a = accounts.get(key, false);
        return audit.list(a.resource(), a.key(), page, size);
    }

    @Transactional
    public long createUser(SystemModels.UserCreate b) {
        lock.acquire();
        String role = roleCode(b.roleIds());
        AccountStore.Account a;
        if (role.equals("ADMIN")) {
            Checks.require(b.brokerId() == null, "管理员不绑定经纪人档案");
            Checks.require(b.phone() == null || b.phone().isBlank(), "管理员原表没有电话字段");
            a = accounts.createAdmin(b.username(), b.password(), b.displayName());
        } else {
            Checks.require(b.storeId() != null, "经纪人必须选择门店");
            access.store(b.storeId());
            String dept =
                    (String)
                            db.one(
                                            "SELECT name FROM store WHERE id=#{p.id}",
                                            args("id", b.storeId()))
                                    .get("name");
            if (b.brokerId() == null)
                a =
                        accounts.createBroker(
                                b.username(),
                                b.password(),
                                b.displayName(),
                                b.phone(),
                                b.storeId(),
                                dept,
                                true);
            else {
                a = accounts.get(b.brokerId(), true);
                Checks.require(!a.administrator() && !a.hasCredentials(), "该档案已有登录账号，请使用原账号或重置密码");
                Checks.require(a.storeId() == null || a.storeId().equals(b.storeId()), "档案不属于该门店");
                accounts.available(b.username());
                a = accounts.profile(a, b.displayName(), b.phone());
                a =
                        accounts.update(
                                a,
                                args(
                                        "username",
                                        b.username(),
                                        "store_id",
                                        b.storeId(),
                                        "dept",
                                        dept,
                                        "audit_status",
                                        1,
                                        "account_status",
                                        1,
                                        "reject_reason",
                                        null));
                a = accounts.password(a, b.password());
            }
        }
        audit.record(a.resource(), a.key(), "CREATE", "管理员开通账号", null, accounts.view(a));
        return a.key();
    }

    @Transactional
    public void updateUser(long key, SystemModels.UserUpdate b) {
        lock.acquire();
        var a = accounts.get(key, true);
        accounts.version(a, b.version());
        Checks.require(Set.of("ACTIVE", "DISABLED").contains(a.status()), "待审核或已拒绝账号须通过审核处理");
        Checks.require(roleCode(b.roleIds()).equals(a.type()), "身份由管理员表或经纪人表决定，不能直接互换");
        Checks.require(key != scope.user().id() || b.enabled(), "不能停用自己的账号");
        var before = accounts.view(a);
        if (a.administrator()) {
            if (!b.enabled())
                Checks.require(
                        db.count(
                                        "SELECT COUNT(*) AS value FROM sys_admin WHERE"
                                            + " COALESCE(is_deleted,0)=0 AND id<>#{p.id}",
                                        args("id", a.sourceId()))
                                > 0,
                        "必须保留至少一个启用的管理员");
            a = accounts.profile(a, b.displayName(), b.phone());
            a = accounts.update(a, args("is_deleted", b.enabled() ? 0 : 1));
        } else {
            Checks.require(b.storeId() != null, "经纪人必须选择门店");
            access.store(b.storeId());
            if (!Objects.equals(a.storeId(), b.storeId())) {
                for (String table :
                        List.of("house", "owner", "customer", "house_show", "house_deal")) {
                    String extra =
                            switch (table) {
                                case "house" -> " AND status<>'DEAL'";
                                case "customer" -> " AND status='ACTIVE'";
                                case "house_show" -> " AND status='SCHEDULED'";
                                case "house_deal" ->
                                        " AND status='CONFIRMED' AND"
                                            + " verification_status<>'APPROVED'";
                                default -> "";
                            };
                    Checks.require(
                            db.count(
                                            "SELECT COUNT(*) AS value FROM "
                                                    + table
                                                    + " WHERE broker_id=#{p.id} AND deleted=0"
                                                    + extra,
                                            args("id", a.sourceId()))
                                    == 0,
                            "经纪人仍有业务记录，须先交接后调整门店");
                }
            }
            a = accounts.profile(a, b.displayName(), b.phone());
            a =
                    accounts.update(
                            a,
                            args(
                                    "store_id",
                                    b.storeId(),
                                    "dept",
                                    db.one(
                                                    "SELECT name FROM store WHERE id=#{p.id}",
                                                    args("id", b.storeId()))
                                            .get("name"),
                                    "account_status",
                                    b.enabled() ? 1 : 0));
        }
        auth.revokeUser(key);
        audit.record(a.resource(), key, "UPDATE", b.reason(), before, accounts.view(a));
    }

    @Transactional
    public void resetPassword(long key, String password) {
        var a = accounts.get(key, true);
        accounts.password(a, password);
        auth.revokeUser(key);
        audit.record(a.resource(), key, "RESET_PASSWORD", "管理员重置密码", null, null);
    }

    public Object brokers(Long store, Boolean unbound) {
        var p = args("store", store);
        String name = profiles.nameColumn();
        String source = "(SELECT broker.*,id AS broker_id FROM broker) b";
        String condition;
        if (Boolean.TRUE.equals(unbound)) {
            if (!scope.user().administrator()) throw new BusinessException(403, "仅管理员可查询尚未开通的档案");
            condition =
                    "(b.username IS NULL OR TRIM(b.username)='' OR b.password IS NULL OR"
                        + " TRIM(b.password)='')";
        } else
            condition =
                    scope.sql("b", p)
                            + " AND b.account_status=1 AND b.audit_status=1 AND"
                            + " TRIM(b.username)<>'' AND TRIM(b.password)<>''";
        if (store != null) condition += " AND b.store_id=#{p.store}";
        return db.list(
                "SELECT b.id,b."
                        + name
                        + " AS broker_name,b.phone,b.dept,b.store_id FROM "
                        + source
                        + " WHERE "
                        + condition
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
