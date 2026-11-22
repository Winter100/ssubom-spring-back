package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.dtos.ReviewStats;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.config.import=")
class ReviewContentPersistenceTests {

    @Autowired private ReviewRepository reviews;
    @Autowired private MemberRepository members;
    @Autowired private CategoryRepository categories;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsAndUpdatesJsonDocumentAndReturnsAnObject() {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode content = mapper.readTree("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},
                   "content":[{"type":"text","text":"사용 후기"}]},
                  {"type":"paragraph","content":[
                    {"type":"text","text":"만족합니다.","marks":[{"type":"bold"}]}]},
                  {"type":"image","attrs":{"imageKey":"reviews/body-image","alt":"제품 사진"}}
                ]}
                """);
        Member author = members.save(Member.create("json@example.com", "json-user", "unused"));
        Category category = categories.save(new Category("JSON 테스트"));
        Review review = reviews.saveAndFlush(Review.create(new ReviewRequest("후기", "test product", content, BigDecimal.ONE,
                null, null, null, null, null, null, null),
                author, category));
        Long reviewId = review.getId();
        entityManager.clear();

        Review loaded = reviews.findById(reviewId).orElseThrow();
        assertThat(loaded.getContent()).isEqualTo(content);
        ReviewResponse response = ReviewResponse.from(loaded, new ReviewStats(0L, false, 0L), false, false);
        JsonNode responseJson = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(responseJson.path("content").isObject()).isTrue();
        assertThat(responseJson.path("content")).isEqualTo(content);

        JsonNode updated = textDocument("수정한 본문");
        loaded.updateDetails("수정한 제목", updated, BigDecimal.valueOf(5), loaded.getProduct(), loaded.getCategory(), loaded.getDuration(), loaded.getPros(), loaded.getCons(), loaded.getVerdict());
        reviews.flush();
        entityManager.clear();

        assertThat(reviews.findById(reviewId).orElseThrow().getContent()).isEqualTo(updated);
    }
}
