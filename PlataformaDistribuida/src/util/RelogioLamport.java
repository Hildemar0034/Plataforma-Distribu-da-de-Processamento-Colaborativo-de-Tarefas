package util;

import java.util.concurrent.atomic.AtomicLong;

public class RelogioLamport {
    private AtomicLong clock = new AtomicLong(0);

    public long tick() {
        return clock.incrementAndGet();
    }

    public long onReceive(long other) {
        long now = clock.get();
        long m = Math.max(now, other);
        clock.set(m);
        return clock.incrementAndGet();
    }

    public long value() {
        return clock.get();
    }
}
