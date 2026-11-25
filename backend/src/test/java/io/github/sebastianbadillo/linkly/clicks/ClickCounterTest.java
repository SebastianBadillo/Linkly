package io.github.sebastianbadillo.linkly.clicks;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.valkey.springframework.data.valkey.core.SetOperations;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ValueOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests: Valkey is a mock, so we can make it fail on demand. */
@ExtendWith(MockitoExtension.class)
class ClickCounterTest {

    @Mock
    private StringValkeyTemplate valkey;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private SetOperations<String, String> sets;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry(); // a real, in-memory registry
    private ClickCounter counter;

    @BeforeEach
    void setUp() {
        counter = new ClickCounter(valkey, meters);
    }

    @Test
    void recordIncrementsTheCounterBeforeListingTheCode() {
        when(valkey.opsForValue()).thenReturn(values);
        when(valkey.opsForSet()).thenReturn(sets);

        counter.record("BF26");

        InOrder order = inOrder(values, sets);
        order.verify(values).increment("click:bf26", 1);
        order.verify(sets).add("clicks:pending", "bf26");
    }

    @Test
    void recordSwallowsValkeyErrorsAndCountsThemInAMetric() {
        when(valkey.opsForValue()).thenReturn(values);
        when(values.increment("click:bf26", 1)).thenThrow(new IllegalStateException("Valkey down"));

        assertThatCode(() -> counter.record("bf26")).doesNotThrowAnyException();

        assertThat(meters.counter("linkly.clicks.failed").count()).isEqualTo(1);
        verifyNoInteractions(sets);
    }

    @Test
    void takeCountReturnsZeroWhenThereIsNoCounter() {
        when(valkey.opsForValue()).thenReturn(values);
        when(values.getAndDelete("click:bf26")).thenReturn(null);

        assertThat(counter.takeCount("bf26")).isZero();
    }

    @Test
    void restoreAddsTheClicksBackAndListsTheCodeAgain() {
        when(valkey.opsForValue()).thenReturn(values);
        when(valkey.opsForSet()).thenReturn(sets);

        counter.restore("bf26", 4200);

        InOrder order = inOrder(values, sets);
        order.verify(values).increment("click:bf26", 4200);
        order.verify(sets).add("clicks:pending", "bf26");
    }
}
