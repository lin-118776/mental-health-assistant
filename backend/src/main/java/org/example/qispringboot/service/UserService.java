package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.UserLoginCommandDTO;
import org.example.qispringboot.DTO.UserRegisterCommandDTO;
import org.example.qispringboot.DTO.response.UserLoginResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.config.JwtUtil;
import org.example.qispringboot.convert.UserConvert;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.entity.UserStatus;
import org.example.qispringboot.entity.UserType;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;

/**
 * 用户服务类
 * 提供用户注册、登录、获取当前登录用户等业务方法
 */
@Service
public class UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     * 校验用户名/手机号是否已存在，密码使用BCrypt加密后写入数据库
     *
     * @param commandDTO 注册请求参数
     * @return 统一响应
     */
    public Result<Void> register(UserRegisterCommandDTO commandDTO) {
        if (commandDTO.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException("密码过长，请减少中文字符");
        String email = commandDTO.getEmail() == null || commandDTO.getEmail().isBlank()
                ? null : commandDTO.getEmail().trim();
        // 1. 校验用户名是否已注册
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, commandDTO.getUsername()));
        if (usernameCount != null && usernameCount > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME);
        }
        // 2. 校验手机号是否已注册
        Long phoneCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, commandDTO.getPhone()));
        if (phoneCount != null && phoneCount > 0) {
            throw new BusinessException(ResultCode.PHONE_EXIST);
        }
        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0)
            throw new BusinessException("邮箱已被注册");
        // 3. 构建用户实体（密码使用BCrypt加密存储）
        User user = User.builder()
                .username(commandDTO.getUsername())
                .phone(commandDTO.getPhone())
                .password(passwordEncoder.encode(commandDTO.getPassword()))
                .email(email)
                .nickname(commandDTO.getNickname() == null || commandDTO.getNickname().isBlank()
                        ? null : commandDTO.getNickname().trim())
                .userType(UserType.USER.getCode())          // 注册默认普通用户
                .status(UserStatus.NORMAL.getCode())        // 注册默认正常状态
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        // 4. 插入数据库
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("用户名、手机号或邮箱已被注册，请修改后重试");
        }
        return Result.OK();
    }

    /**
     * 用户登录
     * 校验用户名/邮箱与密码，账号正常时签发JWT令牌并组装登录返回实体
     *
     * @param commandDTO 登录请求参数
     * @return 统一响应（携带token与用户详情）
     */
    public Result<UserLoginResponseDTO> login(UserLoginCommandDTO commandDTO) {
        // 1. 按用户名或邮箱查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, commandDTO.getUsername())
                .or()
                .eq(User::getEmail, commandDTO.getUsername());
        User user = userMapper.selectOne(queryWrapper);
        // 2. 用户不存在
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 3. 校验密码（BCrypt加密比对）
        if (!passwordEncoder.matches(commandDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        // 4. 校验账号状态是否被禁用（封禁）
        if (user.isDisabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        // 5. 认证通过，生成JWT令牌
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getUserType(), user.getPassword());
        // 6. 组装登录返回实体（token + 角色 + 用户详情）
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = UserConvert.entityToDetailResponse(user);
        UserLoginResponseDTO loginResponse = UserConvert.buildLoginResponse(token, userInfo);
        return Result.OK(loginResponse);
    }

    /**
     * 获取当前登录用户
     * 从Security上下文读取登录人，并校验账号状态是否被禁用
     *
     * @return 统一响应（携带当前用户详情）
     */
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser() {
        // 1. 从Security上下文获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 2. 未登录或认证主体不是LoginUser时抛出未授权异常
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 3. 根据用户ID查询最新用户信息
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 4. 校验账号状态是否被禁用（被封禁则拒绝访问）
        if (user.isDisabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        // 5. 返回用户详情
        return Result.OK(UserConvert.entityToDetailResponse(user));
    }
}
