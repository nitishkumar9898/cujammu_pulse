package com.college.events.controller;

import com.college.events.dto.LostFoundDtos.*;
import com.college.events.service.LostAndFoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/cujammu/lost-and-found")
public class LostFoundApiController {
    private final LostAndFoundService service;
    @GetMapping public ResponseEntity<Page> list(@RequestParam(required=false) String category,@RequestParam(defaultValue="0") int page) {return privateResponse(service.list(category,page));}
    @PostMapping public ResponseEntity<Item> report(@Valid @RequestBody Report command) {return privateResponse(service.report(command));}
    @PostMapping("/{id}/claim") public ResponseEntity<ClaimView> claim(@PathVariable long id,@Valid @RequestBody Claim command) {return privateResponse(service.claim(id,command));}
    @GetMapping("/{id}/claims") public ResponseEntity<java.util.List<ClaimView>> claims(@PathVariable long id,@RequestParam(defaultValue="0") int page) {return privateResponse(service.claims(id,page));}
    @PostMapping("/{id}/hand-over") public ResponseEntity<Item> handover(@PathVariable long id,@Valid @RequestBody Handover command) {return privateResponse(service.handover(id,command));}
    private static <T> ResponseEntity<T> privateResponse(T body) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
