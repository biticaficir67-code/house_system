package system.auth;

import static system.common.DataStore.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.house.HouseService;
import system.security.AccessScope;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RegistrationService {
    private final DataStore db;
    private final PasswordEncoder passwords;
    private final AuthService auth;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final AuditService audit;

    public RegistrationService(
            DataStore db,
            PasswordEncoder passwords,
            AuthService auth,
            ResourceAccess access,
            AccessScope scope,
            AuditService audit) {
        this.db = db;
        this.passwords = passwords;
        this.auth = auth;
        this.access = access;
        this.scope = scope;
        this.audit = audit;
    }

    private Map<String, Object> safe(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>();
        for (String key :
                List.of(
                        "id",
                        "username",
                        "displayName",
                        "phone",
                        "accountStatus",
                        "storeId",
                        "brokerId",
                        "reviewReason",
                        "reviewedBy",
                        "reviewedAt",
                        "version")) result.put(key, row.get(key));
        return result;
    }

    private Map<String, Object> ticket(long id) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        var expires = LocalDateTime.now().plusMinutes(20);
        db.insert(
                "application_session",
                args(
                        "user_id",
                        id,
                        "token_hash",
                        AuthService.hash(token),
                        "expires_at",
                        expires,
                        "revoked",
                        false));
        return args(
                "applicationToken",
                token,
                "expiresAt",
                expires,
                "application",
                safe(user(id, false)));
    }

    private Map<String, Object> user(long id, boolean lock) {
        return db.one(
                "SELECT * FROM sys_user WHERE id=#{p.id}" + (lock ? " FOR UPDATE" : ""),
                args("id", id));
    }

    private long applicant(String token) {
        if (token == null || token.length() < 32 || token.length() > 256)
            throw new BusinessException(401, "申请凭证无效");
        return id(
                db.optional(
                                "SELECT s.user_id FROM application_session s JOIN sys_user u ON"
                                    + " u.id=s.user_id WHERE s.token_hash=#{p.hash} AND s.revoked=0"
                                    + " AND s.expires_at>#{p.now} AND u.account_status IN"
                                    + " ('PENDING','REJECTED')",
                                args("hash", AuthService.hash(token), "now", LocalDateTime.now()))
                        .orElseThrow(() -> new BusinessException(401, "申请凭证已失效，请重新核验账号密码")),
                "userId");
    }

    @Transactional
    public Object register(AuthModels.Register b) {
        Checks.require(b.password().equals(b.confirmPassword()), "两次密码不一致");
        // Serialize admission with the one-time initializer, so pending registrations cannot block
        // first admin setup.
        db.one("SELECT id FROM sys_bootstrap WHERE id=1 FOR UPDATE", args());
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM sys_user u JOIN sys_user_role ur ON"
                                    + " ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE"
                                    + " r.code='ADMIN' AND u.account_status='ACTIVE' AND"
                                    + " u.enabled=1",
                                args())
                        > 0,
                "请先由系统负责人初始化管理员");
        long id =
                db.insert(
                        "sys_user",
                        args(
                                "username",
                                b.username(),
                                "password_hash",
                                passwords.encode(Checks.password(b.password())),
                                "display_name",
                                b.displayName(),
                                "phone",
                                b.phone(),
                                "account_status",
                                "PENDING",
                                "enabled",
                                false,
                                "version",
                                0L));
        long role =
                id(
                        db.one("SELECT id FROM sys_role WHERE code='BROKER' AND enabled=1", args()),
                        "id");
        db.execute(
                "INSERT INTO sys_user_role(user_id,role_id) VALUES(#{p.user},#{p.role})",
                args("user", id, "role", role));
        audit.recordAs(id, "user", id, "REGISTER", "提交经纪人注册申请", null, safe(user(id, false)));
        return ticket(id);
    }

    @Transactional
    public Object session(AuthModels.Login b, String client) {
        var row = auth.verifyCredentials(b, client);
        Checks.choice((String) row.get("accountStatus"), "PENDING", "REJECTED");
        return ticket(id(row, "id"));
    }

    public Object application(String token) {
        long id = applicant(token);
        return args(
                "application", safe(user(id, false)), "history", audit.list("user", id, 1, 100));
    }

    @Transactional
    public Object reapply(String token, AuthModels.Reapply b) {
        long id = applicant(token);
        var row = user(id, true);
        HouseService.version(row, b.version());
        Checks.require("REJECTED".equals(row.get("accountStatus")), "只有已拒绝的申请可以重新提交");
        var values =
                args(
                        "display_name",
                        b.displayName(),
                        "phone",
                        b.phone(),
                        "account_status",
                        "PENDING",
                        "enabled",
                        false,
                        "review_reason",
                        null,
                        "reviewed_by",
                        null,
                        "reviewed_at",
                        null,
                        "version",
                        id(row, "version") + 1);
        if (b.password() != null) {
            Checks.require(b.password().equals(b.confirmPassword()), "两次密码不一致");
            values.put("password_hash", passwords.encode(Checks.password(b.password())));
        } else Checks.require(b.confirmPassword() == null, "修改密码时须同时填写新密码");
        db.update("sys_user", id, values);
        revokeApplications(id);
        audit.recordAs(id, "user", id, "REAPPLY", "重新提交注册申请", safe(row), safe(user(id, false)));
        return ticket(id);
    }

    @Transactional
    public Object review(long id, AuthModels.Review b) {
        var row = user(id, true);
        HouseService.version(row, b.version());
        Checks.require("PENDING".equals(row.get("accountStatus")), "该申请已经处理，请刷新");
        Long broker = null;
        if (b.approved()) {
            Checks.require(b.storeId() != null, "通过申请时必须选择门店");
            access.store(b.storeId());
            if (b.brokerId() != null) {
                var old =
                        db.one(
                                "SELECT * FROM broker WHERE id=#{p.id} FOR UPDATE",
                                args("id", b.brokerId()));
                Checks.require(old.get("userId") == null, "该经纪人档案已绑定账号");
                Checks.require(
                        old.get("storeId") != null && id(old, "storeId") == b.storeId(),
                        "经纪人档案不属于该门店");
                broker = b.brokerId();
                db.update(
                        "broker",
                        broker,
                        args(
                                "user_id",
                                id,
                                "broker_name",
                                row.get("displayName"),
                                "phone",
                                row.get("phone")));
            } else
                broker =
                        db.insert(
                                "broker",
                                args(
                                        "broker_name",
                                        row.get("displayName"),
                                        "phone",
                                        row.get("phone"),
                                        "store_id",
                                        b.storeId(),
                                        "user_id",
                                        id,
                                        "dept",
                                        db.one(
                                                        "SELECT name FROM store WHERE id=#{p.id}",
                                                        args("id", b.storeId()))
                                                .get("name")));
        }
        db.update(
                "sys_user",
                id,
                args(
                        "account_status",
                        b.approved() ? "ACTIVE" : "REJECTED",
                        "enabled",
                        b.approved(),
                        "store_id",
                        b.approved() ? b.storeId() : null,
                        "broker_id",
                        broker,
                        "review_reason",
                        b.reason(),
                        "reviewed_by",
                        scope.user().id(),
                        "reviewed_at",
                        LocalDateTime.now(),
                        "version",
                        id(row, "version") + 1));
        auth.revokeUser(id);
        revokeApplications(id);
        audit.record(
                "user",
                id,
                b.approved() ? "APPROVE" : "REJECT",
                b.reason(),
                safe(row),
                safe(user(id, false)));
        return safe(user(id, false));
    }

    private void revokeApplications(long id) {
        db.execute(
                "UPDATE application_session SET revoked=1 WHERE user_id=#{p.id}", args("id", id));
    }

    public Object profile() {
        return safe(user(scope.user().id(), false));
    }

    @Transactional
    public Object profile(AuthModels.Profile b) {
        long id = scope.user().id();
        var row = user(id, true);
        HouseService.version(row, b.version());
        db.update(
                "sys_user",
                id,
                args(
                        "display_name",
                        b.displayName(),
                        "phone",
                        b.phone(),
                        "version",
                        id(row, "version") + 1));
        if (row.get("brokerId") != null)
            db.update(
                    "broker",
                    id(row, "brokerId"),
                    args("broker_name", b.displayName(), "phone", b.phone()));
        audit.record("user", id, "PROFILE", "修改本人资料", safe(row), safe(user(id, false)));
        return safe(user(id, false));
    }
}
