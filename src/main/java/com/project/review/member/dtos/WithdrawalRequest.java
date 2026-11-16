package com.project.review.member.dtos;

import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(@NotBlank String password) {}
