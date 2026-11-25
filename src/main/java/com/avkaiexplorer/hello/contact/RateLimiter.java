package com.avkaiexplorer.hello.contact;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** At most `limit` accepted calls per key in any rolling 60 seconds, kept in memory (one instance per container). */
class RateLimiter {

	private static final long WINDOW_MS = 60_000;
	private static final int MAX_KEYS = 10_000;

	private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
	private final Clock clock;
	private final int limit;

	RateLimiter(Clock clock, int limit) {
		this.clock = clock;
		this.limit = limit;
	}

	boolean allow(String key) {
		long now = clock.millis();
		if (hits.size() > MAX_KEYS) {
			hits.values().removeIf(q -> q.isEmpty() || now - q.peekLast() > WINDOW_MS);
		}
		Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (queue) {
			while (!queue.isEmpty() && now - queue.peekFirst() > WINDOW_MS) {
				queue.pollFirst();
			}
			if (queue.size() >= limit) {
				return false;
			}
			queue.addLast(now);
			return true;
		}
	}

}
