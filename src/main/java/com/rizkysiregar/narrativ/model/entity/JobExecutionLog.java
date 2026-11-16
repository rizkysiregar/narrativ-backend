package com.rizkysiregar.narrativ.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_execution_log")
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class JobExecutionLog {

    @Id
    @Column(name = "job_name")
    private String jobName;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;
}