package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.service.PerformanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @GetMapping
    public List<PerformanceReview> list() {
        return performanceService.findAll();
    }

    @PostMapping
    public PerformanceReview create(@RequestBody PerformanceReview review) {
        return performanceService.create(review);
    }
}
