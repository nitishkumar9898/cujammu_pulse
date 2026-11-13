package com.college.events.controller;
import com.college.events.dto.ChatMembershipDtos.*;
import com.college.events.service.ChatMembershipService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/cujammu/chat/admin")
public class ChatMembershipApiController {
    private final ChatMembershipService service;
    @GetMapping("/department-groups") public ResponseEntity<DepartmentGroups> groups() { return privateResponse(service.departmentGroups()); }
    @GetMapping("/groups/{groupId}/members") public ResponseEntity<Members> members(@PathVariable long groupId, @RequestParam(defaultValue="0") int page) {
        return privateResponse(service.members(groupId, page));
    }
    @PutMapping("/groups/{groupId}/members/{userId}") public ResponseEntity<Member> change(@PathVariable long groupId, @PathVariable long userId, @Valid @RequestBody Change command) {
        return privateResponse(service.change(groupId, userId, command));
    }
    private static <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
