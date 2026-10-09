package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.service.VideoProcessingService;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VideoProcessingServiceImpl implements VideoProcessingService {

    @Override
    public double getDurationSeconds(Path videoPath) {
        try {
            Process process = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    videoPath.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().reduce("", (a, b) -> a + b);
            process.waitFor(30, TimeUnit.SECONDS);

            return Double.parseDouble(output.trim());
        } catch (Exception e) {
            log.error(":: FFPROBE FAILED for {}: {}", videoPath, e.getMessage());
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

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
