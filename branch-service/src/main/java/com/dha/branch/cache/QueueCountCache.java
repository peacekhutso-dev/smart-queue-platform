package com.dha.branch.cache;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local, in-memory, event-fed cache of live per-branch queue counts — fed by
 * QueueEventListener off queue.events, replacing what used to be a
 * synchronous call to the monolith on every request (QueueStatsClient).
 * In-memory rather than a DB table on purpose: this is high-churn,
 * self-healing data (every queue mutation republishes the count), so nothing
 * here is worth persisting — a restarted instance is simply empty until the
 * next event for each branch arrives.
 */
@Component
public class QueueCountCache {

    private final Map<Long, Long> counts = new ConcurrentHashMap<>();

    public void update(Long branchId, Long activeCount) {
        counts.put(branchId, activeCount);
    }

    public Map<Long, Long> getCounts() {
        return Map.copyOf(counts);
    }
}
