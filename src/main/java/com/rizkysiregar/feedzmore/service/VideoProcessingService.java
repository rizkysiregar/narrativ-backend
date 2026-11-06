package com.rizkysiregar.feedzmore.service;

import java.nio.file.Path;

public interface VideoProcessingService {
    Path extractThumbnail(Path videoPath, Path outputPath);
}
