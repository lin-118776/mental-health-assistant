package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.example.qispringboot.DTO.command.UserStatusUpdateDTO;
import org.example.qispringboot.DTO.response.AdminUserResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.service.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping("/page")
    public Result<Page<AdminUserResponseDTO>> page(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return Result.OK(service.page(keyword, status, page, size));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateDTO dto) {
        service.updateStatus(id, dto.getStatus());
        return Result.OK();
    }
}
