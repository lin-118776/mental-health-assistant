package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.qispringboot.DTO.response.AdminUserResponseDTO;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.common.ResultCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AdminUserService {
    private final UserMapper users;

    public AdminUserService(UserMapper users) { this.users = users; }

    public Page<AdminUserResponseDTO> page(String keyword, Integer status, long page, long size) {
        if (status != null && status != 0 && status != 1) throw new BusinessException("账号状态无效");
        String word = keyword == null ? "" : keyword.trim();
        if (word.length() > 100) throw new BusinessException("搜索关键词不能超过100个字符");
        var query = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getNickname, User::getEmail, User::getPhone,
                        User::getUserType, User::getStatus, User::getCreatedAt)
                .eq(status != null, User::getStatus, status);
        if (!word.isEmpty()) {
            query.and(q -> q.like(User::getUsername, word).or().like(User::getNickname, word)
                    .or().like(User::getEmail, word).or().like(User::getPhone, word));
        }
        query.orderByDesc(User::getCreatedAt).orderByDesc(User::getId);
        var source = users.selectPage(new Page<User>(Math.max(1, page), Math.max(1, Math.min(100, size))), query);
        var result = new Page<AdminUserResponseDTO>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(user -> new AdminUserResponseDTO(user.getId(),
                user.getUsername(), user.getNickname(), user.getEmail(), user.getPhone(), user.getUserType(),
                user.getStatus(), user.getCreatedAt())).toList());
        return result;
    }

    public void updateStatus(Long id, Integer status) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser login))
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (status == null || status != 0 && status != 1) throw new BusinessException("账号状态无效");
        if (id.equals(login.getUserId())) throw new BusinessException("不能修改自己的账号状态");
        User target = users.selectById(id);
        if (target == null) throw new BusinessException("用户不存在或已删除");
        if (!target.isUser()) throw new BusinessException("此处仅支持启用或禁用普通用户");
        int changed = users.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id).eq(User::getUserType, 1)
                .set(User::getStatus, status).set(User::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException("账号状态保存失败，请刷新后重试");
    }
}
