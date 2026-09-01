package org.example.qispringboot.controller;

import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.response.DataAnalyticsOverviewDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.service.DataAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Resource
    private DataAnalyticsService dataAnalyticsService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DataAnalyticsOverviewDTO> overview() {
        return Result.OK(dataAnalyticsService.overview());
    }
}