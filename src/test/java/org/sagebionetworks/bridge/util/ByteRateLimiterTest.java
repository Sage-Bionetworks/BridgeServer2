package org.sagebionetworks.bridge.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

/**
 * Tests the ByteRateLimiter
 */
public class ByteRateLimiterTest {
    /**
     * Checks whether the ByteRateLimiter correctly allows a download after a refill
     * which was not allowed before the refill.
     * 
     * @throws InterruptedException
     */
    @Test
    public void validAfterRefill() throws InterruptedException {
        ByteRateLimiter rateLimiter = new ByteRateLimiter(
                1_000, // 1 KB
                100_000_000, // 10 MB
                2, // 2s
                1_000_000 // 1 MB
        );

        assertFalse(rateLimiter.tryConsumeBytes(10_000),
                "ByteRateLimiter should have rejected 10 KB download with initial of 1 KB");

        Thread.sleep(3000);
        assertTrue(rateLimiter.tryConsumeBytes(10_000),
                "ByteRateLimiter should have allowed 10 KB download with initial of 1 KB after refill of 1 MB");
    }

    /**
     * Checks whether the ByteRateLimiter allows a small file within the limit
     * immediately after rejecting a large file
     */
    @Test
    public void largeFileThenSmallFile() {
        ByteRateLimiter rateLimiter = new ByteRateLimiter(
                1_000, // 1 KB
                100_000_000, // 10 MB
                3600, // 1 hr
                1_000_000 // 1 MB
        );

        assertFalse(rateLimiter.tryConsumeBytes(10_000),
                "ByteRateLimiter should have rejected 10 KB download with initial of 1 KB");
        assertTrue(rateLimiter.tryConsumeBytes(5),
                "ByteRateLimiter should have allowed 5 B download with initial of 1 KB");
    }

    /**
     * Checks whether the ByteRateLimiter rejects a large file immediately after
     * allowing a small file
     */
    @Test
    public void smallFileThenLargeFile() {
        ByteRateLimiter rateLimiter = new ByteRateLimiter(
                1_000, // 1 KB
                100_000_000, // 10 MB
                3600, // 1 hr
                1_000_000 // 1 MB
        );

        assertTrue(rateLimiter.tryConsumeBytes(5),
                "ByteRateLimiter should have allowed 5 B download with initial of 1 KB");
        assertFalse(rateLimiter.tryConsumeBytes(10_000),
                "ByteRateLimiter should have rejected 10 KB download with initial of 1 KB");
    }

    /**
     * Checks whether the ByteRateLimiter allows a large number of small files but still
     * rejects the file that goes over the allowed limit
     */
    @Test
    public void manySmallFiles() {
        ByteRateLimiter rateLimiter = new ByteRateLimiter(
                1_000_000, // 1 MB
                100_000_000, // 10 MB
                3600, // 1 hr
                1_000_000 // 1 MB
        );

        for (int i = 0; i < 100; i++) {
            assertTrue(rateLimiter.tryConsumeBytes(10_000),
                    "ByteRateLimiter should have allowed 100 10 KB downloads with initial of 1 MB");
        }

        assertFalse(rateLimiter.tryConsumeBytes(10_000),
                "ByteRateLimiter should have rejected 101st 10 KB download with initial of 1 MB");
    }
}
