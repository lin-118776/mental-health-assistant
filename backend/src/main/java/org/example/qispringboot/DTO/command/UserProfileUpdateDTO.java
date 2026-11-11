package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UserProfileUpdateDTO {
    @Size(max = 50, message = "昵称不能超过50字")
    private String nickname;
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100字")
    private String email;
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @Min(0) @Max(2)
    private Integer gender;
    @PastOrPresent(message = "生日不能晚于今天")
    private LocalDate birthday;
}
