package com.rizkysiregar.narrativ.model.response;

import com.rizkysiregar.narrativ.model.enums.MediaType;

import java.util.UUID;

public record PostMediaResponse(UUID id, MediaType type, String url, String thumbnailUrl) {}
