package gg.moonrise.quests.core.sql;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs tasks on a bounded worker pool while keeping tasks that share a key in submission order:
 * a task starts only after every earlier task with one of its keys has finished. Tasks with
 * different keys run in parallel, up to the pool size.
 *
 * <p>{@link #barrier(Runnable)} runs an action once all accepted work has finished and before any
 * later work starts, and {@link #close(long, TimeUnit)} drains accepted work before stopping.
 */
public final class KeyedSerialExecutor {

    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private final ThreadPoolExecutor workers;
    /**
     * The last accepted task of each key; entries are removed once that task completes.
     */
    private final Map<Object, CompletableFuture<?>> lanes = new HashMap<>();
    private CompletableFuture<Void> gate = DONE;
    private boolean closed;

    public KeyedSerialExecutor(@NotNull String threadName, int threads) {
        int size = Math.max(1, threads);
        this.workers = new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), threadFactory(threadName));
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public int threads() {
        return workers.getMaximumPoolSize();
    }

    public <T> CompletableFuture<T> submit(@NotNull Object key, @NotNull Callable<T> task) {
        return submit(List.of(key), task);
    }

    /**
     * Submits a task ordered after every earlier task of each of {@code keys}.
     */
    public <T> CompletableFuture<T> submit(@NotNull Collection<?> keys, @NotNull Callable<T> task) {
        Set<Object> taskKeys = new LinkedHashSet<>(keys);
        if (taskKeys.isEmpty()) {
            throw new IllegalArgumentException("A keyed task needs at least one key");
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        List<CompletableFuture<?>> predecessors = new ArrayList<>();
        synchronized (this) {
            if (closed) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("SQL executor is closed"));
            }
            predecessors.add(gate);
            for (Object key : taskKeys) {
                CompletableFuture<?> previous = lanes.put(key, result);
                if (previous != null) {
                    predecessors.add(previous);
                }
            }
        }
        result.whenComplete((ignored, error) -> release(taskKeys, result));
        // allOf completes once every predecessor completes, whether it succeeded or failed.
        CompletableFuture.allOf(predecessors.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> dispatch(new Job<>(task, result)));
        return result;
    }

    /**
     * Waits for every task accepted so far, runs {@code action} while no task runs, then lets tasks
     * accepted in the meantime start.
     */
    public void barrier(@NotNull Runnable action) {
        CompletableFuture<Void> released = new CompletableFuture<>();
        List<CompletableFuture<?>> accepted;
        synchronized (this) {
            accepted = new ArrayList<>(lanes.values());
            accepted.add(gate);
            gate = released;
        }
        try {
            awaitQuietly(accepted);
            action.run();
        } finally {
            released.complete(null);
        }
    }

    /**
     * Changes the number of workers; call it from a {@link #barrier(Runnable)} action.
     */
    public void resize(int threads) {
        int size = Math.max(1, threads);
        if (size > workers.getMaximumPoolSize()) {
            workers.setMaximumPoolSize(size);
            workers.setCorePoolSize(size);
        } else {
            workers.setCorePoolSize(size);
            workers.setMaximumPoolSize(size);
        }
    }

    /**
     * Rejects new tasks, waits up to {@code timeout} for accepted tasks and stops the workers. Tasks
     * still waiting when the timeout passes complete exceptionally.
     *
     * @return whether every accepted task finished in time
     */
    public boolean close(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        List<CompletableFuture<?>> accepted;
        synchronized (this) {
            closed = true;
            accepted = new ArrayList<>(lanes.values());
            accepted.add(gate);
        }
        boolean drained;
        try {
            CompletableFuture.allOf(accepted.toArray(CompletableFuture[]::new)).get(timeout, unit);
            drained = true;
        } catch (ExecutionException exception) {
            drained = true;
        } catch (TimeoutException exception) {
            drained = false;
        } catch (InterruptedException exception) {
            abortQueued();
            throw exception;
        }
        if (drained) {
            workers.shutdown();
            return true;
        }
        abortQueued();
        return false;
    }

    private void abortQueued() {
        for (Runnable queued : workers.shutdownNow()) {
            if (queued instanceof Job<?> job) {
                job.abort();
            }
        }
    }

    private void dispatch(Job<?> job) {
        try {
            workers.execute(job);
        } catch (RejectedExecutionException exception) {
            job.abort();
        }
    }

    private synchronized void release(Set<Object> keys, CompletableFuture<?> task) {
        for (Object key : keys) {
            lanes.remove(key, task);
        }
    }

    private static void awaitQuietly(List<CompletableFuture<?>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            // Failed tasks still count as finished.
        }
    }

    private static ThreadFactory threadFactory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record Job<T>(Callable<T> task, CompletableFuture<T> result) implements Runnable {

        @Override
        public void run() {
            try {
                result.complete(task.call());
            } catch (Throwable throwable) {
                result.completeExceptionally(new CompletionException(throwable));
            }
        }

        private void abort() {
            result.completeExceptionally(new CompletionException(new RejectedExecutionException("SQL executor is closed")));
        }
    }
}
