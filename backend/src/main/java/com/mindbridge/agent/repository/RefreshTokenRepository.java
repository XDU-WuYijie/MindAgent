package com.mindbridge.agent.repository;

import com.mindbridge.agent.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @Modifying
    @Transactional
    @Query("delete from RefreshToken r where r.userId = :userId")
    int deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from RefreshToken r where r.expiresAt < :time")
    int deleteByExpiresAtBefore(LocalDateTime time);
}
