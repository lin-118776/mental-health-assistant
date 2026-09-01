package org.example.qispringboot.common;

import lombok.Data;

/**
 * 统一响应封装类
 * 所有接口均通过该类返回统一格式的JSON数据
 */
@Data
public class Result<T> {

    // 响应码
    private String code;

    // 响应消息
    private String msg;

    // 响应数据
    private T data;

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> OK() {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        return result;
    }

    /**
     * 成功响应（携带数据），方法重载
     */
    public static <T> Result<T> OK(T data) {
        Result<T> result = OK();
        result.setData(data);
        return result;
    }

    /**
     * 失败响应（默认错误码）
     */
    public static <T> Result<T> error() {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.ERROR.getCode());
        result.setMsg(ResultCode.ERROR.getMsg());
        return result;
    }

    /**
     * 失败响应（自定义错误码、消息与数据）
     */
    public static <T> Result<T> error(String code, String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    /**
     * 失败响应（根据统一响应码枚举构造）
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return error(resultCode.getCode(), resultCode.getMsg(), null);
    }
}