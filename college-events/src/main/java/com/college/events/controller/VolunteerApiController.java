package com.college.events.controller;

import com.college.events.dto.VolunteerDtos;
import com.college.events.model.VolunteerTask;
import com.college.events.security.DomainAccess;
import com.college.events.service.VolunteerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cujammu/volunteers")
@RequiredArgsConstructor
public class VolunteerApiController {
    private final VolunteerService volunteers;
    private final DomainAccess access;
    @GetMapping("/mine") public List<VolunteerDtos.TaskView> mine(@RequestParam(defaultValue="0") int page) { return volunteers.getTasksForUser(access.currentUser().getId(),page); }
    @GetMapping("/events/{eventId}/tasks") public List<VolunteerDtos.TaskView> eventTasks(@PathVariable long eventId,@RequestParam(defaultValue="0") int page) { return volunteers.getTasksForEvent(eventId,page); }
    @GetMapping("/events/{eventId}/eligible") public List<VolunteerDtos.EligibleVolunteer> eligible(@PathVariable long eventId,@RequestParam(defaultValue="0") int page) { return volunteers.eligible(eventId,page); }
    @PostMapping("/events/{eventId}/tasks") public VolunteerDtos.TaskView create(@PathVariable long eventId, @Valid @RequestBody VolunteerDtos.CreateTask command) { return volunteers.createTask(eventId,command); }
    @PutMapping("/tasks/{taskId}/assign") public VolunteerDtos.TaskView assign(@PathVariable long taskId, @Valid @RequestBody VolunteerDtos.Assign command) { return volunteers.assignVolunteer(taskId,command); }
    @PostMapping("/tasks/{taskId}/complete") public VolunteerDtos.TaskView complete(@PathVariable long taskId, @Valid @RequestBody VolunteerDtos.Complete command) { return volunteers.completeTask(taskId,command); }
    @PostMapping("/tasks/{taskId}/approve") public VolunteerDtos.TaskView approve(@PathVariable long taskId,@Valid @RequestBody VolunteerDtos.Review command) { return volunteers.approveCompletion(taskId,command); }
}
