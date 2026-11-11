package com.rizkysiregar.feedzmore.model.response;

import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommentResponseTest {

    private final UUID authorId = UUID.randomUUID();
    private final Instant takenDownAt = Instant.parse("2026-11-08T01:00:00Z");

    private CommentResponse takenDown() {
        return new CommentResponse(UUID.randomUUID(), authorId, "author", null, "rude text",
                ModerationStatus.TAKEN_DOWN, "kata kasar", takenDownAt, null, null, null, null, 0L);
    }

    @Test
    void takenDownComment_otherViewersGetNeitherTextNorReason() {
        CommentResponse result = takenDown().forViewer(UUID.randomUUID());

        assertEquals(ModerationStatus.TAKEN_DOWN, result.getModerationStatus());
        assertEquals(takenDownAt, result.getTakenDownAt());
        assertNull(result.getCommentContent());
        assertNull(result.getTakedownReason());
    }

    @Test
    void takenDownComment_authorKeepsTheTextAndSeesTheReason() {
        CommentResponse result = takenDown().forViewer(authorId);

        assertEquals("rude text", result.getCommentContent());
        assertEquals("kata kasar", result.getTakedownReason());
    }

    @Test
    void takenDownComment_withoutAViewerIsTreatedAsSomeoneElse() {
        assertNull(takenDown().forViewer(null).getCommentContent());
    }

    @Test
    void activeComment_isUntouchedAndCarriesNoTakedownFields() {
        CommentResponse active = new CommentResponse(UUID.randomUUID(), authorId, "author", null, "nice",
                ModerationStatus.ACTIVE, null, null, null, null, null, null, 0L);

        CommentResponse result = active.forViewer(UUID.randomUUID());

        assertEquals("nice", result.getCommentContent());
        assertNull(result.getTakedownReason());
        assertNull(result.getTakenDownAt());
    }
}
