package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

        qw.orderByDesc(KnowledgeArticle::getId);
        Page<KnowledgeArticle> page = new Page<>(Math.max(1, current), Math.max(1, Math.min(100, size)));
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

    public Page<KnowledgeArticleResponseDTO> pagePublishedArticles(Long current, Long size, String title,
            Long categoryId, String sortField, String sortDirection) {
        return pageArticles(current, size, title, categoryId, 1, sortField, sortDirection);
    }

    public KnowledgeArticleResponseDTO getAdminArticleDetail(String id) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("article not found");
        }
        return toResponse(article, loadCategoryNames(), loadAuthorNames());
    }

    public KnowledgeArticleResponseDTO getPublishedArticleDetail(String id) {
        KnowledgeArticle article = articleMapper.selectOne(new LambdaQueryWrapper<KnowledgeArticle>()
                .eq(KnowledgeArticle::getId, id).eq(KnowledgeArticle::getStatus, 1));
        if (article == null) throw new BusinessException("文章不存在或已下线");
        int changed = articleMapper.update(null, new LambdaUpdateWrapper<KnowledgeArticle>()
                .eq(KnowledgeArticle::getId, id).eq(KnowledgeArticle::getStatus, 1)
                .setSql("read_count=COALESCE(read_count,0)+1, updated_at=updated_at"));
        if (changed == 0) throw new BusinessException("文章不存在或已下线");
        article.setReadCount((article.getReadCount() == null ? 0 : article.getReadCount()) + 1);
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
        // 内容编辑不回写读数、作者或状态，避免把并发下线的文章重新发布。
        int changed = articleMapper.update(null, new LambdaUpdateWrapper<KnowledgeArticle>()
                .eq(KnowledgeArticle::getId, id).set(KnowledgeArticle::getTitle, dto.getTitle())
                .set(KnowledgeArticle::getCategoryId, dto.getCategoryId())
                .set(KnowledgeArticle::getSummary, dto.getSummary()).set(KnowledgeArticle::getContent, dto.getContent())
                .set(KnowledgeArticle::getCoverImage, dto.getCoverImage()).set(KnowledgeArticle::getTags, dto.getTags())
                .set(KnowledgeArticle::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException("文章不存在或已删除");
    }

    public void updateStatus(String id, String statusValue) {
        Integer code = parseStatus(statusValue);
        var now = LocalDateTime.now();
        var update = new LambdaUpdateWrapper<KnowledgeArticle>().eq(KnowledgeArticle::getId, id)
                .set(KnowledgeArticle::getStatus, code).set(KnowledgeArticle::getUpdatedAt, now);
        if (code == 1) update.setSql("published_at=COALESCE(published_at, {0})", now);
        if (articleMapper.update(null, update) == 0) throw new BusinessException("文章不存在或已删除");
    }

    public void deleteArticle(String id) {
        int changed = articleMapper.delete(new LambdaQueryWrapper<KnowledgeArticle>()
                .eq(KnowledgeArticle::getId, id).in(KnowledgeArticle::getStatus, 0, 2));
        if (changed == 0) throw new BusinessException("文章不存在，或仍在发布中，请先下线再删除");
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
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getNickname, User::getUsername)).stream()
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
            int status = Integer.parseInt(statusValue);
            if (status < 0 || status > 2) throw new BusinessException("文章状态无效");
            return status;
        } catch (NumberFormatException e) {
            throw new BusinessException("invalid status");
        }
    }
}
