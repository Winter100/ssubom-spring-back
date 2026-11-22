package com.project.review.review.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 사유 선택 항목입니다. 실제 항목은 관리자가 DB에 등록하여 관리합니다.
 * 대표 예시: 광고·도배, 욕설·비방, 개인정보 노출, 불법·유해 콘텐츠, 기타.
 */
@Entity
@Table(name = "review_report_types", uniqueConstraints = @UniqueConstraint(
        name = "uk_review_report_types_name", columnNames = "name"
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReportType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Min(0)
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    public ReviewReportType(String name, int displayOrder) {
        update(name, displayOrder, true);
    }

    public void update(String name, int displayOrder, boolean active) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
