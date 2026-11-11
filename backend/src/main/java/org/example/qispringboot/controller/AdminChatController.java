package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.qispringboot.DTO.response.AdminChatDetailResponseDTO;
import org.example.qispringboot.DTO.response.AdminChatSessionResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.service.AdminChatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/psychological-chat/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {
    private final AdminChatService adminChatService;

    public AdminChatController(AdminChatService adminChatService) {
        this.adminChatService = adminChatService;
    }

    @GetMapping("/sessions")
    public Result<Page<AdminChatSessionResponseDTO>> sessions(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return Result.OK(adminChatService.page(page, size));
    }

    @GetMapping("/sessions/{id}")
    public Result<AdminChatDetailResponseDTO> detail(@PathVariable Long id) {
        return Result.OK(adminChatService.detail(id));
    }
}
