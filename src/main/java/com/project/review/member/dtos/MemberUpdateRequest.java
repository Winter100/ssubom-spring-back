package com.project.review.member.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "닉네임이 필요합니다.")
        String nickName,

        @Pattern(regexp = ".*\\S.*", message = "이미지가 필요합니다.")
        String profileImage
) {
}
