package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Who may touch which object through the generic {@code /files} endpoints. Object keys look like
 * {@code owner/GROUP/file}. Post media is only reachable through the feed (which presigns what the viewer may see,
 * and hides taken down posts), so here it is limited to its owner; profile pictures are shown to everybody.
 */
@Component
public class FileAccessPolicy {

    public void checkRead(UserPrincipal caller, String path) {
        String[] segments = segmentsOf(path);
        if (isAdmin(caller)) {
            return;
        }
        String group = segments[1];
        if (ObjectStorageTypeGroup.PROFILE_PICTURE.equals(group)) {
            return;
        }
        if (ObjectStorageTypeGroup.POST_FEED.equals(group) && segments[0].equals(caller.getUsername())) {
            return;
        }
        throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    /** Only profile pictures go through this endpoint, and only into the caller's own folder. */
    public void checkUpload(UserPrincipal caller, String userId, String featureName) {
        if (!ObjectStorageTypeGroup.PROFILE_PICTURE.equals(featureName) || isUnsafe(userId)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        if (!userId.equals(caller.getUsername()) && !userId.equals(caller.getUserId().toString())) {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
    }

    private String[] segmentsOf(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains("..")) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        String[] segments = path.split("/", -1);
        if (segments.length < 3) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }
        }
        return segments;
    }

    private boolean isUnsafe(String value) {
        return value == null || value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..");
    }

    private boolean isAdmin(UserPrincipal caller) {
        return caller.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
