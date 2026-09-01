package org.example.qispringboot.controller;

import org.example.qispringboot.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class Test {
    @GetMapping("/test")
    public Result<String> test() {
//        Result result = new Result<>();
//        result.setCode(200);
//        System.out.println(result.getCode());
        return Result.OK("hello world");
    }
}
