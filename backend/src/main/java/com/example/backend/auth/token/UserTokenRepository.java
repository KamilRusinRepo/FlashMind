package com.example.backend.auth.token;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    @Query("select token.user.id from UserToken token where token.tokenHash = :hash")
    Optional<Long> findOwnerIdByTokenHash(@Param("hash") String tokenHash);

    Optional<UserToken> findFirstByUser_IdAndTypeOrderByCreatedAtDesc(Long userId, TokenType type);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserToken token where token.user.id = :userId and token.type = :type and token.usedAt is null")
    void deleteUnusedByUserIdAndType(@Param("userId") Long userId, @Param("type") TokenType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from UserToken token where token.tokenHash = :hash")
    Optional<UserToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);
}
