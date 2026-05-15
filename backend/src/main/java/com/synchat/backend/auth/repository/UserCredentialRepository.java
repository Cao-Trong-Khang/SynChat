package com.synchat.backend.auth.repository;

import com.synchat.backend.auth.entity.UserCredentialEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredentialEntity, UUID> {
}
