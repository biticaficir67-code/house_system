package system.auth;

import static system.common.DataStore.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.security.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class AuthService {
    private final DataStore db;
    private final AccountStore accounts;
    private final AccountLock lock;
    private final String bootstrapKey;
    private final int tokenHours;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Attempt> failures = new ConcurrentHashMap<>();

    private record Attempt(int count, LocalDateTime until) {}

    public AuthService(
            DataStore db,
            AccountStore accounts,
            AccountLock lock,
            @Value("${app.security.bootstrap-key:}") String bootstrapKey,
            @Value("${app.security.token-hours:12}") int tokenHours) {
        this.db = db;
        this.accounts = accounts;
        this.lock = lock;
        this.bootstrapKey = bootstrapKey;
        this.tokenHours = tokenHours;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public Map<String, Object> bootstrap(String key, AuthModels.Bootstrap body) {
        if (bootstrapKey.isBlank()) throw new BusinessException(404, "初始化入口未启用");
        if (key == null
                || !MessageDigest.isEqual(
                        bootstrapKey.getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(403, "初始化密钥错误");
        lock.acquire();
        Checks.require(
                db.count("SELECT COUNT(*) AS value FROM sys_admin", args()) == 0,
                "管理员原表已有账号，无需初始化");
        var account = accounts.createAdmin(body.username(), body.password(), body.displayName());
        if (db.count(
                        "SELECT COUNT(*) AS value FROM store WHERE name=#{p.name}",
                        args("name", body.storeName()))
                == 0) db.insert("store", args("name", body.storeName(), "enabled", true));
        return args("userId", account.key(), "message", "管理员已创建，请登录；随后清空 BOOTSTRAP_KEY");
    }

    @Transactional
    public Map<String, Object> verifyCredentials(AuthModels.Login body, String client) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(401, "用户名或密码错误");
        String key = hash(client + ":" + body.username().toLowerCase(Locale.ROOT));
        var now = LocalDateTime.now();
        failures.entrySet().removeIf(e -> e.getValue().until().isBefore(now));
        var attempt = failures.get(key);
        if (attempt != null && attempt.count() >= 5)
            throw new BusinessException(429, "登录失败次数过多，请5分钟后重试");
        var account = accounts.find(body.username(), true);
        if (account == null || !accounts.matches(body.password(), account.password())) {
            if (failures.size() > 10000) failures.clear();
            failures.compute(
                    key,
                    (ignored, old) ->
                            new Attempt(old == null ? 1 : old.count() + 1, now.plusMinutes(5)));
            throw new BusinessException(401, "用户名或密码错误");
        }
        failures.remove(key);
        account = accounts.upgradePassword(account, body.password());
        return args("accountKey", account.key());
    }

    @Transactional
    public Map<String, Object> login(AuthModels.Login body, String client) {
        long key = id(verifyCredentials(body, client), "accountKey");
        var account = accounts.get(key, false);
        var principal = principal(account);
        var ticket = issue(account, "b2", tokenHours * 60);
        return args(
                "token",
                ticket.get("token"),
                "tokenType",
                "Bearer",
                "expiresAt",
                ticket.get("expiresAt"),
                "user",
                principal);
    }

    private UserPrincipal principal(AccountStore.Account a) {
        if (!"ACTIVE".equals(a.status()) || !a.hasCredentials()) {
            String message =
                    switch (a.status()) {
                        case "PENDING" -> "经纪人申请待审核";
                        case "REJECTED" ->
                                "申请已拒绝：" + Objects.toString(a.raw().get("rejectReason"), "请联系管理员");
                        default -> "账号已停用";
                    };
            throw new BusinessException(403, message);
        }
        if (!truth(accounts.role(a.type()).get("enabled")))
            throw new BusinessException(403, "该身份已停用");
        if (!a.administrator()
                && (a.storeId() == null
                        || db.count(
                                        "SELECT COUNT(*) AS value FROM store WHERE id=#{p.id} AND"
                                            + " enabled=1",
                                        args("id", a.storeId()))
                                != 1)) throw new BusinessException(403, "经纪人所属门店不可用，请联系管理员");
        return new UserPrincipal(
                a.key(),
                a.username(),
                a.name(),
                a.storeId(),
                a.administrator() ? null : a.sourceId(),
                Set.of(a.type()),
                a.administrator() ? Permissions.ALL : Permissions.BROKER,
                a.administrator() ? "ALL" : "OWN");
    }

    // Negative account keys identify admins; positive keys identify brokers. These keys never
    // replace broker_id on business records. New prefixes reject every pre-refactor token.
    private Map<String, Object> issue(AccountStore.Account a, String purpose, int minutes) {
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String token =
                purpose
                        + "."
                        + a.key()
                        + "."
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        var expires = LocalDateTime.now().plusMinutes(minutes);
        db.insert(
                "auth_session",
                args(
                        "token_hash",
                        tokenHash(token, a),
                        "user_id",
                        a.key(),
                        "expires_at",
                        expires,
                        "revoked",
                        false));
        return args("token", token, "expiresAt", expires);
    }

    private String tokenHash(String token, AccountStore.Account a) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            accounts.securityStamp(a).getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private AccountStore.Account session(String token, String purpose) {
        if (token == null || token.length() > 120) throw new BusinessException(401, "凭证无效");
        String[] bits = token.split("\\.", -1);
        if (bits.length != 3 || !bits[0].equals(purpose) || !bits[2].matches("[a-zA-Z0-9_-]{43}"))
            throw new BusinessException(401, "凭证无效，请重新登录");
        long key;
        try {
            key = Long.parseLong(bits[1]);
        } catch (NumberFormatException e) {
            throw new BusinessException(401, "凭证无效");
        }
        if (key == 0 || key == Long.MIN_VALUE) throw new BusinessException(401, "凭证无效");
        AccountStore.Account a;
        try {
            a = accounts.get(key, false);
        } catch (BusinessException e) {
            throw new BusinessException(401, "账号不存在或凭证已失效");
        }
        if (db.count(
                        "SELECT COUNT(*) AS value FROM auth_session WHERE token_hash=#{p.hash} AND"
                            + " user_id=#{p.id} AND revoked=0 AND expires_at>#{p.now}",
                        args("hash", tokenHash(token, a), "id", key, "now", LocalDateTime.now()))
                != 1) throw new BusinessException(401, "凭证已失效，请重新登录");
        return a;
    }

    public UserPrincipal authenticate(String token) {
        return principal(session(token, "b2"));
    }

    public Map<String, Object> applicationToken(AccountStore.Account account) {
        Checks.require(
                !account.administrator()
                        && Set.of("PENDING", "REJECTED").contains(account.status()),
                "仅待审核或已拒绝申请可获取申请凭证");
        return issue(account, "p2", 20);
    }

    public AccountStore.Account applicant(String token) {
        var a = session(token, "p2");
        if (a.administrator() || !Set.of("PENDING", "REJECTED").contains(a.status()))
            throw new BusinessException(401, "申请凭证已失效");
        return a;
    }

    @Transactional
    public void logout(String token) {
        var a = session(token, "b2");
        db.execute(
                "UPDATE auth_session SET revoked=1 WHERE token_hash=#{p.hash} AND user_id=#{p.id}",
                args("hash", tokenHash(token, a), "id", a.key()));
    }

    @Transactional
    public void changePassword(long key, AuthModels.ChangePassword body) {
        Checks.require(body.oldPassword().getBytes(StandardCharsets.UTF_8).length <= 72, "原密码格式错误");
        var a = accounts.get(key, true);
        Checks.require(accounts.matches(body.oldPassword(), a.password()), "原密码错误");
        accounts.password(a, body.newPassword());
        revokeUser(key);
    }

    public void revokeUser(long key) {
        db.execute("UPDATE auth_session SET revoked=1 WHERE user_id=#{p.id}", args("id", key));
    }
}
