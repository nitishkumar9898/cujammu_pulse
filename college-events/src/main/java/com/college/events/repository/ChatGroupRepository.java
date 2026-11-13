package com.college.events.repository;

import com.college.events.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from ChatGroup g where g.id = :id")
    Optional<ChatGroup> findForUpdate(@org.springframework.data.repository.query.Param("id") long id);
    @org.springframework.data.jpa.repository.Query("select g.groupType from ChatGroup g where g.id = :id")
    Optional<String> findTypeById(@org.springframework.data.repository.query.Param("id") long id);
    @org.springframework.data.jpa.repository.Query("select g.relatedEventId from ChatGroup g where g.id = :id")
    Optional<Long> findEventIdById(@org.springframework.data.repository.query.Param("id") long id);
    List<ChatGroup> findByGroupTypeOrderByIdAsc(String type, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        select g from ChatGroup g where
          (g.groupType = 'PERMANENT_DEPARTMENT' and g.relatedEventId is null and
            exists (select m.id from ChatMembership m where m.groupId = g.id and m.user.id = :userId and m.active = true))
          or (g.groupType = 'EPHEMERAL_EVENT' and :eventsEnabled = true and
            (exists (select r.id from Registration r where r.event.id = g.relatedEventId and r.user.id = :userId
                 and r.status in (com.college.events.model.RegistrationStatus.REGISTERED, com.college.events.model.RegistrationStatus.ATTENDED))
             or (:canOwn = true and exists (select e.id from Event e where e.id = g.relatedEventId and e.organizer.id = :userId))))
        order by g.id desc
        """)
    org.springframework.data.domain.Slice<ChatGroup> visiblePage(@org.springframework.data.repository.query.Param("userId") long userId,
            @org.springframework.data.repository.query.Param("canOwn") boolean canOwn,
            @org.springframework.data.repository.query.Param("eventsEnabled") boolean eventsEnabled, org.springframework.data.domain.Pageable pageable);
    Optional<ChatGroup> findByRelatedEventId(Long eventId);
    List<ChatGroup> findByGroupType(String groupType);
    List<ChatGroup> findByRelatedDepartment(String department);
    void deleteByRelatedEventId(Long eventId);
}
