package org.example.qispringboot.DTO.response;

import java.time.LocalDateTime;

public record AdminUserResponseDTO(Long id, String username, String nickname, String email, String phone,
                                   Integer userType, Integer status, LocalDateTime createdAt) {}
