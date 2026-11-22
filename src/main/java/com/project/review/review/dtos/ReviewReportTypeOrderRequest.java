package com.project.review.review.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReviewReportTypeOrderRequest(@NotNull List<@NotNull @Positive Long> reportTypeIds) {}
