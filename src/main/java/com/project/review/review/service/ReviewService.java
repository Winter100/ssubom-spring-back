package com.project.review.review.service;

import com.project.review.category.entities.Category;
import com.project.review.category.service.CategoryService;
import com.project.review.comment.repository.CommentRepository;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.member.service.MemberService;
import com.project.review.review.dtos.*;
import com.project.review.review.entities.Review;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.storage.service.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final CategoryService categoryService;
    private final StorageService storageService;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final ReviewImageDeleteRepository reviewImageDeleteRepository;
    private final ReviewViewRepository reviewViewRepository;
    private final ReviewImageKeys reviewImageKeys;
    private final Clock imageDeletionClock;
    private final ReviewLikeService reviewLikeService;

    /**
     * 1부터 시작하는 요청 페이지를 JPA의 0 기반 페이지로 변환합니다.
     */
    @Transactional(readOnly = true)
    public ReviewPageResponse findAll(int page, int size, String searchType, Long categoryId, String query, Long memberId) {
        if (page < 1) {
            throw new IllegalArgumentException("페이지는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }

        if (!"title".equals(searchType) && !"author".equals(searchType)) {
            throw new IllegalArgumentException("검색 유형은 title 또는 author여야 합니다.");
        }
        String searchQuery = query == null || query.isBlank() ? null : query;
        String titleQuery = "title".equals(searchType) && searchQuery != null ? searchQuery : "";
        String authorQuery = "author".equals(searchType) && searchQuery != null ? searchQuery : "";

        PageRequest pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        boolean hasSearchConditions = categoryId != null || searchQuery != null;
        Page<ReviewSummaryResponse> reviews = hasSearchConditions
                ? reviewRepository.findAllSummaries(categoryId, titleQuery, authorQuery, pageable)
                : reviewRepository.findAllSummaries(pageable);
        return getReviewPageResponse(memberId, reviews);
    }

    /**
     * 내 게시글 조회
     */
    @Transactional(readOnly = true)
    public ReviewPageResponse findReviewByMemberId(
            Long memberId, String page, String size, String categoryId) {
        int pageNumber;
        int pageSize;
        Long category;
        try {
            pageNumber = Integer.parseInt(page);
            pageSize = Integer.parseInt(size);
            category = categoryId == null ? null : Long.valueOf(categoryId);
            if (pageNumber < 1 || pageSize < 1 || pageSize > 100 || (category != null && category < 1)) {
                throw new IllegalArgumentException("유효하지 않은 조회 조건입니다.");
            }
        } catch (IllegalArgumentException exception) {
            pageNumber = 1;
            pageSize = 50;
            category = null;
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<ReviewSummaryResponse> reviews = reviewRepository.findAllSummariesByAuthorId(
                memberId, category, PageRequest.of(pageNumber - 1, pageSize, sort));
        return getReviewPageResponse(memberId, reviews);
    }

    @NonNull
    private ReviewPageResponse getReviewPageResponse(Long memberId, Page<ReviewSummaryResponse> reviews) {
        List<Long> reviewIds = reviews.getContent().stream()
                .map(ReviewSummaryResponse::id)
                .toList();
        Map<Long, Long> counts = reviewViewRepository.findCounts(reviewIds);
        Map<Long, Long> likeCounts = reviewLikeService.findLikeCounts(reviewIds);
        Set<Long> likedReviewIds = reviewLikeService.findLikedReviewIds(memberId, reviewIds);
        return ReviewPageResponse.from(reviews.map(
                review -> review.withStats(new ReviewStats(
                        counts.getOrDefault(review.id(), 0L),
                        likedReviewIds.contains(review.id()),
                        likeCounts.getOrDefault(review.id(), 0L)))));
    }

    /**
     * 카테고리별 Member가 작성한 리뷰 수
     */
    @Transactional(readOnly = true)
    public List<ReviewCategoryCountResponse> findByMyReviewCount(Long memberId) {
        return reviewRepository.findCategoryCountsByAuthorId(memberId);
    }

    /**
     * 상세 리뷰 조회
     */
    @Transactional
    public ReviewResponse findByIdAndCountView(Long reviewId, Long memberId) {
        Review review = findReviewById(reviewId);

        // 조회수 증가
        reviewViewRepository.increment(reviewId);

        // 조회수
        long viewCount = reviewViewRepository.findCount(reviewId);

        // 좋아요 여부
        boolean isLike = reviewLikeService.isLike(reviewId, memberId);

        // 리뷰의 좋아요 수
        long countLike = reviewLikeService.countLike(reviewId);

        return ReviewResponse.from(review, new ReviewStats(viewCount, isLike, countLike));
    }

    /**
     * 수정용 리뷰 조회
     */
    @Transactional(readOnly = true)
    public ReviewEditResponse findByIdEditView(Long reviewId, Long memberId) {

        Review review = findReviewById(reviewId);
        review.validateAuthor(memberId);

        return ReviewEditResponse.from(review);
    }

    /**
     * 리뷰 생성
     * 업로드된 이미지의 첨부 가능 여부 및 R2 메타데이터를 검증한 뒤 리뷰 생성
     * 업로드 상태변경 및 리뷰 저장만 하나의 트랜잭션으로 처리
     */
    public void create(ReviewRequest reviewRequest, Long memberId) {
        Member member = memberService.findById(memberId);
        Category category = categoryService.findById(reviewRequest.categoryId());

        // 메인, 서브 이미지 키를 하나의 리스트로 반환
        List<String> imageKeys = collectImageKeys(reviewRequest.mainImageKey(),
                reviewRequest.subImageKeys(), reviewRequest.content());

        // 요청 이미지 키와 실제 R2 메타데이터 검증
        storageService.validateImagesForReview(imageKeys, member.getId());

        transactionTemplate.executeWithoutResult(status ->
                saveReview(reviewRequest, member, category, imageKeys));
    }

    /**
     * 리뷰 수정
     * - PUT 방식 기존 리뷰 -> 새로운 리뷰로 교체
     * - 이미지만 변경되어도 리뷰의 버전 증가
     */
    public void update(Long reviewId, ReviewUpdateRequest reviewUpdateRequest, Long memberId) {
        Review review = findReviewById(reviewId);
        review.validateAuthor(memberId);

        Long expectedVersion = review.getVersion();

        // 수정 요청에서 이미지 리스트 가져오기
        Set<String> requestImages = new HashSet<>(collectImageKeys(reviewUpdateRequest.mainImageKey(),
                reviewUpdateRequest.subImageKeys(), reviewUpdateRequest.content()));

        // 기존 리뷰에서 이미지 리스트 가져오기
        Set<String> existingImages = reviewImageKeys.collect(review);

        // 새로 생성 되는 이미지 키 분리 -> 이미지 등록 요청 시 생성된 이미지 테이블 검증 -> R2 검증
        List<String> createImageKeys = requestImages.stream().filter(key -> !existingImages.contains(key)).toList();
        storageService.validateImagesForReview(createImageKeys, memberId);

        // 이미지 삭제 테이블에 등록되어야 하는 이미지 키 분리
        List<String> removeImageKeys = existingImages.stream().filter(key -> !requestImages.contains(key)).toList();
        List<ReviewImageDelete> deleteImages = removeImageKeys.stream()
                .map(this::newImageDeletion)
                .toList();

        // 리뷰 수정 트랜잭션
        transactionTemplate.executeWithoutResult(status -> {
            Review currentReview = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_UPDATE_CONFLICT));
            if (!currentReview.getVersion().equals(expectedVersion)) {
                throw new BusinessException(ErrorCode.REVIEW_UPDATE_CONFLICT);
            }
            // 이미지 관계만 변경해도 버전을 증가시켜 다른 수정·삭제와의 충돌을 감지합니다.
            entityManager.lock(currentReview, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

            storageService.attachIfPending(createImageKeys, memberId);
            reviewImageDeleteRepository.saveAll(deleteImages);
            updateReview(currentReview, reviewUpdateRequest);
        });
    }

    /**
     * 리뷰 삭제
     * - 요청자와 리뷰 작성자 검증
     * - 삭제될 리뷰의 이미지 키를 이미지 삭제 예정 테이블에 등록 후 리뷰 삭제
     */
    @Transactional
    public void remove(Long reviewId, Long memberId) {
        // 댓글 변경과 같은 리뷰 행을 먼저 잠가 삭제 중 새 댓글이 추가되지 않도록 합니다.
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        review.validateAuthor(memberId);

        // 리뷰에서 이미지 키를 받고 삭제 테이블에 맞게 생성
        List<ReviewImageDelete> deleteImages = reviewImageKeys.collect(review).stream()
                .map(this::newImageDeletion)
                .toList();

        // Todo 이벤트로 이미지 삭제 테이블 등록 변경하기
        reviewImageDeleteRepository.saveAll(deleteImages);

        // 리뷰 삭제
        commentRepository.deleteRepliesByReviewId(reviewId);
        commentRepository.deleteAllByReviewId(reviewId);
        reviewRepository.delete(review);
    }

    // 리뷰 "도움이 됐어요" 추가
    @Transactional
    public void insertLike(Long reviewId, Long memberId) {
        Review review = findReviewById(reviewId);
        reviewLikeService.insertLike(review.getId(), memberId);
    }

    // 리뷰 "도움이 됐어요" 제거
    @Transactional
    public void deleteLike(Long reviewId, Long memberId) {
        Review review = findReviewById(reviewId);
        reviewLikeService.deleteLike(review.getId(), memberId);
    }

    // 리뷰 저장
    private void saveReview(ReviewRequest reviewRequest, Member member, Category category, List<String> imageKeys) {

        // 이미지 저장 요청시 Pending으로 저장되었던 요청을 attach로 변경
        storageService.attachIfPending(imageKeys, member.getId());

        Review review = Review.create(reviewRequest, member, category);

        String mainImageKey = reviewRequest.mainImageKey();
        List<String> subImageKeys = reviewRequest.subImageKeys();

        if (mainImageKey != null) {
            review.addImage(mainImageKey, 0);
        }

        if (subImageKeys != null) {
            for (int index = 0; index < subImageKeys.size(); index++) {
                review.addImage(subImageKeys.get(index), index + 1);
            }
        }

        reviewRepository.save(review);
    }

    private void updateReview(Review review, ReviewUpdateRequest reviewUpdateRequest) {
        Category category = categoryService.findById(reviewUpdateRequest.categoryId());
        review.updateDetails(
                reviewUpdateRequest.title(),
                reviewUpdateRequest.content(),
                reviewUpdateRequest.rating(),
                reviewUpdateRequest.product(),
                category,
                reviewUpdateRequest.duration(),
                reviewUpdateRequest.pros(),
                reviewUpdateRequest.cons(),
                reviewUpdateRequest.verdict()
        );

        review.clearImages();

        reviewRepository.flush();

        addReviewImages(
                review,
                reviewUpdateRequest.mainImageKey(),
                reviewUpdateRequest.subImageKeys()
        );
    }

    private Review findReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }


    // 이미지 리스트 키 추출 및 반환
    private List<String> collectImageKeys(String mainImageKey, List<String> subImageKeys, JsonNode content) {
        List<String> imageKeys = new ArrayList<>();

        if (mainImageKey != null) {
            imageKeys.add(mainImageKey);
        }
        if (subImageKeys != null) {
            imageKeys.addAll(subImageKeys);
        }
        if (new HashSet<>(imageKeys).size() != imageKeys.size()) {
            throw new IllegalArgumentException("같은 이미지를 중복 등록할 수 없습니다.");
        }

        return List.copyOf(reviewImageKeys.collect(mainImageKey, subImageKeys, content));
    }

    private ReviewImageDelete newImageDeletion(String key) {
        return new ReviewImageDelete(key, ImageDeletionTime.nextMidnight(imageDeletionClock.instant()));
    }

    private void addReviewImages(Review review, String mainImage, List<String> subImages) {
        if (mainImage != null) {
            review.addImage(mainImage, 0);
        }

        if (subImages != null) {
            for (int index = 0; index < subImages.size(); index++) {
                review.addImage(subImages.get(index), index + 1);
            }
        }
    }
}
