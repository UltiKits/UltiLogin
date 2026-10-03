package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

/**
 * The one way this module writes an account row (UltiKits/UltiLogin#47).
 * <p>
 * {@code DataOperator#update(T)} writes nothing and returns normally when the stored row has gone, for
 * example because another server on a shared database ran {@code /logadmin unregister} between this
 * module's read and its write. {@link DataOperator#updateCounted} returns {@code 0} then, and this
 * helper turns that into the failure a thrown write already is: the caller's own error line followed by
 * a reason, and {@code false}, so each caller keeps the return value it has for a failed write.
 * <p>
 * 账号行的唯一写入方式：存储的行已不存在时（例如共用数据库的另一台服务器恰好在读写之间删除了账号），
 * 写入落空；这里把落空当作失败处理，而不是当作已写成功。
 */
final class AccountWrites {

    private AccountWrites() {
    }

    /**
     * Writes {@code account} by its id.
     *
     * @param operator       the account table's operator
     * @param logger         where a failed write is reported
     * @param account        the account row to write, as read
     * @param failureLine    the caller's own error line, already translated
     * @param rowGoneReason  why a write that matched no row is reported, already translated
     * @return {@code true} when one stored row was written; {@code false} when the row no longer exists or
     *         the entity's fields could not be read (both logged)
     */
    static boolean update(DataOperator<AccountData> operator, PluginLogger logger, AccountData account,
                          String failureLine, String rowGoneReason) {
        try {
            if (operator.updateCounted(account) > 0) {
                return true;
            }
            logger.error(failureLine + ": " + rowGoneReason);
            return false;
        } catch (DataAccessException e) {
            // updateCounted wraps the IllegalAccessException that update(T) declares; that is the failure
            // every site caught before, and it is handled the same way. Any other storage failure
            // propagated before and still does.
            if (e.getCause() instanceof IllegalAccessException) {
                logger.error(failureLine, e.getCause());
                return false;
            }
            throw e;
        }
    }
}
