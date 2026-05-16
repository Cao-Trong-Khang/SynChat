package com.synchat.backend.user.repository;

import com.synchat.backend.user.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select user
            from UserEntity user
            where user.id <> :currentUserId
              and (
                  lower(user.email) like :keyword
                  or lower(user.displayName) like :keyword
              )
            order by user.displayName asc, user.email asc
            """)
    List<UserEntity> searchUsers(UUID currentUserId, String keyword, Pageable pageable);
}
