package com.project.review.member.repository;

import com.project.review.member.entities.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    @Query("select m.id from Member m where m.withdrawalScheduledAt <= :cutoff and m.id > :afterId order by m.id")
    List<Long> findDueWithdrawalIds(@Param("cutoff") Instant cutoff, @Param("afterId") Long afterId, Pageable pageable);

    @Query("select m.profileImage from Member m where m.profileImage in :keys")
    Set<String> findReferencedProfileImageKeys(@Param("keys") Set<String> keys);

    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}
