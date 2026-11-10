package com.rizkysiregar.feedzmore.model.dto;

import java.time.Instant;

/** An object in storage as listed: its key and when it was last written. */
public record StoredObject(String key, Instant lastModified) {}
