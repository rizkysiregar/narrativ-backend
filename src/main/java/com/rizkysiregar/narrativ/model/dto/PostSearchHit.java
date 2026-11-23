package com.rizkysiregar.narrativ.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** A post a search found: its id, and with its created_at the cursor of the page it ends. */
public interface PostSearchHit {
    UUID getId();

    LocalDateTime getCreatedAt();
}
