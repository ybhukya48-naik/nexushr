package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PerformanceService {

    private final PerformanceReviewRepository reviewRepository;

    public PerformanceService(PerformanceReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public PerformanceReview create(PerformanceReview review) {
        return reviewRepository.save(review);
    }

    public List<PerformanceReview> findAll() {
        return reviewRepository.findAll();
    }
}
