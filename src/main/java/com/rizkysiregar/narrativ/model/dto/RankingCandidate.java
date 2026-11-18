package com.rizkysiregar.narrativ.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A post the ranked feeds may show, with what its score is made of. Likes and comments count only when they come
 * from somebody else than the author; reports are the open ones.
 */
public record RankingCandidate(UUID postId, UUID authorId, LocalDateTime createdAt,
                               long likeCount, long commentCount, long openReportCount) {
}
