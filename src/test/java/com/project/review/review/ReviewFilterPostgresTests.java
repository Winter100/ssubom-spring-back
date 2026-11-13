package com.project.review.review;

import com.project.review.review.service.ReviewService;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REVIEW_POSTGRES_FILTER_TESTS", matches = "true")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.datasource.hikari.read-only=true"})
@Transactional(readOnly = true)
class ReviewFilterPostgresTests {
    @Autowired ReviewService service;
    @Autowired CategoryRepository categories;
    @Autowired ReviewRepository reviews;

    @Test
    void categoryOnlyQueriesRunOnPostgresWithoutNullableSearchTextErrors() {
        for (var category : categories.findAll()) {
            var result = service.findAll(1, 10, "title", category.getId(), null, null);
            assertThat(result.content()).allSatisfy(review ->
                    assertThat(review.category().id()).isEqualTo(category.getId()));
        }
        var sample = reviews.findAllSummaries(PageRequest.of(0, 1));
        if (!sample.isEmpty()) {
            var review = sample.getContent().getFirst();
            var result = service.findReviewByMemberId(review.author().id(), "1", "10", review.category().id().toString());
            assertThat(result.content()).allSatisfy(item -> {
                assertThat(item.author().id()).isEqualTo(review.author().id());
                assertThat(item.category().id()).isEqualTo(review.category().id());
            });
        }
    }
}
