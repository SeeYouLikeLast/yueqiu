package com.hm.badminton;

import com.hm.badminton.utils.OrderNoGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNoGeneratorTest {

    @Test
    void shouldGenerateUniqueStandardUuidOrderNumbers() {
        String first = OrderNoGenerator.next();
        String second = OrderNoGenerator.next();

        assertThat(first).hasSize(36).isNotEqualTo(second);
        assertThat(UUID.fromString(first).toString()).isEqualTo(first);
        assertThat(UUID.fromString(second).toString()).isEqualTo(second);
    }
}
