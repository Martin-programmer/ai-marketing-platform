package com.amp.config;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {

    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findByCategoryOrderBySettingKey(String category);

    List<SystemSetting> findAllByOrderByCategoryAscSettingKeyAsc();
}
