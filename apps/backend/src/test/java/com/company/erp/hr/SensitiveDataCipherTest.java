package com.company.erp.hr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataCipherTest {
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutStoringPlaintext() {
        SensitiveDataCipher cipher = new SensitiveDataCipher(TEST_KEY);
        String first = cipher.encrypt("012345678901");
        String second = cipher.encrypt("012345678901");

        assertThat(first).startsWith("v1:").doesNotContain("012345678901");
        assertThat(second).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("012345678901");
    }
}
