package org.example.qispringboot.controller;

import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.response.FileInfoDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.FileService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<FileInfoDTO> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "businessType", required = false) String businessType,
                                      @RequestParam(value = "businessId", required = false) String businessId,
                                      @RequestParam(value = "businessField", required = false) String businessField) {
        return Result.OK(fileService.upload(file, businessType, businessId, businessField,
                getCurrentLoginUser().getUserId()));
    }

    private LoginUser getCurrentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }
}
