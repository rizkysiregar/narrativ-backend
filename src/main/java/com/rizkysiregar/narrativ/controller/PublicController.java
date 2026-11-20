package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.response.PublicPostResponse;
import com.rizkysiregar.narrativ.model.response.PublicProfileResponse;
import com.rizkysiregar.narrativ.service.PublicContentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Shared links, readable without signing in: the web client renders them on the server for link previews (Open
 * Graph) and for visitors without an account. Rate limited per client IP.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Tag(name = "Public share pages")
public class PublicController {

    // shorter than the hour a presigned URL lives, so a cached redirect never points at an expired one
    private static final CacheControl REDIRECT_CACHE = CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic();

    private final PublicContentService publicContentService;

    @io.swagger.v3.oas.annotations.Operation(summary = "A post by its link, without signing in",
            description = "404 when the post is deleted or taken down, or its author is deleted or banned. Image paths are relative to the API base and redirect to the file")
    @GetMapping("/posts/{postId}")
    public PublicPostResponse post(@PathVariable UUID postId) {
        return publicContentService.getPost(postId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "A profile by its link, without signing in",
            description = "404 when the user is deleted or banned")
    @GetMapping("/users/{username}")
    public PublicProfileResponse profile(@PathVariable String username) {
        return publicContentService.getProfile(username);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Redirect to the image of a public post's media",
            description = "The photo, or the thumbnail of a video. A stable URL for og:image: it redirects (302) to a fresh presigned URL")
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Void> media(@PathVariable UUID mediaId) {
        return redirect(publicContentService.mediaImageUrl(mediaId));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Redirect to a public profile's picture")
    @GetMapping("/users/{username}/avatar")
    public ResponseEntity<Void> avatar(@PathVariable String username) {
        return redirect(publicContentService.avatarUrl(username));
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).cacheControl(REDIRECT_CACHE).build();
    }
}
