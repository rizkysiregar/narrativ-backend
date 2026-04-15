package com.rizkysiregar.feedzmore.controller;


import com.rizkysiregar.feedzmore.model.response.FileUploadResponse;
import com.rizkysiregar.feedzmore.model.response.FileUrlResponse;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */


@RestController
@RequestMapping("/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "Upload file to Object Storage")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("userId") String userId,
            @RequestPart("featureName") String featureName
    ) throws Exception {

        String objectPath = fileStorageService.uploadFile(file, userId, featureName);

        return FileUploadResponse.builder()
                .fileName(file.getOriginalFilename())
                .objectPath(objectPath)
                .build();

    }


    @Operation(summary = "Getting presign url")
    @GetMapping(value = "presign-url")
    public FileUrlResponse getFileUrl(@RequestParam("path") String path) throws Exception {
        String url = fileStorageService.getPresignUrl(path);
        return new FileUrlResponse(path, url);
    }

}
