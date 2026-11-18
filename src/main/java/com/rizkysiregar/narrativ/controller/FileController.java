package com.rizkysiregar.narrativ.controller;


import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.response.FileUrlResponse;
import com.rizkysiregar.narrativ.service.FileAccessPolicy;
import com.rizkysiregar.narrativ.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Getting presign url. Profile pictures are open to every user, post media only to its owner.")
    @GetMapping(value = "presign-url")
    public FileUrlResponse getFileUrl(@RequestParam("path") String path,
                                      @AuthenticationPrincipal UserPrincipal caller) throws Exception {
        fileAccessPolicy.checkRead(caller, path);
        String url = fileStorageService.getPresignUrl(path);
        return new FileUrlResponse(path, url);
    }

}
