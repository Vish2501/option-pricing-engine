package com.optionpricing.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TickerSymbolTest {
    @Test
    void normalizeTrimsAndUpperCases() {
        assertThat(TickerSymbol.normalize("  brk.b ")).isEqualTo("BRK.B");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "AAPL<script>", "../etc/passwd", "ES=F", "^GSPC", "WAYTOOLONGSYMBOL"})
    void normalizeRejectsAnythingNotShapedLikeATicker(String raw) {
        assertThatThrownBy(() -> TickerSymbol.normalize(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> TickerSymbol.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
