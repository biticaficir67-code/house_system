package system.auth;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.security.AccessScope;

import java.util.*;

@Service
public class RegistrationService {
    private final DataStore db;
    private final AccountStore accounts;
    private final AccountLock lock;
    private final AuthService auth;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final AuditService audit;

    public RegistrationService(
            DataStore db,
            AccountStore accounts,
            AccountLock lock,
            AuthService auth,
            ResourceAccess access,
            AccessScope scope,
            AuditService audit) {
        this.db = db;
        this.accounts = accounts;
        this.lock = lock;
        this.auth = auth;
        this.access = access;
        this.scope = scope;
        this.audit = audit;
    }

    private Object ticket(AccountStore.Account a) {
        var t = auth.applicationToken(a);
        return args(
                "applicationToken",
                t.get("token"),
                "expiresAt",
                t.get("expiresAt"),
                "application",
                accounts.view(a));
    }

    @Transactional
    public Object register(AuthModels.Register b) {
        Checks.require(b.password().equals(b.confirmPassword()), "两次密码不一致");
        lock.acquire();
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM sys_admin WHERE"
                                    + " COALESCE(is_deleted,0)=0",
                                args())
                        > 0,
                "请先初始化管理员");
        var a =
                accounts.createBroker(
                        b.username(), b.password(), b.displayName(), b.phone(), null, null, false);
        audit.recordAs(
                a.key(), a.resource(), a.key(), "REGISTER", "提交经纪人申请", null, accounts.view(a));
        return ticket(a);
    }

    @Transactional
    public Object session(AuthModels.Login b, String client) {
        long key = id(auth.verifyCredentials(b, client), "accountKey");
        return ticket(accounts.get(key, false));
    }

    public Object application(String token) {
        var a = auth.applicant(token);
        return args(
                "application",
                accounts.view(a),
                "history",
                audit.list(a.resource(), a.key(), 1, 100));
    }

    @Transactional
    public Object reapply(String token, AuthModels.Reapply b) {
        lock.acquire();
        var a = accounts.get(auth.applicant(token).key(), true);
        accounts.version(a, b.version());
        Checks.require(a.status().equals("REJECTED"), "只有已拒绝的申请可以重新提交");
        var before = accounts.view(a);
        a = accounts.profile(a, b.displayName(), b.phone());
        if (b.password() != null) {
            Checks.require(b.password().equals(b.confirmPassword()), "两次密码不一致");
            a = accounts.password(a, b.password());
        } else Checks.require(b.confirmPassword() == null, "修改密码时须同时填写新密码");
        a = accounts.update(a, args("audit_status", 0, "reject_reason", null));
        auth.revokeUser(a.key());
        audit.recordAs(
                a.key(), a.resource(), a.key(), "REAPPLY", "重新提交申请", before, accounts.view(a));
        return ticket(a);
    }

    @Transactional
    public Object review(long key, AuthModels.Review b) {
        lock.acquire();
        var a = accounts.get(key, true);
        accounts.version(a, b.version());
        Checks.require(!a.administrator() && a.status().equals("PENDING"), "该申请已经处理，请刷新");
        Checks.require(b.brokerId() == null || b.brokerId() == a.sourceId(), "申请就是原经纪人记录，不能另绑其他档案");
        var before = accounts.view(a);
        var values =
                args(
                        "audit_status",
                        b.approved() ? 1 : 2,
                        "reject_reason",
                        b.approved() ? null : b.reason());
        if (b.approved()) {
            Checks.require(b.storeId() != null, "通过申请时必须选择门店");
            access.store(b.storeId());
            Checks.require(
                    a.storeId() == null || a.storeId().equals(b.storeId()), "已有门店的档案须在原门店审核");
            values.put("store_id", b.storeId());
            values.put(
                    "dept",
                    db.one("SELECT name FROM store WHERE id=#{p.id}", args("id", b.storeId()))
                            .get("name"));
            values.put("account_status", 1);
        }
        a = accounts.update(a, values);
        auth.revokeUser(a.key());
        audit.record(
                a.resource(),
                a.key(),
                b.approved() ? "APPROVE" : "REJECT",
                b.reason(),
                before,
                accounts.view(a));
        return accounts.view(a);
    }

    public Object profile() {
        return accounts.view(accounts.get(scope.user().id(), false));
    }

    @Transactional
    public Object profile(AuthModels.Profile b) {
        var a = accounts.get(scope.user().id(), true);
        accounts.version(a, b.version());
        var before = accounts.view(a);
        a = accounts.profile(a, b.displayName(), b.phone());
        audit.record(a.resource(), a.key(), "PROFILE", "修改本人资料", before, accounts.view(a));
        return accounts.view(a);
    }
}
