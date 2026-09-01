package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.qispringboot.DTO.command.KnowledgeArticleStatusDTO;
import org.example.qispringboot.DTO.response.KnowledgeArticleResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.entity.KnowledgeCategory;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.KnowledgeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeService knowledgeService;

    @GetMapping("/category/tree")
    public Result<List<KnowledgeCategory>> categoryTree() {
        return Result.OK(knowledgeService.listCategories());
    }

    @GetMapping("/article/page")
    public Result<Page<KnowledgeArticleResponseDTO>> articlePage(
            @RequestParam(value = "currentPage", required = false) Long currentPage,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            @RequestParam(value = "page", required = false) Long page,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection) {
        long current = currentPage != null ? currentPage : (page != null ? page : 1);
        long pageSizeValue = pageSize != null ? pageSize : (size != null ? size : 10);
        if (current < 1) {
            current = 1;
        }
        if (pageSizeValue < 1) {
            pageSizeValue = 10;
        }
        return Result.OK(knowledgeService.pageArticles(current, pageSizeValue, title, categoryId,
                parseStatus(status), sortField, sortDirection));
    }

    @GetMapping("/article/{id}")
    public Result<KnowledgeArticleResponseDTO> articleDetail(@PathVariable String id) {
        return Result.OK(knowledgeService.getArticleDetail(id));
    }

    @PostMapping("/article")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> createArticle(@Valid @RequestBody KnowledgeArticleCommandDTO dto) {
        knowledgeService.createArticle(getCurrentLoginUser().getUserId(), dto);
        return Result.OK();
    }

    @PutMapping("/article/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateArticle(@PathVariable String id, @Valid @RequestBody KnowledgeArticleCommandDTO dto) {
        knowledgeService.updateArticle(id, dto);
        return Result.OK();
    }

    @PutMapping("/article/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateArticleStatus(@PathVariable String id,
                                            @RequestBody KnowledgeArticleStatusDTO dto) {
        knowledgeService.updateStatus(id, dto.getStatus());
        return Result.OK();
    }

    @DeleteMapping("/article/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteArticle(@PathVariable String id) {
        knowledgeService.deleteArticle(id);
        return Result.OK();
    }

    private LoginUser getCurrentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return new LoginUser(null, null, 1);
        }
        return loginUser;
    }

    private Integer parseStatus(String status) {
        if (StringUtils.hasText(status)) {
            try {
                return Integer.valueOf(status);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}