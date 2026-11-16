package com.project.review.member.dtos;

import jakarta.validation.constraints.Pattern;
import org.springframework.util.StringUtils;

public record MemberUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "닉네임이 필요합니다.")
        String nickName,

        @Pattern(regexp = ".*\\S.*", message = "이미지가 필요합니다.")
        String profileImage
) {
    public MemberUpdateRequest {
        if (nickName != null) {
            nickName = nickName.replaceAll("(?U)\\s+", "");
        }
    }

    public boolean hasName() {
        return StringUtils.hasText(nickName);
    }

    public boolean hasProfileImage() {
        return StringUtils.hasText(profileImage);
    }
}
