package com.rizkysiregar.feedzmore.service;

import java.nio.file.Path;

public interface VideoProcessingService {
    double getDurationSeconds(Path videoPath);

    Path extractThumbnail(Path videoPath, Path outputPath);
}
