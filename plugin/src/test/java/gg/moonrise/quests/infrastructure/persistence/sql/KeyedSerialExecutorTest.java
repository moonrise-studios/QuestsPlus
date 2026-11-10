package gg.moonrise.quests.infrastructure.persistence.sql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyedSerialExecutorTest {

    private static final int THREADS = 4;

    private final KeyedSerialExecutor executor = new KeyedSerialExecutor("QuestsPlus-SQL-Test", THREADS);

    @AfterEach
    public void tearDown() throws InterruptedException {
        executor.close(5, TimeUnit.SECONDS);
    }

    @Test
    public void sameKeyTasksRunInSubmissionOrderWithoutOverlapWhileKeysRunInParallel() {
        List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Map<UUID, List<Integer>> executed = new ConcurrentHashMap<>();
        Map<UUID, AtomicInteger> runningPerKey = new ConcurrentHashMap<>();
        AtomicInteger overlapping = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int sequence = 0; sequence < 60; sequence++) {
            for (UUID player : players) {
                int order = sequence;
                futures.add(executor.submit(player, () -> {
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    if (runningPerKey.computeIfAbsent(player, ignored -> new AtomicInteger()).incrementAndGet() > 1) {
                        overlapping.incrementAndGet();
                    }
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(2));
                        executed.computeIfAbsent(player, ignored -> new CopyOnWriteArrayList<>()).add(order);
                    } finally {
                        runningPerKey.get(player).decrementAndGet();
                        running.decrementAndGet();
                    }
                    return null;
                }));
            }
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).orTimeout(30, TimeUnit.SECONDS).join();

        List<Integer> expected = new ArrayList<>();
        for (int sequence = 0; sequence < 60; sequence++) {
            expected.add(sequence);
        }
        for (UUID player : players) {
            assertEquals(expected, executed.get(player), "tasks of one key must run in submission order");
        }
        assertEquals(0, overlapping.get(), "tasks of one key must never overlap");
        assertTrue(maxRunning.get() > 1, "tasks of different keys should use more than one worker");
        assertTrue(maxRunning.get() <= THREADS);
    }

    @Test
    public void differentKeysDoNotWaitForEachOther() {
        CountDownLatch otherKeyStarted = new CountDownLatch(1);
        CompletableFuture<Boolean> waiting = executor.submit("a", () -> otherKeyStarted.await(5, TimeUnit.SECONDS));
        CompletableFuture<Void> other = executor.submit("b", () -> {
            otherKeyStarted.countDown();
            return null;
        });

        assertTrue(waiting.orTimeout(10, TimeUnit.SECONDS).join());
        other.orTimeout(10, TimeUnit.SECONDS).join();
    }

    @Test
    public void failedTaskDoesNotBlockLaterTasksOfTheSameKey() {
        CompletableFuture<Object> failed = executor.submit("a", () -> {
            throw new IllegalStateException("boom");
        });
        CompletableFuture<String> next = executor.submit("a", () -> "ok");

        assertEquals("ok", next.orTimeout(10, TimeUnit.SECONDS).join());
        assertTrue(failed.isCompletedExceptionally());
    }

    @Test
    public void multiKeyTaskWaitsForEveryKeyAndOrdersLaterTasksOfEachKey() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> blockingA = executor.submit("a", () -> {
            release.await(5, TimeUnit.SECONDS);
            order.add("a");
            return null;
        });
        CompletableFuture<Void> both = executor.submit(Set.of("a", "b"), () -> {
            order.add("a+b");
            return null;
        });
        CompletableFuture<Void> laterB = executor.submit("b", () -> {
            order.add("b");
            return null;
        });

        Thread.sleep(100);
        assertFalse(both.isDone(), "a multi-key task must wait for earlier work of each key");
        assertFalse(laterB.isDone(), "later work of a key must wait for the multi-key task");
        release.countDown();

        CompletableFuture.allOf(blockingA, both, laterB).orTimeout(10, TimeUnit.SECONDS).join();
        assertEquals(List.of("a", "a+b", "b"), order);
    }

    @Test
    public void barrierRunsAfterAcceptedWorkAndBeforeLaterWork() throws InterruptedException {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch barrierEntered = new CountDownLatch(1);
        List<CompletableFuture<Void>> before = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            String key = "key-" + index;
            before.add(executor.submit(key, () -> {
                Thread.sleep(50);
                order.add("before");
                return null;
            }));
        }
        CompletableFuture<Void> barrier = CompletableFuture.runAsync(() -> executor.barrier(() -> {
            barrierEntered.countDown();
            sleepQuietly(100);
            order.add("barrier");
        }));
        assertTrue(barrierEntered.await(10, TimeUnit.SECONDS));
        CompletableFuture<Void> after = executor.submit("key-0", () -> {
            order.add("after");
            return null;
        });

        CompletableFuture.allOf(barrier, after).orTimeout(10, TimeUnit.SECONDS).join();

        before.forEach(future -> assertTrue(future.isDone()));
        assertEquals(10, order.size());
        assertEquals("barrier", order.get(8));
        assertEquals("after", order.get(9));
    }

    @Test
    public void resizeChangesTheNumberOfWorkers() {
        executor.barrier(() -> executor.resize(1));
        assertEquals(1, executor.threads());

        executor.barrier(() -> executor.resize(6));
        assertEquals(6, executor.threads());
    }

    @Test
    public void closeDrainsAcceptedWorkAndRejectsNewWork() throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        List<CompletableFuture<Void>> accepted = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            accepted.add(executor.submit("a", () -> {
                Thread.sleep(5);
                completed.incrementAndGet();
                return null;
            }));
        }

        assertTrue(executor.close(10, TimeUnit.SECONDS));

        accepted.forEach(future -> assertFalse(future.isCompletedExceptionally()));
        assertEquals(20, completed.get());
        assertTrue(executor.isClosed());
        assertTrue(executor.submit("a", () -> null).isCompletedExceptionally());
    }

    @Test
    public void closeTimeoutFailsTasksThatNeverStarted() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> blocking = executor.submit("a", () -> {
            release.await();
            return null;
        });
        CompletableFuture<Void> queued = executor.submit("a", () -> null);

        assertFalse(executor.close(100, TimeUnit.MILLISECONDS));

        assertTrue(queued.orTimeout(5, TimeUnit.SECONDS).handle((ignored, error) -> error != null).join());
        assertTrue(blocking.orTimeout(5, TimeUnit.SECONDS).handle((ignored, error) -> error != null).join());
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
