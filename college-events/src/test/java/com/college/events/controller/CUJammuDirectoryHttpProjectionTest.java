package com.college.events.controller;

import com.college.events.dto.DepartmentViews;
import com.college.events.dto.FacilityViews;
import com.college.events.service.CUJammuMasterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CUJammuDirectoryHttpProjectionTest {
    private CUJammuMasterService master;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<?> constructor = CUJammuApiController.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                .map(type -> mock(type))
                .toArray();
        master = (CUJammuMasterService) arguments[
                Arrays.asList(constructor.getParameterTypes()).indexOf(CUJammuMasterService.class)];
        mvc = MockMvcBuilders.standaloneSetup((CUJammuApiController) constructor.newInstance(arguments)).build();
    }

    @Test
    void departmentGetAndCreateResponsesExposeOnlyDirectoryFields() throws Exception {
        var item = new DepartmentViews.DirectoryItem(1, "SYNTH", "Synthetic School", "Synthetic Hall");
        when(master.getAllActiveDepartments()).thenReturn(java.util.List.of(item));
        when(master.createDepartment("SYNTH", "Synthetic School", "Synthetic Hall", "Private HOD"))
                .thenReturn(item);

        mvc.perform(get("/api/v1/cujammu/departments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":1,\"code\":\"SYNTH\",\"name\":\"Synthetic School\",\"building\":\"Synthetic Hall\"}]", true));

        mvc.perform(post("/api/v1/cujammu/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SYNTH\",\"name\":\"Synthetic School\",\"building\":\"Synthetic Hall\",\"hod\":\"Private HOD\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":1,\"code\":\"SYNTH\",\"name\":\"Synthetic School\",\"building\":\"Synthetic Hall\"}", true));
        verify(master).createDepartment("SYNTH", "Synthetic School", "Synthetic Hall", "Private HOD");
    }

    @Test
    void facilityGetResponseExcludesOperationalEntityFields() throws Exception {
        when(master.getAllActiveFacilities()).thenReturn(java.util.List.of(
                new FacilityViews.CatalogItem(4, "Synthetic Hall", "AUDITORIUM", "Synthetic Campus", 250)));

        mvc.perform(get("/api/v1/cujammu/facilities"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":4,\"name\":\"Synthetic Hall\",\"facilityType\":\"AUDITORIUM\",\"location\":\"Synthetic Campus\",\"capacity\":250}]", true));
    }
}
