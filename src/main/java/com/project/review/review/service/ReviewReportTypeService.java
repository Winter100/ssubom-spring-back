package com.project.review.review.service;

import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.review.dtos.AdminReviewReportTypeResponse;
import com.project.review.review.dtos.ReviewReportTypeCreateRequest;
import com.project.review.review.dtos.ReviewReportTypeResponse;
import com.project.review.review.dtos.ReviewReportTypeUpdateRequest;
import com.project.review.review.entities.ReviewReportType;
import com.project.review.review.repository.ReviewReportTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewReportTypeService {
    private final ReviewReportTypeRepository types;

    @Transactional(readOnly = true)
    public List<ReviewReportTypeResponse> findActive() {
        return types.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(ReviewReportTypeResponse::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AdminReviewReportTypeResponse> findAll() {
        return types.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(AdminReviewReportTypeResponse::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminReviewReportTypeResponse create(ReviewReportTypeCreateRequest request) {
        if (types.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_ALREADY_EXISTS);
        }
        return AdminReviewReportTypeResponse.from(types.saveAndFlush(
                new ReviewReportType(request.name(), request.displayOrder())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminReviewReportTypeResponse update(Long id, ReviewReportTypeUpdateRequest request) {
        ReviewReportType type = types.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_NOT_FOUND));
        if (types.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_ALREADY_EXISTS);
        }
        int displayOrder = request.displayOrder() == null ? type.getDisplayOrder() : request.displayOrder();
        type.update(request.name(), displayOrder, request.active());
        return AdminReviewReportTypeResponse.from(type);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<AdminReviewReportTypeResponse> reorder(List<Long> reportTypeIds) {
        var requestedIds = new HashSet<>(reportTypeIds);
        if (requestedIds.size() != reportTypeIds.size()) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_ORDER_DUPLICATE_ID);
        }

        var byId = types.findAllForUpdate().stream()
                .collect(Collectors.toMap(ReviewReportType::getId, Function.identity()));
        if (!byId.keySet().equals(requestedIds)) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_TYPE_ORDER_LIST_CHANGED);
        }

        var ordered = new ArrayList<AdminReviewReportTypeResponse>(reportTypeIds.size());
        for (int index = 0; index < reportTypeIds.size(); index++) {
            ReviewReportType type = byId.get(reportTypeIds.get(index));
            type.update(type.getName(), index + 1, type.isActive());
            ordered.add(AdminReviewReportTypeResponse.from(type));
        }
        return ordered;
    }
}
