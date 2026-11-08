package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import com.rizkysiregar.feedzmore.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportClosingServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportClosingService service;

    @Test
    void closeOpenReports_closesWithAnAutomaticNoteThatNamesTheAction() {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(reportRepository.closeOpenForTarget(eq(ReportTargetType.POST), eq(targetId), eq(adminId), any(),
                eq(ReportAction.TAKEDOWN), eq("Ditutup otomatis: target ditindak langsung oleh admin (TAKEDOWN)."))).thenReturn(4);

        assertEquals(4, service.closeOpenReports(ReportTargetType.POST, targetId, adminId, ReportAction.TAKEDOWN));
    }

    @Test
    void closeOpenReportsOfCommentsOnPost_usesTheSameNote() {
        UUID postId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(reportRepository.closeOpenForCommentsOfPost(eq(postId), eq(adminId), any(),
                eq(ReportAction.HARD_DELETE), eq("Ditutup otomatis: target ditindak langsung oleh admin (HARD_DELETE)."))).thenReturn(2);

        assertEquals(2, service.closeOpenReportsOfCommentsOnPost(postId, adminId, ReportAction.HARD_DELETE));
        verify(reportRepository).closeOpenForCommentsOfPost(eq(postId), eq(adminId), any(), eq(ReportAction.HARD_DELETE), any());
    }
}
