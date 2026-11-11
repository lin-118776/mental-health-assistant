package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.qispringboot.DTO.command.UserPasswordUpdateDTO;
import org.example.qispringboot.DTO.command.UserProfileUpdateDTO;
import org.example.qispringboot.DTO.response.UserLoginResponseDTO.UserDetailResponseDTO;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.convert.UserConvert;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class UserProfileService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    private User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser))
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_EXIST);
        if (!user.isActive()) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        return user;
    }

    public UserDetailResponseDTO updateProfile(UserProfileUpdateDTO dto) {
        User user = currentUser();
        String phone = optional(dto.getPhone());
        String email = optional(dto.getEmail());
        if (phone != null && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ne(User::getId, user.getId()).eq(User::getPhone, phone)) > 0)
            throw new BusinessException(ResultCode.PHONE_EXIST);
        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ne(User::getId, user.getId()).eq(User::getEmail, email)) > 0)
            throw new BusinessException("该邮箱已被使用");
        LocalDateTime now = LocalDateTime.now();
        int changed = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId()).eq(User::getStatus, 1)
                .set(User::getNickname, optional(dto.getNickname())).set(User::getEmail, email)
                .set(User::getPhone, phone).set(User::getGender, dto.getGender())
                .set(User::getBirthday, dto.getBirthday()).set(User::getUpdatedAt, now));
        if (changed == 0) throw new BusinessException("保存失败，请刷新后重试");
        user.setNickname(optional(dto.getNickname()));
        user.setEmail(email);
        user.setPhone(phone);
        user.setGender(dto.getGender());
        user.setBirthday(dto.getBirthday());
        user.setUpdatedAt(now);
        return UserConvert.entityToDetailResponse(user);
    }

    public void updatePassword(UserPasswordUpdateDTO dto) {
        User user = currentUser();
        if (dto.getOldPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(dto.getOldPassword(), user.getPassword()))
            throw new BusinessException("原密码不正确");
        if (dto.getNewPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException("新密码UTF-8编码长度不能超过72字节");
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword()))
            throw new BusinessException("新密码不能与原密码相同");
        int changed = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId()).eq(User::getStatus, 1)
                .eq(User::getPassword, user.getPassword())
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword()))
                .set(User::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException("密码已变化或账号状态异常，请重新登录");
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
