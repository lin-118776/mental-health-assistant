package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.qispringboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.qispringboot.DTO.response.EmotionDiaryResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.EmotionDiaryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    @Resource
    private EmotionDiaryService emotionDiaryService;

    @PostMapping
    public Result<Void> add(@Valid @RequestBody EmotionDiaryCreateDTO dto) {
        emotionDiaryService.addDiary(getCurrentLoginUser().getUserId(), dto);
        return Result.OK();
    }

    @GetMapping("/admin/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<EmotionDiaryResponseDTO>> adminPage(
            @RequestParam(value = "page", required = false) Long page,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "currentPage", required = false) Long currentPage,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "moodScreRange", required = false) String moodScreRange) {
        long current = currentPage != null ? currentPage : (page != null ? page : 1);
        long pageSizeValue = pageSize != null ? pageSize : (size != null ? size : 10);
        if (current < 1) {
            current = 1;
        }
        if (pageSizeValue < 1) {
            pageSizeValue = 10;
        }
        return Result.OK(emotionDiaryService.adminPage(current, pageSizeValue, userId, moodScreRange));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        emotionDiaryService.deleteDiary(id);
        return Result.OK();
    }

    private LoginUser getCurrentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }
}