package com.ultikits.plugins.login.service;

import com.ultikits.plugins.login.entity.AccountData;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.exceptions.ErrorCode;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code AccountWrites#update} is the one way this module writes an account row (UltiKits/UltiLogin#47):
 * a count of 0 and the wrapped {@link IllegalAccessException} are failures that are logged and returned as
 * {@code false}; every other storage failure propagates, exactly as it did before the write was counted.
 */
@DisplayName("AccountWrites (#47)")
class AccountWritesTest {

    @SuppressWarnings("unchecked")
    private final DataOperator<AccountData> operator = mock(DataOperator.class);
    private final PluginLogger logger = mock(PluginLogger.class);
    private final AccountData account = new AccountData();

    @Test
    @DisplayName("one stored row written: true, nothing logged")
    void rowWritten() {
        when(operator.updateCounted(account)).thenReturn(1);

        assertThat(AccountWrites.update(operator, logger, account, "FAILED", "ROW GONE")).isTrue();

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("no stored row matched: false, the caller's line followed by the reason is logged")
    void rowGone() {
        when(operator.updateCounted(account)).thenReturn(0);

        assertThat(AccountWrites.update(operator, logger, account, "FAILED", "ROW GONE")).isFalse();

        verify(logger).error("FAILED: ROW GONE");
    }

    @Test
    @DisplayName("the IllegalAccessException the framework wraps: false, the caller's line and that exception are logged")
    void unreadableEntity() {
        IllegalAccessException cause = new IllegalAccessException("no access");
        when(operator.updateCounted(account)).thenThrow(
                new DataAccessException(ErrorCode.DATA_ENTITY_INVALID, "Failed to access entity fields", cause));

        assertThat(AccountWrites.update(operator, logger, account, "FAILED", "ROW GONE")).isFalse();

        verify(logger).error("FAILED", cause);
        verify(logger, never()).error("FAILED: ROW GONE");
    }

    @Test
    @DisplayName("any other storage failure propagates and is not turned into false")
    void otherStorageFailurePropagates() {
        DataAccessException failure = new DataAccessException(ErrorCode.DATA_OPERATION_FAILED, "connection lost");
        when(operator.updateCounted(any(AccountData.class))).thenThrow(failure);

        assertThatThrownBy(() -> AccountWrites.update(operator, logger, account, "FAILED", "ROW GONE"))
                .isSameAs(failure);

        verifyNoInteractions(logger);
    }
}
