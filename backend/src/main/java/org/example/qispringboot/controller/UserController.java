package org.example.qispringboot.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.qispringboot.DTO.UserLoginCommandDTO;
import org.example.qispringboot.DTO.UserRegisterCommandDTO;
import org.example.qispringboot.DTO.response.UserLoginResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 * 提供注册、登录、获取当前登录用户等接口
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册接口
     * 使用@Valid校验账号、手机号、密码等参数格式
     */
    @PostMapping("/add")
    public Result<Void> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO) {
        return userService.register(commandDTO);
    }

    /**
     * 用户登录接口
     * 认证成功后返回JWT令牌与用户信息
     */
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO) {
        return userService.login(commandDTO);
    }

    /**
     * 获取当前登录用户接口
     * 从Security上下文读取登录人并校验账号状态
     */
    @GetMapping("/me")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> me() {
        return userService.getCurrentUser();
    }

    /**
     * 用户登出接口
     * JWT为无状态认证，前端清除本地token即可；此处返回统一成功结构
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.OK();
    }
}