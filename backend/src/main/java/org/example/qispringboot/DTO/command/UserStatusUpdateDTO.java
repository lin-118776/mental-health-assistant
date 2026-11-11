package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusUpdateDTO {
    @NotNull(message = "请选择账号状态")
    @Min(value = 0, message = "状态仅支持启用或禁用")
    @Max(value = 1, message = "状态仅支持启用或禁用")
    private Integer status;
}
