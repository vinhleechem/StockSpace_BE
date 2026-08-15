package fu.stockspace.stockspace_be.chatbot.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;




@Component
public class ChatStreamRuntime {

    @Value("${app.chatbot.sse.timeout:90s}")
    private Duration timeout;

    @Value("${app.chatbot.sse.heartbeat:15s}")
    private Duration heartbeat;

    @Value("${app.chatbot.sse.executor.core-pool-size:12}")
    private int corePoolSize;

    @Value("${app.chatbot.sse.executor.max-pool-size:12}")
    private int maxPoolSize;

    @Value("${app.chatbot.sse.executor.queue-capacity:24}")
    private int queueCapacity;

    private ThreadPoolExecutor workerExecutor;
    private ScheduledThreadPoolExecutor heartbeatScheduler;

    @PostConstruct
    void initialize() {
        int boundedCore = Math.max(1, corePoolSize);
        int boundedMax = Math.max(boundedCore, maxPoolSize);
        int boundedQueue = Math.max(0, queueCapacity);
        BlockingQueue<Runnable> queue = boundedQueue == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(boundedQueue);
        RejectedExecutionHandler reject = new ThreadPoolExecutor.AbortPolicy();

        workerExecutor = new ThreadPoolExecutor(
                boundedCore,
                boundedMax,
                60L,
                TimeUnit.SECONDS,
                queue,
                namedThreads("chat-stream-worker-"),
                reject
        );
        workerExecutor.allowCoreThreadTimeOut(true);

        heartbeatScheduler = new ScheduledThreadPoolExecutor(
                1,
                namedThreads("chat-stream-heartbeat-"),
                reject
        );
        heartbeatScheduler.setRemoveOnCancelPolicy(true);
        heartbeatScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        heartbeatScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    public Future<?> submit(Runnable task) {
        return workerExecutor.submit(task);
    }

    public ScheduledFuture<?> scheduleHeartbeat(Runnable task) {
        long intervalMillis = effectiveHeartbeat().toMillis();
        return heartbeatScheduler.scheduleAtFixedRate(
                task,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public Duration effectiveTimeout() {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofSeconds(90);
        }
        return timeout;
    }

    Duration effectiveHeartbeat() {
        if (heartbeat == null || heartbeat.isZero() || heartbeat.isNegative()) {
            return Duration.ofSeconds(15);
        }
        return heartbeat.compareTo(Duration.ofSeconds(5)) < 0
                ? Duration.ofSeconds(5)
                : heartbeat;
    }

    @PreDestroy
    void shutdown() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }
        if (workerExecutor != null) {
            workerExecutor.shutdownNow();
        }
    }

    private ThreadFactory namedThreads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
