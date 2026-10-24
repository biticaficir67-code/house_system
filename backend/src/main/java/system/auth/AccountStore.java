package system.auth;

import static system.common.DataStore.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import system.common.*;
import system.system.BrokerProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Accounts live in the team's sys_admin and broker tables; no mirrored login rows. */
@Service
public class AccountStore {
    public record Account(long key, Map<String, Object> raw) {
        public boolean administrator() {
            return key < 0;
        }

        public long sourceId() {
            return Math.abs(key);
        }

        public String type() {
            return administrator() ? "ADMIN" : "BROKER";
        }

        public String table() {
            return administrator() ? "sys_admin" : "broker";
        }

        public String resource() {
            return administrator() ? "admin_account" : "broker_account";
        }

        public String username() {
            return Objects.toString(raw.get("username"), "");
        }

        public String password() {
            return Objects.toString(raw.get("password"), "");
        }

        public String name() {
            return Objects.toString(
                    raw.get(
                            administrator()
                                    ? "adminName"
                                    : raw.containsKey("name") ? "name" : "brokerName"),
                    username());
        }

        public Long storeId() {
            return raw.get("storeId") instanceof Number n ? n.longValue() : null;
        }

        public String status() {
            if (administrator()) return truth(raw.get("isDeleted")) ? "DISABLED" : "ACTIVE";
            if (!hasCredentials()) return "DISABLED";
            if (!(raw.get("accountStatus") instanceof Number n) || n.intValue() != 1)
                return "DISABLED";
            return switch (((Number) raw.get("auditStatus")).intValue()) {
                case 0 -> "PENDING";
                case 1 -> "ACTIVE";
                case 2 -> "REJECTED";
                default -> "DISABLED";
            };
        }

        public boolean hasCredentials() {
            return !username().isBlank() && !password().isBlank();
        }
    }

    private final DataStore db;
    private final PasswordEncoder passwords;
    private final BrokerProfiles brokers;

    public AccountStore(DataStore db, PasswordEncoder passwords, BrokerProfiles brokers) {
        this.db = db;
        this.passwords = passwords;
        this.brokers = brokers;
    }

    public Account get(long key, boolean lock) {
        Checks.require(key != 0 && key != Long.MIN_VALUE, "账号编号无效");
        String table = key < 0 ? "sys_admin" : "broker";
        return new Account(
                key,
                db.one(
                        "SELECT * FROM "
                                + table
                                + " WHERE id=#{p.id}"
                                + (lock ? " FOR UPDATE" : ""),
                        args("id", Math.abs(key))));
    }

    public Account find(String username, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        var admin =
                db.optional(
                        "SELECT * FROM sys_admin WHERE username=#{p.name}" + suffix,
                        args("name", username));
        var broker =
                db.optional(
                        "SELECT * FROM broker WHERE username=#{p.name}" + suffix,
                        args("name", username));
        if (admin.isPresent() && broker.isPresent())
            throw new BusinessException(409, "管理员和经纪人登录名重复，请先核对原表账号");
        if (admin.isPresent()) return new Account(-id(admin.get(), "id"), admin.get());
        return broker.map(row -> new Account(id(row, "id"), row)).orElse(null);
    }

    public void available(String username) {
        Checks.require(
                username != null && username.matches("[a-zA-Z0-9_.-]{3,50}"), "账号需为3至50位字母数字或 _.-");
        long n =
                db.count(
                                "SELECT COUNT(*) AS value FROM sys_admin WHERE username=#{p.name}",
                                args("name", username))
                        + db.count(
                                "SELECT COUNT(*) AS value FROM broker WHERE username=#{p.name}",
                                args("name", username));
        if (n > 0) throw new BusinessException(409, "登录名已存在");
    }

    public boolean matches(String raw, String stored) {
        if (stored == null || stored.isBlank()) return false;
        if (stored.startsWith("$2")) return passwords.matches(raw, stored);
        return MessageDigest.isEqual(
                raw.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }

    public Account update(Account account, Map<String, Object> values) {
        if (account.administrator()) db.update("sys_admin", account.sourceId(), values);
        else brokers.update(account.sourceId(), values);
        return get(account.key(), false);
    }

    public Account password(Account account, String raw) {
        return update(account, args("password", passwords.encode(Checks.password(raw))));
    }

    public Account upgradePassword(Account account, String raw) {
        return account.password().startsWith("$2")
                ? account
                : update(account, args("password", passwords.encode(raw)));
    }

    public Account createAdmin(String username, String password, String name) {
        available(username);
        Checks.require(name.length() <= 50, "管理员姓名最多50字");
        long id =
                db.insert(
                        "sys_admin",
                        args(
                                "username",
                                username,
                                "password",
                                passwords.encode(Checks.password(password)),
                                "admin_name",
                                name,
                                "is_deleted",
                                0));
        return get(-id, false);
    }

    public Account createBroker(
            String username,
            String password,
            String name,
            String phone,
            Long store,
            String dept,
            boolean approved) {
        available(username);
        long id =
                brokers.create(
                        args(
                                "broker_name",
                                name,
                                "phone",
                                phone,
                                "store_id",
                                store,
                                "dept",
                                dept,
                                "audit_status",
                                approved ? 1 : 0,
                                "account_status",
                                1),
                        username,
                        passwords.encode(Checks.password(password)));
        return get(id, false);
    }

    public Account profile(Account account, String name, String phone) {
        if (account.administrator()) {
            Checks.require(name.length() <= 50, "管理员姓名最多50字");
            Checks.require(phone == null || phone.isBlank(), "管理员原表没有电话字段");
            return update(account, args("admin_name", name));
        }
        return update(account, args("broker_name", name, "phone", phone));
    }

    public long version(Account account) {
        var values = new TreeMap<>(account.raw());
        values.remove("password");
        values.remove("userId");
        return Long.parseLong(AuthService.hash(db.json(values)).substring(0, 13), 16);
    }

    public void version(Account account, Long requested) {
        if (requested == null || requested != version(account))
            throw new BusinessException(409, "账号已被修改，请刷新后重试");
    }

    public String securityStamp(Account account) {
        return db.json(
                args(
                        "type",
                        account.type(),
                        "id",
                        account.sourceId(),
                        "username",
                        account.username(),
                        "password",
                        account.password(),
                        "status",
                        account.status(),
                        "storeId",
                        account.storeId()));
    }

    public Map<String, Object> role(String code) {
        var row =
                db.one(
                        "SELECT id,code,name,enabled FROM sys_role WHERE code=#{p.code}",
                        args("code", code));
        row.put("name", code.equals("ADMIN") ? "管理员" : "经纪人");
        return row;
    }

    public Map<String, Object> view(Account a) {
        var review =
                db.optional(
                                "SELECT actor_id,created_at FROM operation_log WHERE"
                                    + " resource_type=#{p.type} AND resource_id=#{p.id} AND"
                                    + " event_type IN ('APPROVE','REJECT') ORDER BY id DESC LIMIT"
                                    + " 1",
                                args("type", a.resource(), "id", a.key()))
                        .orElse(Map.of());
        return args(
                "id",
                a.key(),
                "username",
                a.username(),
                "displayName",
                a.name(),
                "phone",
                a.administrator() ? null : a.raw().get("phone"),
                "storeId",
                a.storeId(),
                "brokerId",
                a.administrator() ? null : a.sourceId(),
                "enabled",
                a.status().equals("ACTIVE"),
                "accountStatus",
                a.status(),
                "reviewReason",
                a.raw().get("rejectReason"),
                "reviewedBy",
                review.get("actorId"),
                "reviewedAt",
                review.get("createdAt"),
                "version",
                version(a),
                "createdAt",
                a.raw().get(a.administrator() ? "createTime" : "createdAt"),
                "roles",
                List.of(role(a.type())));
    }

    public Map<String, Object> list(String keyword, Long store, String status, int page, int size) {
        var q = new PageQuery(page, size);
        var p =
                args(
                        "keyword",
                        Checks.search(keyword),
                        "store",
                        store,
                        "status",
                        status,
                        "offset",
                        q.offset(),
                        "size",
                        q.size());
        String name = brokers.nameColumn();
        String source =
                " FROM (SELECT -a.id AS account_key,a.username,a.admin_name AS display_name,NULL AS"
                    + " store_id,'ADMIN' AS role_code,CASE WHEN COALESCE(a.is_deleted,0)=0 THEN"
                    + " 'ACTIVE' ELSE 'DISABLED' END AS account_status FROM sys_admin a UNION ALL"
                    + " SELECT b.id,b.username,b."
                        + name
                        + ",b.store_id,'BROKER',CASE WHEN b.username IS NULL OR TRIM(b.username)=''"
                        + " OR b.password IS NULL OR TRIM(b.password)='' OR b.account_status<>1"
                        + " THEN 'DISABLED' WHEN b.audit_status=0 THEN 'PENDING' WHEN"
                        + " b.audit_status=1 THEN 'ACTIVE' WHEN b.audit_status=2 THEN 'REJECTED'"
                        + " ELSE 'DISABLED' END FROM broker b) accounts WHERE 1=1";
        if (keyword != null && !keyword.isBlank())
            source +=
                    " AND (username LIKE CONCAT('%',#{p.keyword},'%') OR display_name LIKE"
                        + " CONCAT('%',#{p.keyword},'%'))";
        if (store != null) source += " AND store_id=#{p.store}";
        if (status != null) {
            Checks.choice(status, "ACTIVE", "PENDING", "REJECTED", "DISABLED");
            source += " AND account_status=#{p.status}";
        }
        var ids =
                db.list(
                        "SELECT account_key"
                                + source
                                + " ORDER BY role_code,ABS(account_key) DESC LIMIT #{p.size} OFFSET"
                                + " #{p.offset}",
                        p);
        return args(
                "total",
                db.count("SELECT COUNT(*) AS value" + source, p),
                "page",
                q.page(),
                "size",
                q.size(),
                "records",
                ids.stream().map(row -> view(get(id(row, "accountKey"), false))).toList());
    }
}
