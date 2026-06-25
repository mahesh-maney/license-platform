package com.modus.license.test.kafka;

import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Generic Kafka consumer for capturing events in integration tests.
 *
 * Register as a Spring bean in your test configuration with the topics and
 * group ID you want to consume, then use {@link #awaitMessages} to wait for
 * delivery and {@link #getMessages} to assert on received records.
 *
 * Example test configuration:
 * <pre>
 *   {@literal @}TestConfiguration
 *   static class TestKafkaConfig {
 *       {@literal @}Bean
 *       KafkaTestEventConsumer{@literal <}TenantEvent{@literal >} tenantEventConsumer() {
 *           return new KafkaTestEventConsumer{@literal <>}();
 *       }
 *   }
 * </pre>
 *
 * Then in the listener, add {@literal @}KafkaListener on the bean's
 * {@link #onMessage} method, or subclass and add it there.
 *
 * For simpler usage in a single test class, use {@link #reset()} between tests
 * to clear accumulated messages and reset the latch.
 *
 * @param <T> the Avro SpecificRecord type this consumer captures
 */
public class KafkaTestEventConsumer<T extends SpecificRecord> {

    private static final Logger log = LoggerFactory.getLogger(KafkaTestEventConsumer.class);

    private final List<T>      messages   = Collections.synchronizedList(new ArrayList<>());
    private       CountDownLatch latch    = new CountDownLatch(1);
    private       int            expected = 1;

    /**
     * Call this method from a {@literal @}KafkaListener in your test configuration.
     */
    public void onMessage(T record) {
        log.debug("Test consumer received: {}", record.getClass().getSimpleName());
        messages.add(record);
        if (messages.size() >= expected) {
            latch.countDown();
        }
    }

    /**
     * Waits up to {@code timeoutSeconds} for at least {@code count} messages to arrive.
     *
     * @return {@code true} if the expected messages arrived within the timeout
     */
    public boolean awaitMessages(int count, long timeoutSeconds) throws InterruptedException {
        this.expected = count;
        this.latch    = new CountDownLatch(1);
        return latch.await(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Waits up to 10 seconds for one message. */
    public boolean awaitOneMessage() throws InterruptedException {
        return awaitMessages(1, 10);
    }

    /** Returns a snapshot of all received messages. */
    public List<T> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /** Returns the most recently received message. */
    public T getLastMessage() {
        if (messages.isEmpty()) throw new IllegalStateException("No messages received");
        return messages.get(messages.size() - 1);
    }

    /** Clears all accumulated messages and resets the latch. Call in {@literal @}BeforeEach. */
    public void reset() {
        messages.clear();
        latch    = new CountDownLatch(1);
        expected = 1;
    }

    public int getMessageCount() { return messages.size(); }
    public boolean isEmpty()     { return messages.isEmpty(); }
}
