package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.AdminAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, UUID>, JpaSpecificationExecutor<AdminAuditLogEntity> {
}
