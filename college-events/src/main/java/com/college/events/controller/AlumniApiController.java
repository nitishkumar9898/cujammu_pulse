package com.college.events.controller;

import com.college.events.dto.AlumniDtos.*;
import com.college.events.service.AlumniNetworkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cujammu/alumni")
@RequiredArgsConstructor
public class AlumniApiController {
    private final AlumniNetworkService alumni;

    @GetMapping public PageResult<DirectoryEntry> directory(@RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page) { return alumni.directory(department, false, page); }
    @GetMapping("/mentors") public PageResult<DirectoryEntry> mentors(@RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page) { return alumni.directory(department, true, page); }
    @GetMapping("/profile") public org.springframework.http.ResponseEntity<Profile> own() {
        Profile profile = alumni.ownProfile();
        return profile == null ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(profile);
    }
    @GetMapping("/profiles/{id}") public Profile profile(@PathVariable Long id) { return alumni.privateProfile(id); }
    @PostMapping("/profile") public Profile update(@Valid @RequestBody ProfileUpdate command) {
        return alumni.updateProfile(command);
    }
    @GetMapping("/review") public PageResult<Profile> review(@RequestParam(defaultValue = "0") int page) {
        return alumni.pendingReview(page);
    }
    @PutMapping("/profiles/{id}/verification") public Profile verify(@PathVariable Long id,
            @Valid @RequestBody Verification command) { return alumni.verifyProfile(id, command); }
    @GetMapping("/jobs") public PageResult<Job> jobs(@RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page) { return alumni.activeJobs(department, page); }
    @GetMapping("/jobs/mine") public PageResult<Job> ownJobs(@RequestParam(defaultValue = "0") int page) {
        return alumni.ownJobs(page);
    }
    @PostMapping("/jobs") public Job postJob(@Valid @RequestBody JobUpdate command) { return alumni.postJob(command); }
    @PutMapping("/jobs/{id}/state") public Job jobState(@PathVariable Long id, @Valid @RequestBody JobState command) {
        return alumni.changeJobState(id, command);
    }
}
