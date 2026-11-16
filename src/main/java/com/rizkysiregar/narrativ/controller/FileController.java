package com.rizkysiregar.narrativ.controller;


import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.response.FileUploadResponse;
import com.rizkysiregar.narrativ.model.response.FileUrlResponse;
import com.rizkysiregar.narrativ.service.FileAccessPolicy;
import com.rizkysiregar.narrativ.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final FileAccessPolicy fileAccessPolicy;

    public FileController(FileStorageService fileStorageService, FileAccessPolicy fileAccessPolicy) {
        this.fileStorageService = fileStorageService;
        this.fileAccessPolicy = fileAccessPolicy;
    }

    @Operation(summary = "Upload a profile picture to Object Storage (featureName must be PROFILE_PICTURE, userId your own username or id). Post media goes through POST /posts/upload-url.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("userId") String userId,
            @RequestPart("featureName") String featureName,
            @AuthenticationPrincipal UserPrincipal caller
    ) throws Exception {
        fileAccessPolicy.checkUpload(caller, userId, featureName);

        String objectPath = fileStorageService.uploadFile(file, userId, featureName);

        return FileUploadResponse.builder()
                .fileName(file.getOriginalFilename())
                .objectPath(objectPath)
                .build();

    }


    @Operation(summary = "Getting presign url. Profile pictures are open to every user, post media only to its owner.")
    @GetMapping(value = "presign-url")
    public FileUrlResponse getFileUrl(@RequestParam("path") String path,
                                      @AuthenticationPrincipal UserPrincipal caller) throws Exception {
        fileAccessPolicy.checkRead(caller, path);
        String url = fileStorageService.getPresignUrl(path);
        return new FileUrlResponse(path, url);
    }

}
