package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.FileUploadResponse;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    public FileUploadResponse uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId
    ) {
        try {
            String objectPath = fileStorageService.uploadFile(file, userId);

            return FileUploadResponse.builder()
                    .fileName(file.getOriginalFilename())
                    .objectPath(objectPath)
                    .build();
        } catch (Exception e) {
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
