package com.project.review.review;

import com.project.review.review.dtos.ReviewRequest;
import com.project.review.review.dtos.ReviewUpdateRequest;
import com.project.review.storage.dtos.ImageUploadBatchRequest;
import com.project.review.storage.dtos.ImageUploadRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestBodyTests {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void bindsBodyAndPreservesNestedContent() {
        JsonNode json = mapper.readTree("""
                {
                  "title": "리뷰 제목",
                  "product": "제품",
                  "body": {
                    "type": "doc",
                    "content": [
                      {"type": "paragraph", "content": [{"type": "text", "text": "본문"}]}
                    ]
                  },
                  "rating": 4.5,
                  "categoryId": 1
                }
                """);

        ReviewRequest request = mapper.treeToValue(json, ReviewRequest.class);

        assertThat(request.content()).isEqualTo(json.path("body"));
        assertThat(request.content().path("content").get(0).path("content").get(0).path("text").asString())
                .isEqualTo("본문");
        JsonNode serialized = mapper.valueToTree(request);
        assertThat(serialized.path("content")).isEqualTo(json.path("body"));
        assertThat(serialized.has("body")).isFalse();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsMissingBodyAndInvalidDocumentStructure() {
        for (String body : new String[]{"null", "{}", "{\"type\":\"doc\",\"content\":{}}",
                "{\"type\":\"paragraph\",\"content\":[]}"}) {
            ReviewRequest request = mapper.readValue("""
                    {"title":"리뷰 제목","product":"제품","body":%s,"rating":4.5,"categoryId":1}
                    """.formatted(body), ReviewRequest.class);

            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(request)).isNotEmpty();
            }
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null|null",
            "null|[]",
            "\"reviews/main\"|null",
            "null|[\"reviews/sub\"]",
            "\"reviews/main\"|[\"reviews/sub\"]"
    }, delimiter = '|')
    void acceptsIndependentOptionalImages(String main, String sub) {
        ReviewRequest request = mapper.readValue("""
                {"title":"리뷰 제목","product":"제품","body":{"type":"doc","content":[]},
                 "rating":4.5,"categoryId":1,"mainImageKey":%s,"subImageKeys":%s}
                """.formatted(main, sub), ReviewRequest.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"10,true", "11,false"})
    void validatesImageCountLimits(int count, boolean valid) {
        List<String> keys = IntStream.range(0, count)
                .mapToObj(index -> "reviews/sub-" + index)
                .toList();
        JsonNode content = mapper.readTree("""
                {"type":"doc","content":[]}
                """);
        ReviewRequest create = new ReviewRequest("제목", "제품", content, BigDecimal.ONE,
                1L, "reviews/main", keys, null, null, null, null);
        ReviewUpdateRequest update = new ReviewUpdateRequest("제목", content, BigDecimal.ONE, "Product", 1L, "reviews/main", keys, null, null, null, null);
        ImageUploadBatchRequest batch = new ImageUploadBatchRequest(IntStream.range(0, count)
                .mapToObj(index -> new ImageUploadRequest("image-" + index + ".png", "image/png",
                        100L, "client-" + index))
                .toList());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(create).isEmpty()).isEqualTo(valid);
            assertThat(validator.validate(update).isEmpty()).isEqualTo(valid);
            assertThat(validator.validate(batch).isEmpty()).isEqualTo(valid);
        }
    }
}
