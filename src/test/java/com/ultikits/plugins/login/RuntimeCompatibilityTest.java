package com.ultikits.plugins.login;

import com.ultikits.plugins.login.util.RuntimeCompatibility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCompatibilityTest {
    @ParameterizedTest
    @CsvSource({
            "1.19.2, 6.3.0, REFUSE",
            "1.20.6-R0.1-SNAPSHOT, 6.3.1-SNAPSHOT, REFUSE",
            "1.20.6, 6.4.0, ALLOW",
            "1.20.6, 6.4.0-SNAPSHOT, ALLOW",
            "1.21, 6.3.0, ALLOW",
            "1.21.11-R0.1-SNAPSHOT, 6.3.0, ALLOW",
            "1.19.2, 6.10.0, ALLOW",
            "1.20.6, 7.0.0, ALLOW",
            "2.0.0, 6.3.0, ALLOW",
            "1.20.6, 6.3.99, REFUSE",
            "garbage, 6.3.0, UNKNOWN",
            "1.20.6, garbage, UNKNOWN",
            ", 6.3.0, UNKNOWN",
            "1.20.6, , UNKNOWN",
            "1.20.6, 999999999999999999999.0.0, UNKNOWN",
            "1.20.6, 6.4.0oops, UNKNOWN",
            "1.21.11, garbage, UNKNOWN"
    })
    void comparesNumericVersionsWithoutGuessing(String minecraft, String framework,
                                                RuntimeCompatibility.Result expected) {
        assertThat(RuntimeCompatibility.check(minecraft, framework)).isEqualTo(expected);
    }
}
