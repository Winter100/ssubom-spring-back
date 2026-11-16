package com.project.review.auth.repository;

import com.project.review.auth.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Modifying
    @Query("delete from RefreshToken t where t.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

    @EntityGraph(attributePaths = "member")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByMemberId(Long memberId);

    void deleteByTokenHash(String tokenHash);
}
