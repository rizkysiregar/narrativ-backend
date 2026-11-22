package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the direct message of two users read-only while a block stands between them (marpokat PLAN NV-06). It runs
 * after the block was committed, and decides from the state of the blocks, not from the event: a block in the other
 * direction keeps the chat frozen when one of the two lifts theirs.
 *
 * <p>Best effort: if Marpokat cannot be reached the block still holds in Narrativ (nobody can open a new DM), only an
 * existing chat stays writable until the next block or unblock between the pair. The failure is logged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatBlockSync {

    /** Published by {@code UserBlockService} when a block between two users was added or removed. */
    public record BlockChanged(UUID userA, UUID userB) {
    }

    private final MarpokatProperties properties;
    private final MarpokatClient marpokat;
    private final UserBlockRepository userBlockRepository;

    @Async("chatSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBlockChanged(BlockChanged event) {
        sync(event.userA(), event.userB());
    }

    void sync(UUID a, UUID b) {
        if (!properties.enabled()) {
            return;
        }
        boolean blocked = userBlockRepository.existsBetween(a, b);
        try {
            Optional<String> dm = marpokat.findDirectMessage(a.toString(), b.toString());
            if (dm.isEmpty()) {
                return; // they never chatted: nothing to freeze, and a later DM is refused by the block check
            }
            marpokat.setFrozen(dm.get(), blocked);
            log.info("CHAT.DM.{} channelId={} userA={} userB={}", blocked ? "FROZEN" : "UNFROZEN", dm.get(), a, b);
        } catch (MarpokatClient.MarpokatException e) {
            log.error("CHAT.DM.SYNC_FAILED userA={} userB={} blocked={} reason={}", a, b, blocked, e.getMessage());
        }
    }
}
