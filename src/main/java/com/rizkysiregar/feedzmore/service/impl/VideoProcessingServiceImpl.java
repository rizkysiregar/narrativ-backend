package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.service.VideoProcessingService;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VideoProcessingServiceImpl implements VideoProcessingService {

    @Override
    public Path extractThumbnail(Path videoPath, Path outputPath) {
        try {
            Files.deleteIfExists(outputPath);
            Process process = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-ss", "00:00:01",
                    "-i", videoPath.toAbsolutePath().toString(),
                    "-frames:v", "1",
                    "-update", "1",
                    "-q:v", "2",
                    outputPath.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            process.getInputStream().transferTo(System.out);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0 || !Files.exists(outputPath)) {
                throw new RuntimeException("ffmpeg exited with code " + (finished ? process.exitValue() : "timeout"));
            }
            return outputPath;
        } catch (Exception e) {
            log.error(":: THUMBNAIL EXTRACTION FAILED for {}: {}", videoPath, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
