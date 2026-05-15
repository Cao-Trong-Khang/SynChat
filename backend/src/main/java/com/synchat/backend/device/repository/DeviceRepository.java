package com.synchat.backend.device.repository;

import com.synchat.backend.device.entity.DeviceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    List<DeviceEntity> findByUserId(UUID userId);
}
