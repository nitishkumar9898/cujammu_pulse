package com.college.events.controller;

import com.college.events.dto.ReadinessTaskDtos;
import com.college.events.service.ReadinessTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cujammu/readiness/events/{eventId}/tasks")
@RequiredArgsConstructor
public class ReadinessTaskApiController {
    private final ReadinessTaskService tasks;

    @GetMapping
    public ResponseEntity<ReadinessTaskDtos.ListView> list(@PathVariable long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.list(eventId));
    }

    @GetMapping("/owners")
    public ResponseEntity<java.util.List<ReadinessTaskDtos.Owner>> owners(@PathVariable long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.owners(eventId));
    }

    @PostMapping
    public ResponseEntity<ReadinessTaskDtos.Item> create(@PathVariable long eventId,
            @Valid @RequestBody ReadinessTaskDtos.Create command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.create(eventId, command));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ReadinessTaskDtos.Item> update(@PathVariable long eventId, @PathVariable long taskId,
            @Valid @RequestBody ReadinessTaskDtos.Update command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.update(eventId, taskId, command));
    }
}
