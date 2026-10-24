package system.common;

import static system.common.DataStore.*;

import org.springframework.stereotype.Component;

/** Serialize account changes using a role row present in both supported database baselines. */
@Component
public class AccountLock {
    private final DataStore db;

    public AccountLock(DataStore db) {
        this.db = db;
    }

    // Call from the surrounding account transaction, before locking individual users.
    public void acquire() {
        db.optional("SELECT id FROM sys_role WHERE code='ADMIN' FOR UPDATE", args())
                .orElseThrow(() -> new BusinessException(503, "数据库缺少管理员角色，请核对当前数据库底稿"));
    }
}
