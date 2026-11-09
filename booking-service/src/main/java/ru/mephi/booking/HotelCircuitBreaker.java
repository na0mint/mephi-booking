package ru.mephi.booking;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** Thread-safe circuit metadata. No monitor is held while performing an HTTP call. */
final class HotelCircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }
    private static final Logger log = LoggerFactory.getLogger(HotelCircuitBreaker.class);
    private final String name;
    private final long openNanos;
    private final LongSupplier clock;
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAt;
    private long generation;
    HotelCircuitBreaker(String name, Duration wait) {
        this(name, wait, System::nanoTime);
    }
    HotelCircuitBreaker(String name, Duration wait, LongSupplier clock) {
        this.name = name;
        this.openNanos = wait.toNanos();
        this.clock = clock;
    }
    <T> T execute(Supplier<T> action) {
        long permit = acquire();
        try {
            T result = action.get();
            complete(permit, true);
            return result;
        } catch (HotelRejectedException e) {
            complete(permit, true);
            throw e;
        } catch (RuntimeException e) {
            complete(permit, false);
            throw e;
        }
    }
    private synchronized long acquire() {
        if (state == State.OPEN && clock.getAsLong() - openedAt >= openNanos)
            transition(State.HALF_OPEN);
        else if (state != State.CLOSED)
            throw new HotelUnavailableException(new IllegalStateException("Hotel circuit is " + state));
        return generation;
    }
    private synchronized void complete(long permit, boolean success) {
        if (permit != generation)
            return; // Ignore late responses from an earlier circuit generation.
        if (state == State.HALF_OPEN) {
            if (success) {
                consecutiveFailures = 0;
                transition(State.CLOSED);
            } else
                open();
            return;
        }
        if (success)
            consecutiveFailures = 0;
        else if (++consecutiveFailures >= 5)
            open();
    }
    private void open() {
        openedAt = clock.getAsLong();
        transition(State.OPEN);
    }
    private void transition(State next) {
        State previous = state;
        state = next;
        generation++;
        log.warn("event=circuit_transition name={} from={} to={}", name, previous, next);
    }
    synchronized State state() {
        return state;
    }
}
