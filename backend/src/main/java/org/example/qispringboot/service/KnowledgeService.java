package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.qispringboot.DTO.response.KnowledgeArticleResponseDTO;
import org.example.qispringboot.entity.KnowledgeArticle;
import org.example.qispringboot.entity.KnowledgeCategory;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.KnowledgeArticleMapper;
import org.example.qispringboot.mapper.KnowledgeCategoryMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private static final java.util.Set<String> SORT_WHITELIST = java.util.Set.of(
            "publishedAt", "readCount", "createdAt", "updatedAt");

    @Resource
    private KnowledgeArticleMapper articleMapper;

    @Resource
    private KnowledgeCategoryMapper categoryMapper;

    @Resource
    private UserMapper userMapper;

    public List<KnowledgeCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<KnowledgeCategory>()
                .eq(KnowledgeCategory::getStatus, 1)
                .orderByAsc(KnowledgeCategory::getSortOrder)
                .orderByAsc(KnowledgeCategory::getId));
    }

    public Page<KnowledgeArticleResponseDTO> pageArticles(Long current, Long size, String title,
                                                          Long categoryId, Integer status,
                                                          String sortField, String sortDirection) {
        LambdaQueryWrapper<KnowledgeArticle> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), KnowledgeArticle::getTitle, title)
                .eq(categoryId != null, KnowledgeArticle::getCategoryId, categoryId)
                .eq(status != null, KnowledgeArticle::getStatus, status);
        applySort(qw, sortField, sortDirection);

        Page<KnowledgeArticle> page = new Page<>(current, size);
        articleMapper.selectPage(page, qw);

        Map<Long, String> categoryNames = loadCategoryNames();
        Map<Long, String> authorNames = loadAuthorNames();

        List<KnowledgeArticleResponseDTO> records = page.getRecords().stream()
                .map(a -> toResponse(a, categoryNames, authorNames))
                .toList();

        Page<KnowledgeArticleResponseDTO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return result;
    }

    public KnowledgeArticleResponseDTO getArticleDetail(String id) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("article not found");
        }
        int readCount = (article.getReadCount() == null ? 0 : article.getReadCount()) + 1;
        KnowledgeArticle update = new KnowledgeArticle();
        update.setId(id);
        update.setReadCount(readCount);
        articleMapper.updateById(update);
        article.setReadCount(readCount);
        return toResponse(article, loadCategoryNames(), loadAuthorNames());
    }

    public void createArticle(Long authorId, KnowledgeArticleCommandDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeArticle article = KnowledgeArticle.builder()
                .categoryId(dto.getCategoryId())
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .content(dto.getContent())
                .coverImage(dto.getCoverImage())
                .tags(dto.getTags())
                .authorId(authorId)
                .readCount(0)
                .status(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        articleMapper.insert(article);
    }

    public void updateArticle(String id, KnowledgeArticleCommandDTO dto) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("article not found");
        }
        article.setTitle(dto.getTitle());
        article.setCategoryId(dto.getCategoryId());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCoverImage(dto.getCoverImage());
        article.setTags(dto.getTags());
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    public void updateStatus(String id, String statusValue) {
        Integer code = parseStatus(statusValue);
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("article not found");
        }
        article.setStatus(code);
        article.setUpdatedAt(LocalDateTime.now());
        if (Integer.valueOf(1).equals(code) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
    }

    public void deleteArticle(String id) {
        articleMapper.deleteById(id);
    }

    private void applySort(LambdaQueryWrapper<KnowledgeArticle> qw, String sortField, String sortDirection) {
        boolean asc = "asc".equalsIgnoreCase(sortDirection);
        if (!StringUtils.hasText(sortField) || !SORT_WHITELIST.contains(sortField)) {
            qw.orderByDesc(KnowledgeArticle::getCreatedAt);
        } else if ("publishedAt".equals(sortField)) {
            qw.orderBy(true, asc, KnowledgeArticle::getPublishedAt);
        } else if ("readCount".equals(sortField)) {
            qw.orderBy(true, asc, KnowledgeArticle::getReadCount);
        } else if ("updatedAt".equals(sortField)) {
            qw.orderBy(true, asc, KnowledgeArticle::getUpdatedAt);
        } else {
            qw.orderBy(true, asc, KnowledgeArticle::getCreatedAt);
        }
    }

    private Map<Long, String> loadCategoryNames() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(KnowledgeCategory::getId, KnowledgeCategory::getCategoryName, (a, b) -> a));
    }

    private Map<Long, String> loadAuthorNames() {
        return userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    private KnowledgeArticleResponseDTO toResponse(KnowledgeArticle a,
                                                   Map<Long, String> categoryNames,
                                                   Map<Long, String> authorNames) {
        return KnowledgeArticleResponseDTO.builder()
                .id(a.getId())
                .categoryId(a.getCategoryId())
                .categoryName(categoryNames.get(a.getCategoryId()))
                .title(a.getTitle())
                .summary(a.getSummary())
                .content(a.getContent())
                .coverImage(a.getCoverImage())
                .tags(a.getTags())
                .tagArray(KnowledgeArticleResponseDTO.splitTags(a.getTags()))
                .authorId(a.getAuthorId())
                .authorName(authorNames.get(a.getAuthorId()))
                .readCount(a.getReadCount())
                .status(a.getStatus())
                .publishedAt(a.getPublishedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private Integer parseStatus(String statusValue) {
        try {
            return Integer.valueOf(statusValue);
        } catch (NumberFormatException e) {
            throw new BusinessException("invalid status");
        }
    }
}