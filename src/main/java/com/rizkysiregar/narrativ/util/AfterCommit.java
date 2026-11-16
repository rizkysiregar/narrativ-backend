package com.rizkysiregar.narrativ.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects that must only happen once the database change is really committed (push notifications,
 * deleting files). A failure is logged and never turns the already committed change into an error.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(String description, Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execute(description, action);
                }
            });
        } else {
            execute(description, action);
        }
    }

    private static void execute(String description, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("AFTER.COMMIT.FAILED description={} reason={}", description, e.getMessage(), e);
        }
    }
}
