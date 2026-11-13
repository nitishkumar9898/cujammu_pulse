package com.college.events.service;

import com.college.events.model.Department;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepartmentDirectoryProjectionTest {
    @Test
    void publicDepartmentDirectoryNeverSerializesHeadContactOrCreationMetadata() throws Exception {
        DepartmentRepository departments = mock(DepartmentRepository.class);
        Department entity = Department.builder().id(1L).code("SYNTH").name("Synthetic School")
                .headOfDepartment("Private HOD").contactEmail("private@example.invalid")
                .phone("0000000000").building("Synthetic Hall").active(true).build();
        when(departments.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(entity));
        CUJammuMasterService service = new CUJammuMasterService(departments, mock(HostelRepository.class),
                mock(HostelGatePassRepository.class), mock(FacilityRepository.class),
                mock(FacilityBookingRepository.class), mock(DomainAccess.class), mock(AuditService.class),
                mock(NotificationService.class));

        String json = new ObjectMapper().writeValueAsString(service.getAllActiveDepartments());

        assertTrue(json.contains("Synthetic School"));
        assertTrue(json.contains("Synthetic Hall"));
        assertFalse(json.contains("Private HOD"));
        assertFalse(json.contains("private@example.invalid"));
        assertFalse(json.contains("0000000000"));
        assertFalse(json.contains("createdAt"));
    }
}
