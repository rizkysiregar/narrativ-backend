package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.entity.UserNotificationEntity;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.model.enums.NotificationType;
import com.rizkysiregar.narrativ.model.response.NotificationResponse;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.repository.UserNotificationRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.util.InboxCursor;
import com.rizkysiregar.narrativ.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The in-app inbox: what happened to a user. The consumers that push a like, comment, reply, follow or pin write it
 * here first, so the inbox holds everything even when a push never arrived. Undoing the action (unlike, unfollow,
 * deleting the comment) takes the notification away again.
 *
 * <p>Writes are idempotent: every row has a dedup key, so a redelivered event adds nothing. Likes of one post on one
 * day share a group key and read as one entry.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationInboxService {

    static final int PREVIEW_LENGTH = 200;

    private final UserNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PostMediaRepository postMediaRepository;
    private final FileStorageService fileStorageService;
    private final ProfileImageUrlResolver profileImageUrlResolver;
    private final Clock clock;

    public void recordLike(UUID recipientId, UUID actorId, UUID postId) {
        String key = likeKey(postId, actorId);
        record(recipientId, NotificationType.POST_LIKED, actorId, postId, null, null, key,
                "like:" + postId + ":" + LocalDate.now(clock));
    }

    /** A comment on the recipient's post ([NotificationType.POST_COMMENTED]) or a reply to their comment. */
    public void recordComment(UUID recipientId, NotificationType type, UUID actorId, UUID postId, UUID commentId,
                              String preview) {
        String key = "comment:" + commentId + ":" + recipientId;
        record(recipientId, type, actorId, postId, commentId, preview, key, key);
    }

    public void recordFollow(UUID recipientId, UUID actorId) {
        String key = followKey(recipientId, actorId);
        record(recipientId, NotificationType.FOLLOWED, actorId, null, null, null, key, key);
    }

    /** The author of a post pinned the recipient's comment. Pinning it again after an unpin adds nothing. */
    public void recordPin(UUID recipientId, UUID actorId, UUID postId, UUID commentId, String preview) {
        String key = "pin:" + commentId;
        record(recipientId, NotificationType.COMMENT_PINNED, actorId, postId, commentId, preview, key, key);
    }

    @Transactional
    public void removeLike(UUID postId, UUID actorId) {
        notificationRepository.deleteByDedupKey(likeKey(postId, actorId));
    }

    @Transactional
    public void removeFollow(UUID recipientId, UUID actorId) {
        notificationRepository.deleteByDedupKey(followKey(recipientId, actorId));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<NotificationResponse> list(UUID recipientId, String cursor, int size) {
        Paging.of(0, size);
        InboxCursor after = InboxCursor.decode(cursor);
        List<UserNotificationRepository.GroupRow> groups = new ArrayList<>(notificationRepository
                .findGroups(recipientId, after.beforeMicros(), after.beforeKey(), size + 1));
        boolean hasMore = groups.size() > size;
        if (hasMore) {
            groups = groups.subList(0, size);
        }

        List<UUID> rowIds = groups.stream()
                .flatMap(group -> java.util.stream.Stream.of(group.getLatestId(), group.getSecondId()))
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, UserNotificationEntity> rows = rowIds.isEmpty() ? Map.of() : notificationRepository
                .findWithActorByIdIn(rowIds).stream()
                .collect(Collectors.toMap(UserNotificationEntity::getId, Function.identity()));
        Map<UUID, String> thumbnails = thumbnailsOf(rows.values().stream()
                .map(UserNotificationEntity::getPostId).filter(Objects::nonNull).distinct().toList());
        // one presign per person, an entry list often holds the same people several times
        Map<UUID, String> avatars = new HashMap<>();

        List<NotificationResponse> content = new ArrayList<>(groups.size());
        for (UserNotificationRepository.GroupRow group : groups) {
            UserNotificationEntity latest = rows.get(group.getLatestId());
            if (latest == null) {
                continue; // removed between the two queries
            }
            List<NotificationResponse.Actor> actors = new ArrayList<>(2);
            actors.add(actorOf(latest, avatars));
            UserNotificationEntity second = group.getSecondId() == null ? null : rows.get(group.getSecondId());
            if (second != null) {
                actors.add(actorOf(second, avatars));
            }
            content.add(new NotificationResponse(
                    group.getGroupKey(),
                    latest.getType(),
                    actors,
                    group.getActorCount(),
                    latest.getPostId(),
                    latest.getPostId() == null ? null : thumbnails.get(latest.getPostId()),
                    latest.getCommentId(),
                    latest.getPreview(),
                    latest.getCreatedAt(),
                    Boolean.TRUE.equals(group.getUnread())
            ));
        }

        String nextCursor = null;
        if (hasMore) {
            UserNotificationRepository.GroupRow last = groups.getLast();
            nextCursor = new InboxCursor(last.getLastAtMicros(), last.getGroupKey()).encode();
        }
        return new PaginatedResponse<>(content, -1, size, -1, -1, nextCursor == null, nextCursor);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notificationRepository.countUnreadGroups(recipientId);
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        notificationRepository.markAllRead(recipientId, clock.instant());
    }

    @Transactional
    public void markRead(UUID recipientId, String groupKey) {
        notificationRepository.markGroupRead(recipientId, groupKey, clock.instant());
    }

    /** Deletes notifications older than [before]; returns how many. */
    @Transactional
    public int deleteOlderThan(Instant before) {
        return notificationRepository.deleteOlderThan(before);
    }

    private void record(UUID recipientId, NotificationType type, UUID actorId, UUID postId, UUID commentId,
                        String preview, String dedupKey, String groupKey) {
        // nobody is told about what they did themselves
        if (recipientId == null || actorId == null || recipientId.equals(actorId)) {
            return;
        }
        if (notificationRepository.existsByDedupKey(dedupKey)) {
            return;
        }
        UserNotificationEntity row = new UserNotificationEntity();
        row.setId(UUID.randomUUID());
        row.setRecipientId(recipientId);
        row.setType(type);
        row.setActor(userRepository.getReferenceById(actorId));
        row.setPostId(postId);
        row.setCommentId(commentId);
        row.setPreview(shorten(preview));
        row.setDedupKey(dedupKey);
        row.setGroupKey(groupKey);
        row.setCreatedAt(clock.instant());
        try {
            notificationRepository.saveAndFlush(row);
            log.debug("INBOX.RECORDED type={} recipientId={} dedupKey={}", type, recipientId, dedupKey);
        } catch (DataIntegrityViolationException e) {
            // written meanwhile by a redelivery, or the comment is already gone: either way there is nothing to add
            log.debug("INBOX.SKIPPED type={} dedupKey={} reason={}", type, dedupKey, e.getMostSpecificCause().getMessage());
        }
    }

    private NotificationResponse.Actor actorOf(UserNotificationEntity row, Map<UUID, String> avatars) {
        var actor = row.getActor();
        String avatar = avatars.computeIfAbsent(actor.getId(), id -> profileImageUrlResolver.resolve(actor.getProfileImage()));
        return new NotificationResponse.Actor(actor.getId(), actor.getUsername(), avatar);
    }

    /** The first photo of each post, or the poster of its first video; a failed presign leaves the post out. */
    private Map<UUID, String> thumbnailsOf(List<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PostMediaEntity> first = new LinkedHashMap<>();
        for (PostMediaEntity media : postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(postIds)) {
            first.putIfAbsent(media.getPostId(), media);
        }
        Map<UUID, String> urls = new HashMap<>();
        first.forEach((postId, media) -> {
            String key = media.getMediaType() == MediaType.VIDEO ? media.getThumbnailKey() : media.getMediaKey();
            if (key == null) {
                return;
            }
            try {
                urls.put(postId, fileStorageService.getPresignUrl(key));
            } catch (Exception e) {
                log.error("INBOX.THUMBNAIL.PRESIGN.FAILED postId={} reason={}", postId, e.getMessage());
            }
        });
        return urls;
    }

    private static String likeKey(UUID postId, UUID actorId) {
        return "like:" + postId + ":" + actorId;
    }

    private static String followKey(UUID recipientId, UUID actorId) {
        return "follow:" + actorId + ":" + recipientId;
    }

    private static String shorten(String preview) {
        if (preview == null || preview.length() <= PREVIEW_LENGTH) {
            return preview;
        }
        return preview.substring(0, PREVIEW_LENGTH - 3) + "...";
    }
}
