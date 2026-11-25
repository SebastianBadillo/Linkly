package io.github.sebastianbadillo.linkly.clicks;

import io.github.sebastianbadillo.linkly.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The "behind" part of write-behind: every few seconds, moves the buffered clicks from Valkey
 * to Postgres. Thousands of clicks on one link become a single UPDATE.
 *
 * Safe with several app instances: SPOP and GETDEL are atomic, so each click is taken once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickFlusher {

    static final int BATCH_SIZE = 1000;

    private final ClickCounter counter;
    private final LinkRepository repository;

    // fixedDelay: the next run starts this long AFTER the previous one ends, so runs never overlap.
    @Scheduled(
            fixedDelayString = "${linkly.clicks.flush-interval}",
            initialDelayString = "${linkly.clicks.flush-interval}")
    public void flush() {
        List<String> codes;
        try {
            codes = counter.takePendingCodes(BATCH_SIZE);
        } catch (RuntimeException e) {
            log.warn("Click flush skipped, Valkey is unavailable: {}", e.getMessage());
            return;
        }

        int failed = 0;
        for (String code : codes) {
            if (!flushOne(code)) {
                failed++;
            }
        }
        if (failed > 0) { // one summary line per run, not one per link
            log.warn("Click flush: {} of {} links failed", failed, codes.size());
        }
    }

    /** One link at a time, so one failure doesn't stop the others. */
    private boolean flushOne(String code) {
        long count = 0;
        try {
            count = counter.takeCount(code);
            if (count > 0) {
                repository.addClicks(code, count); // 0 rows changed = deleted link: nothing to do
            }
            return true;
        } catch (RuntimeException e) {
            log.debug("Could not flush clicks for '{}': {}", code, e.getMessage());
            if (count > 0) {
                restore(code, count); // compensating action: GETDEL can't be rolled back by a transaction
            }
            return false;
        }
    }

    private void restore(String code, long count) {
        try {
            counter.restore(code, count);
        } catch (RuntimeException e) {
            log.error("Lost {} clicks for '{}': could not restore them: {}", count, code, e.getMessage());
        }
    }
}
