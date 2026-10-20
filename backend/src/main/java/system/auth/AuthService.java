package system.auth;

import static system.common.DataStore.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.security.*;
import system.security.Permissions;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final DataStore db;
    private final PasswordEncoder passwords;
    private final String bootstrapKey;
    private final int tokenHours;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Attempt> failures = new ConcurrentHashMap<>();

    private record Attempt(int count, LocalDateTime until) {}

    public AuthService(
            DataStore db,
            PasswordEncoder passwords,
            @Value("${app.security.bootstrap-key:}") String bootstrapKey,
            @Value("${app.security.token-hours:12}") int tokenHours) {
        this.db = db;
        this.passwords = passwords;
        this.bootstrapKey = bootstrapKey;
        this.tokenHours = tokenHours;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public Map<String, Object> bootstrap(String key, AuthModels.Bootstrap request) {
        if (bootstrapKey.isBlank()) throw new BusinessException(404, "初始化入口未启用");
        if (key == null
                || !MessageDigest.isEqual(
                        bootstrapKey.getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(403, "初始化密钥错误");
        db.one("SELECT * FROM sys_bootstrap WHERE id=1 FOR UPDATE", args());
        Checks.require(db.count("SELECT COUNT(*) AS value FROM sys_user", args()) == 0, "系统已初始化");
        long storeId =
                db.optional(
                                "SELECT id FROM store WHERE name=#{p.name}",
                                args("name", request.storeName()))
                        .map(row -> id(row, "id"))
                        .orElseGet(
                                () ->
                                        db.insert(
                                                "store",
                                                args(
                                                        "name",
                                                        request.storeName(),
                                                        "enabled",
                                                        true)));
        long userId =
                db.insert(
                        "sys_user",
                        args(
                                "username",
                                request.username(),
                                "password_hash",
                                passwords.encode(Checks.password(request.password())),
                                "display_name",
                                request.displayName(),
                                "store_id",
                                storeId,
                                "enabled",
                                true));
        long roleId = id(db.one("SELECT id FROM sys_role WHERE code='ADMIN'", args()), "id");
        db.execute(
                "INSERT INTO sys_user_role(user_id,role_id) VALUES(#{p.user},#{p.role})",
                args("user", userId, "role", roleId));
        return args("userId", userId, "message", "管理员已创建，请登录；随后清空 BOOTSTRAP_KEY");
    }

    @Transactional
    public Map<String, Object> login(AuthModels.Login request, String client) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(401, "用户名或密码错误");
        String key = hash(client + ":" + request.username().toLowerCase(Locale.ROOT));
        LocalDateTime now = LocalDateTime.now();
        failures.entrySet().removeIf(entry -> entry.getValue().until().isBefore(now));
        Attempt attempt = failures.get(key);
        if (attempt != null && attempt.count() >= 5)
            throw new BusinessException(429, "登录失败次数过多，请 5 分钟后重试");
        var user =
                db.optional(
                                "SELECT * FROM sys_user WHERE username=#{p.username}",
                                args("username", request.username()))
                        .orElse(null);
        if (user == null
                || !truth(user.get("enabled"))
                || !passwords.matches(request.password(), (String) user.get("passwordHash"))) {
            if (failures.size() > 10_000) failures.clear();
            failures.compute(
                    key,
                    (ignored, old) ->
                            new Attempt(old == null ? 1 : old.count() + 1, now.plusMinutes(5)));
            throw new BusinessException(401, "用户名或密码错误");
        }
        UserPrincipal principal = principal(user);
        if (principal.permissions().isEmpty()) throw new BusinessException(403, "账号尚未分配可用角色");
        failures.remove(key);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expires = now.plusHours(tokenHours);
        db.insert(
                "auth_session",
                args(
                        "token_hash",
                        hash(token),
                        "user_id",
                        principal.id(),
                        "expires_at",
                        expires,
                        "revoked",
                        false));
        return args("token", token, "tokenType", "Bearer", "expiresAt", expires, "user", principal);
    }

    public UserPrincipal authenticate(String token) {
        if (token.length() < 32 || token.length() > 256)
            throw new BusinessException(401, "Token 无效");
        var row =
                db.optional(
                                "SELECT u.* FROM auth_session s JOIN sys_user u ON u.id=s.user_id"
                                        + " WHERE s.token_hash=#{p.hash} AND s.revoked=0 AND"
                                        + " s.expires_at>#{p.now} AND u.enabled=1",
                                args("hash", hash(token), "now", LocalDateTime.now()))
                        .orElseThrow(() -> new BusinessException(401, "Token 已失效"));
        return principal(row);
    }

    private UserPrincipal principal(Map<String, Object> row) {
        Set<String> roles = new HashSet<>();
        for (var role :
                db.list(
                        "SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id "
                                + "WHERE ur.user_id=#{p.id} AND r.enabled=1"
                                + " AND r.code IN ('ADMIN','BROKER')",
                        args("id", id(row, "id")))) {
            roles.add((String) role.get("code"));
        }
        if (roles.size() != 1) throw new BusinessException(403, "账号须配置一个管理员或经纪人身份，请联系管理员");
        boolean administrator = roles.contains("ADMIN");
        if (!administrator
                && (row.get("storeId") == null
                        || db.count(
                                        "SELECT COUNT(*) AS value FROM store WHERE id=#{p.id} AND"
                                                + " enabled=1",
                                        args("id", row.get("storeId")))
                                == 0)) throw new BusinessException(401, "门店已停用，请联系管理员");
        if (!administrator
                && (row.get("brokerId") == null
                        || db.count(
                                        "SELECT COUNT(*) AS value FROM broker WHERE id=#{p.broker}"
                                                + " AND user_id=#{p.user} AND store_id=#{p.store}",
                                        args(
                                                "broker",
                                                row.get("brokerId"),
                                                "user",
                                                row.get("id"),
                                                "store",
                                                row.get("storeId")))
                                != 1)) throw new BusinessException(403, "经纪人账号未正确关联业务档案，请联系管理员");
        Set<String> permissions = administrator ? Permissions.ALL : Permissions.BROKER;
        return new UserPrincipal(
                id(row, "id"),
                (String) row.get("username"),
                (String) row.get("displayName"),
                row.get("storeId") == null ? null : id(row, "storeId"),
                row.get("brokerId") == null ? null : id(row, "brokerId"),
                Set.copyOf(roles),
                Set.copyOf(permissions),
                administrator ? "ALL" : "OWN");
    }

    @Transactional
    public void logout(String token) {
        db.execute(
                "UPDATE auth_session SET revoked=1 WHERE token_hash=#{p.hash}",
                args("hash", hash(token)));
    }

    @Transactional
    public void changePassword(long userId, AuthModels.ChangePassword request) {
        Checks.require(
                request.oldPassword().getBytes(StandardCharsets.UTF_8).length <= 72, "原密码格式错误");
        var user = db.one("SELECT * FROM sys_user WHERE id=#{p.id} FOR UPDATE", args("id", userId));
        Checks.require(
                passwords.matches(request.oldPassword(), (String) user.get("passwordHash")),
                "原密码错误");
        db.update(
                "sys_user",
                userId,
                args("password_hash", passwords.encode(Checks.password(request.newPassword()))));
        revokeUser(userId);
    }

    public void revokeUser(long id) {
        db.execute("UPDATE auth_session SET revoked=1 WHERE user_id=#{p.id}", args("id", id));
    }
}
