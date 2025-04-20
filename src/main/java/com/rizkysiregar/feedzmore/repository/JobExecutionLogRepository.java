package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, String> {
}
