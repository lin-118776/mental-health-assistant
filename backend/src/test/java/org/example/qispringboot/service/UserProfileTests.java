package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.qispringboot.DTO.command.UserPasswordUpdateDTO;
import org.example.qispringboot.DTO.command.UserProfileUpdateDTO;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileTests {
    private UserMapper users;
    private UserProfileService service;
    private User user;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), User.class);
        users = mock(UserMapper.class);
        service = new UserProfileService(users, encoder);
        user = User.builder().id(7L).username("owner").userType(1).status(1)
                .password(encoder.encode("old-pass")).build();
        when(users.selectById(7L)).thenReturn(user);
        when(users.update(isNull(), any(Wrapper.class))).thenReturn(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "owner", 1), null, List.of()));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void profileUpdatesOnlyAllowedFieldsOfLoggedInUserAndSupportsClearing() {
        var dto = new UserProfileUpdateDTO();
        dto.setNickname(" 新昵称 ");
        dto.setBirthday(LocalDate.of(2000, 1, 1));
        dto.setGender(2);
        var result = service.updateProfile(dto);
        assertEquals("新昵称", result.getNickname());
        assertEquals("owner", result.getUsername());
        assertEquals(1, result.getUserType());
        var query = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(users).update(isNull(), query.capture());
        var wrapper = query.getValue();
        assertTrue(wrapper.getSqlSegment().contains("id ="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
        String set = wrapper.getSqlSet();
        assertFalse(set.contains("password="));
        assertFalse(set.contains("user_type="));
        assertFalse(set.contains("username="));
        assertFalse(set.contains("status="));
    }

    @Test
    void rejectsDuplicatePhoneAndEmailWithoutSaving() {
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        var dto = new UserProfileUpdateDTO();
        dto.setPhone("13800138000");
        assertThrows(BusinessException.class, () -> service.updateProfile(dto));
        dto.setPhone(null);
        dto.setEmail("used@example.com");
        assertThrows(BusinessException.class, () -> service.updateProfile(dto));
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void passwordIsEncodedAndUpdateRequiresCurrentPasswordHashAndOwner() {
        service.updatePassword(password("old-pass", "new-pass"));
        var query = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(users).update(isNull(), query.capture());
        var wrapper = query.getValue();
        assertTrue(wrapper.getSqlSegment().contains("password ="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(user.getPassword()));
        assertTrue(wrapper.getParamNameValuePairs().values().stream().anyMatch(value ->
                value instanceof String encoded && encoded.startsWith("$2") && encoder.matches("new-pass", encoded)));
        assertFalse(wrapper.getParamNameValuePairs().containsValue("new-pass"));
    }

    @Test
    void rejectsWrongOldPasswordReusedPasswordAndExcessiveUtf8Length() {
        assertThrows(BusinessException.class, () -> service.updatePassword(password("wrong", "new-pass")));
        assertThrows(BusinessException.class, () -> service.updatePassword(password("old-pass", "old-pass")));
        assertThrows(BusinessException.class, () -> service.updatePassword(password("old-pass", "密".repeat(25))));
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void rejectsMissingLoginAndDisabledAccount() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> service.updateProfile(new UserProfileUpdateDTO()));
        assertThrows(BusinessException.class, () -> service.updatePassword(password("old-pass", "new-pass")));
        verifyNoInteractions(users);
    }

    @Test
    void disabledAccountCannotUpdateEitherProfileOrPassword() {
        user.setStatus(0);
        assertThrows(BusinessException.class, () -> service.updateProfile(new UserProfileUpdateDTO()));
        assertThrows(BusinessException.class, () -> service.updatePassword(password("old-pass", "new-pass")));
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void concurrentPasswordChangeDoesNotReportSuccess() {
        when(users.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.updatePassword(password("old-pass", "new-pass")));
    }

    private UserPasswordUpdateDTO password(String old, String next) {
        var dto = new UserPasswordUpdateDTO();
        dto.setOldPassword(old);
        dto.setNewPassword(next);
        return dto;
    }
}
