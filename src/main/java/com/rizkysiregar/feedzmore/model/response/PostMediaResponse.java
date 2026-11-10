package com.rizkysiregar.feedzmore.model.response;

import com.rizkysiregar.feedzmore.model.enums.MediaType;

import java.util.UUID;

public record PostMediaResponse(UUID id, MediaType type, String url, String thumbnailUrl) {}
