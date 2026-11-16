package com.project.review.review.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ReviewViewRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public void increment(Long reviewId) {
        jdbc.update("""
                INSERT INTO review_view_counts (review_id, view_count) VALUES (:reviewId, 1)
                ON CONFLICT (review_id)
                DO UPDATE SET view_count = review_view_counts.view_count + 1
                """, Map.of("reviewId", reviewId));
    }

    public long findCount(Long reviewId) {
        return findCounts(List.of(reviewId)).getOrDefault(reviewId, 0L);
    }

    public Map<Long, Long> findCounts(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("SELECT review_id, view_count FROM review_view_counts WHERE review_id IN (:ids)",
                Map.of("ids", reviewIds),
                row -> {
                    counts.put(row.getLong("review_id"), row.getLong("view_count"));
                });
        return counts;
    }
}
