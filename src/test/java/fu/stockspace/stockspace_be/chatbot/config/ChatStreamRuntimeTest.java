package fu.stockspace.stockspace_be.chatbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStreamRuntimeTest {

    @Test
    void rejectsWorkWhenWorkerAndBoundedQueueAreFull() throws Exception {
        ChatStreamRuntime runtime = new ChatStreamRuntime();
        ReflectionTestUtils.setField(runtime, "timeout", Duration.ofSeconds(90));
        ReflectionTestUtils.setField(runtime, "heartbeat", Duration.ofSeconds(15));
        ReflectionTestUtils.setField(runtime, "corePoolSize", 1);
        ReflectionTestUtils.setField(runtime, "maxPoolSize", 1);
        ReflectionTestUtils.setField(runtime, "queueCapacity", 1);
        runtime.initialize();

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            runtime.submit(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            runtime.submit(() -> {

            });

            assertThrows(
                    RejectedExecutionException.class,
                    () -> runtime.submit(() -> {
                    })
            );
        } finally {
            releaseWorker.countDown();
            runtime.shutdown();
        }
    }
}
