package com.zidio.nexushr.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "performance_reviews")
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Employee employee;

    @Column(nullable = false)
    private Integer reviewYear;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private String feedback;

    @Column(nullable = false)
    private LocalDate reviewDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Integer getReviewYear() { return reviewYear; }
    public void setReviewYear(Integer reviewYear) { this.reviewYear = reviewYear; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }
}
