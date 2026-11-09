package ru.mephi.booking;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name = "booking.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class SagaRecovery {
    private final BookingTransactions transactions;
    private final BookingWorkflow workflow;
    public SagaRecovery(BookingTransactions transactions, BookingWorkflow workflow) {
        this.transactions = transactions;
        this.workflow = workflow;
    }
    @Scheduled(fixedDelayString = "${booking.recovery.delay-ms:1000}")
    public void recover() {
        transactions.recoverable().forEach(id -> {
            try {
                workflow.process(id);
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(SagaRecovery.class).warn("event=recovery_failed bookingId={}", id, e);
            }
        });
        transactions.compensations().forEach(workflow::compensate);
    }
}
