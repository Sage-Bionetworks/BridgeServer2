package org.sagebionetworks.bridge.util;

import java.time.Instant;

/**
 * Rate limiter which limits a resource by bytes. The number of bytes that can
 * be consumed is tracked and is periodically "refilled."
 */
public class ByteRateLimiter {
    // The maximum number of bytes that can be accumulated.
    private long maximumBytes;
    // The time between byte refills in seconds.
    private long refillIntervalSeconds;
    // The number of bytes that is refilled every refillIntervalSeconds.
    private long refillAmount;

    // The last time a refill occurred.
    private Instant lastRefill;
    // The current number of allowed bytes to be consumed.
    private long currentBytes;

    /**
     * Class constructor specifying initialBytes, maximumBytes,
     * refillIntervalSeconds, and refillAmount.
     * 
     * @param initialBytes          The initial number of bytes allowed to be
     *                              consumed.
     * @param maximumBytes          The maximum number of bytes allowed to be
     *                              consumed.
     * @param refillIntervalSeconds The time between byte refills in seconds.
     * @param refillAmount          The number of bytes that is refilled every
     *                              refillIntervalSeconds.
     */
    public ByteRateLimiter(long initialBytes, long maximumBytes, long refillIntervalSeconds, long refillAmount) {
        this.maximumBytes = maximumBytes;
        this.refillIntervalSeconds = refillIntervalSeconds;
        this.refillAmount = refillAmount;

        this.lastRefill = Instant.now();
        this.currentBytes = initialBytes;
    }

    /**
     * Updates the number of consumable bytes based upon the refill amount and the
     * number of refill intervals that have occurred since the last refill.
     */
    private void updateCurrentBytes() {
        long secondsSinceLastRefill = Instant.now().getEpochSecond() - lastRefill.getEpochSecond();
        long refillsCount = secondsSinceLastRefill / refillIntervalSeconds;
        long bytesToRefill = (long) refillsCount * refillAmount;

        currentBytes = Math.min(maximumBytes, currentBytes + bytesToRefill);
        // It's not just Instant.now() because we want to save the time between the last
        // refill and now.
        lastRefill = lastRefill.plusSeconds(refillsCount * refillIntervalSeconds);
    }

    /**
     * Checks whether the set rate limit will allow the specified number of bytes to
     * be consumed on the resource.
     * 
     * @param bytesToConsume The number of bytes to be consumed on the resource.
     * @return A boolean determining whether the specified number of bytes can be
     *         consumed on the resource (true if it can, false if it cannot).
     */
    public boolean tryConsumeBytes(long bytesToConsume) {
        updateCurrentBytes();

        if (currentBytes >= bytesToConsume) {
            currentBytes -= bytesToConsume;
            return true;
        }
        return false;
    }
}
