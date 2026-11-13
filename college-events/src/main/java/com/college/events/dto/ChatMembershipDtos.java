package com.college.events.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public final class ChatMembershipDtos {
    private ChatMembershipDtos() { }
    public record Change(@Min(0) Long expectedRevision, boolean active,
                         @Size(max=120) String sourceReference, @NotBlank @Size(min=3,max=500) String reason) { }
    public record Member(long userId, String name, boolean active, long revision) { }
    public record Members(List<Member> items, int page, boolean hasMore) { }
    public record DepartmentGroup(long id, String name, String department) { }
    public record DepartmentGroups(List<DepartmentGroup> items) { }
}
