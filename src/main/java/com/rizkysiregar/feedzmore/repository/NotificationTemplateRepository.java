package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.NotificationTemplateEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, UUID>, JpaSpecificationExecutor<NotificationTemplateEntity> {

    boolean existsByCode(String code);


    // Get by stable code (event-based, OTP, admin)
    Optional<NotificationTemplateEntity> findByCodeAndIsActiveTrue(String code);

    // Get all active templates by category and mode
    List<NotificationTemplateEntity> findAllByCategoryAndModeAndIsActiveTrue(String category, String mode);


    // Random CASUAL static message (scheduler)
    @Query("""
                SELECT t FROM NotificationTemplateEntity t
                WHERE t.category = 'CASUAL'
                  AND t.mode = 'STATIC'
                  AND t.isActive = true
                ORDER BY function('random')
            """)
    List<NotificationTemplateEntity> findRandomCasual(Pageable pageable);
}
