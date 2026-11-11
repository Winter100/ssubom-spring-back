package com.project.review.member.entities;

import com.project.review.auth.entities.RefreshToken;
import com.project.review.auth.entities.Role;
import com.project.review.review.entities.Review;
import com.project.review.storage.entities.ImageUploadStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ID
 * 이메일
 * 비밀번호
 * 닉네임
 * Role
 * 프로필 이미지(옵션)
 */

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_members_nickname", columnNames = "nickname")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    private Instant nicknameChangedAt;

    @Column(nullable = false)
    private String password;

    @Column()
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "member")
    private List<RefreshToken> refreshTokenList = new ArrayList<>();

    @OneToMany(mappedBy = "author")
    private List<Review> reviewList = new ArrayList<>();

    @OneToMany(mappedBy = "member")
    private List<ImageUploadStatus> imageUploadStatusList = new ArrayList<>();

    @Builder
    public Member(String email, String nickname, String password) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
    }

    public void changeName(String nickname, Instant changedAt) {
        if (StringUtils.hasText(nickname)) {
            this.nickname = nickname;
            this.nicknameChangedAt = changedAt;
        }
    }

    public void changeProfileImage(String profileImage) {
        if (StringUtils.hasText(profileImage)) {
            this.profileImage = profileImage;
        }
    }
}
