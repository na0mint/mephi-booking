package ru.mephi.booking;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
class HotelCircuitBreakerTest {
    @Test
    void opensAfterFiveFailuresAndAllowsOneProbeAfterWait() throws Exception {
        AtomicLong clock = new AtomicLong();
        HotelCircuitBreaker breaker = new HotelCircuitBreaker("test", Duration.ofSeconds(1), clock::get);
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new HotelUnavailableException(null);
            })).isInstanceOf(HotelUnavailableException.class);
        assertThat(breaker.state()).isEqualTo(HotelCircuitBreaker.State.OPEN);
        AtomicBoolean called = new AtomicBoolean();
        assertThatThrownBy(() -> breaker.execute(() -> {
            called.set(true);
            return true;
        })).isInstanceOf(HotelUnavailableException.class);
        assertThat(called).isFalse();
        clock.addAndGet(Duration.ofSeconds(2).toNanos());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var probe = executor.submit(() -> breaker.execute(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> breaker.execute(() -> true))
                    .isInstanceOf(HotelUnavailableException.class);
            release.countDown();
            assertThat(probe.get(2, TimeUnit.SECONDS)).isTrue();
            assertThat(breaker.state()).isEqualTo(HotelCircuitBreaker.State.CLOSED);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
    @Test
    void businessConflictsDoNotOpenCircuit() {
        var breaker = new HotelCircuitBreaker("test", Duration.ofSeconds(1));
        for (int i = 0; i < 10; i++)
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new HotelRejectedException();
            })).isInstanceOf(HotelRejectedException.class);
        assertThat(breaker.state()).isEqualTo(HotelCircuitBreaker.State.CLOSED);
    }
}
