package com.whitxowl.authservice.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUtilTest {

    @Test
    void sha256_shouldProduceValidHexHash() {
        String input = "test-password";
        String hash = TokenUtil.sha256(input);

        assertThat(hash).isNotBlank();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    void sha256_shouldBeDeterministic() {
        String input = "same-input";
        assertThat(TokenUtil.sha256(input)).isEqualTo(TokenUtil.sha256(input));
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 32, 64})
    void generateSecureHex_shouldProduceCorrectLength(int bytes) {
        String hex = TokenUtil.generateSecureHex(bytes);
        assertThat(hex).hasSize(bytes * 2);
        assertThat(hex).matches("[0-9a-f]+");
    }

    @Test
    void generateSecureHex_shouldProduceDifferentValues() {
        String hex1 = TokenUtil.generateSecureHex(32);
        String hex2 = TokenUtil.generateSecureHex(32);
        assertThat(hex1).isNotEqualTo(hex2);
    }
}
