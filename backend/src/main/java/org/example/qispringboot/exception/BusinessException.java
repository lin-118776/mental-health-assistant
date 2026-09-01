package org.example.qispringboot.exception;

import lombok.Getter;
import org.example.qispringboot.common.ResultCode;

/**
 * 自定义业务异常
 * 业务逻辑异常统一抛出该异常，由全局异常处理器转换为Result格式响应
 */
@Getter
public class BusinessException extends RuntimeException {

    // 业务异常码
    private final String code;

    // 业务异常消息
    private final String message;

    // 附加数据（可为空）
    private final Object data;

    /**
     * 仅传消息构造业务异常（默认错误码）
     */
    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
        this.message = message;
        this.data = null;
    }

    /**
     * 根据统一响应码枚举构造业务异常
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.message = resultCode.getMsg();
        this.data = null;
    }
}