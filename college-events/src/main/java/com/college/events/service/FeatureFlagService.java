package com.college.events.service;

import com.college.events.model.FeatureFlag;
import com.college.events.repository.FeatureFlagRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.college.events.dto.FeatureControlDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final com.college.events.security.DomainAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    private static final Set<String> EVENT_DEPENDENCIES = Set.of("MODULE_TEAMS", "MODULE_JUDGING",
            "MODULE_VOLUNTEERS", "MODULE_TOURNAMENTS", "MODULE_POLLS", "MODULE_DYNAMIC_FORMS", "MODULE_CALENDAR", "MODULE_READINESS");
    /** Business-function switches are catalogued as ordinary flags so existing clients
     * receive them through the same no-store control-plane response. They never grant
     * a role; they only add a deny guard beneath the parent module. */
    private static final Set<String> EVENT_ACTIONS = Set.of(
            "ACTION_EVENT_CANCEL", "ACTION_EVENT_POSTPONE");
    /** These legacy routes accept a purpose string but do not yet verify a facility QR challenge. */
    private static final Set<String> LIBRARY_ACTIONS = Set.of(
            "ACTION_LIBRARY_ENTRY", "ACTION_LIBRARY_EXIT");
    private static final Set<String> LOCKED_FOUNDATION_FLAGS = Set.of(
            "FOUNDATION_AUTH",
            "FOUNDATION_AUDIT",
            "FOUNDATION_ADMIN_RECOVERY",
            "FOUNDATION_MEDIA_RETENTION"
    );

    @PostConstruct
    public void initDefaultFlags() {
        seedFlagIfMissing("FOUNDATION_AUTH", "Authentication & Session Security", "Mandatory login, session, password, and ownership controls", "LOCKED", true);
        seedFlagIfMissing("FOUNDATION_AUDIT", "Audit Logging", "Security and administrative audit trail", "LOCKED", true);
        seedFlagIfMissing("FOUNDATION_ADMIN_RECOVERY", "Admin Recovery", "Protected recovery path for platform administrators", "LOCKED", true);
        seedFlagIfMissing("FOUNDATION_MEDIA_RETENTION", "30-Day Media Retention", "Automatic deletion of uploaded media after retention deadline", "LOCKED", true);

        seedFlagIfMissing("MODULE_EVENTS", "Events", "Core event creation, approval, registration, attendance, and certificates", "EVENTS", true);
        seedFlagIfMissing("MODULE_VENUES", "Venues & Sports Grounds", "Conflict-free venue and ground calendars", "EVENTS", true);
        seedFlagIfMissing("MODULE_LIBRARY_TURNSTILE", "Smart Library Turnstile", "Study vs Book Issue Dual-QR passes and real-time dwell timer", "ACADEMIC", false);
        seedFlagIfMissing("MODULE_HOSTEL_GATEPASS", "Hostel Night Curfew Passes", "Warden approval workflows and automated late entry tracking", "RESIDENTIAL", true);
        seedFlagIfMissing("MODULE_COE_EXAMS", "CoE Exams & Scorecards", "Automated roll-number marksheet distribution and timetables", "ACADEMIC", true);
        seedFlagIfMissing("MODULE_COMMUNITY_CHAT", "Community & Dept Chat", "Institutional chat groups with night silence and slow-mode", "SOCIAL", true);
        seedFlagIfMissing("MODULE_DYNAMIC_FORMS", "Dynamic Event Participation Forms", "Custom event registration forms with verified identity autofill", "EVENTS", true);
        seedFlagIfMissing("MODULE_AI_CHATBOT", "AI Campus Assistant", "Grounding-only campus assistant with data-leakage guardrails", "CORE", true);
        seedFlagIfMissing("MODULE_TEAMS", "Team Registrations", "Invite-code teams and roster locking", "EVENTS", true);
        seedFlagIfMissing("MODULE_JUDGING", "Judging & Leaderboards", "Multi-round judging, rubrics, and live leaderboard", "EVENTS", true);
        seedFlagIfMissing("MODULE_VOLUNTEERS", "Volunteer Coordination", "Volunteer recruitment, scanner permissions, tasks, and hours", "EVENTS", true);
        seedFlagIfMissing("MODULE_LOST_FOUND", "Lost & Found", "Campus lost and found reports and claims", "CAMPUS", true);
        seedFlagIfMissing("MODULE_HELPDESK", "Helpdesk", "Ticketing and grievance resolution", "CAMPUS", true);
        seedFlagIfMissing("MODULE_TOURNAMENTS", "Tournament Brackets", "Fixtures, scoring, and brackets", "SPORTS", true);
        seedFlagIfMissing("MODULE_POLLS", "Audience Polls", "Single-vote audience choice polls", "EVENTS", true);
        seedFlagIfMissing("MODULE_NOTIFICATIONS", "Notifications Inbox", "Private account inbox for campus service updates", "CORE", true);
        seedFlagIfMissing("MODULE_LIVE_SPORTS", "Live Sports Scorecards", "Live ball-by-ball and point-by-point sports scores and commentary", "SPORTS", true);
        seedFlagIfMissing("MODULE_ALUMNI", "Alumni Network & Directory", "Graduated alumni groups, job postings, mentorship, and directory", "ALUMNI", true);
        seedFlagIfMissing("MODULE_SOCIAL_PULSE", "Campus Pulse Social Feed", "Campus-wide social wall with photos, pdfs, comments, and winner showcases", "SOCIAL", true);
        seedFlagIfMissing("MODULE_CALENDAR", "Personal Calendar", "Own events, clashes and approved schedule view", "CORE", false);
        seedFlagIfMissing("MODULE_READINESS", "Event Readiness", "Readiness checks, resource and waitlist guidance", "EVENTS", false);
        seedFlagIfMissing("MODULE_INSIGHTS", "Feedback & Insights", "Governed feedback and operational insights", "CORE", false);
        seedFlagIfMissing("MODULE_DISRUPTIONS", "Campus Disruption Centre", "Verified postponement, cancellation and continuity notices", "CORE", false);
        seedFlagIfMissing("MODULE_CERTIFICATES", "Certificates", "Verified event participation certificates", "ACADEMIC", false);
        seedFlagIfMissing("MODULE_OPERATIONS", "Operations Console", "Protected operational monitoring and reconciliation views", "CORE", false);
        seedFlagIfMissing("ACTION_EVENT_CANCEL", "Cancel events", "Authorized event managers may cancel an approved event and release its reservation", "EVENT_ACTIONS", true);
        seedFlagIfMissing("ACTION_EVENT_POSTPONE", "Postpone events", "Authorized event managers may propose a new event date", "EVENT_ACTIONS", true);
        seedFlagIfMissing("ACTION_CALENDAR_CLASH_CHECK", "Check own event clashes", "Bounded advisory comparison of own confirmed event schedules; missing durations remain unknown", "CALENDAR_ACTIONS", true);
        seedFlagIfMissing("ACTION_LIBRARY_ENTRY", "Library entry", "Unavailable until a supervised facility QR challenge is verified", "LIBRARY_ACTIONS", false);
        seedFlagIfMissing("ACTION_LIBRARY_EXIT", "Library exit", "Unavailable until a supervised facility QR challenge is verified", "LIBRARY_ACTIONS", false);

        log.info("Runtime feature registry initialized");
    }

    private void seedFlagIfMissing(String key, String name, String desc, String cat, boolean defVal) {
        if (!featureFlagRepository.existsByFeatureKey(key)) {
            FeatureFlag flag = FeatureFlag.builder()
                    .featureKey(key)
                    .displayName(name)
                    .description(desc)
                    .category(cat)
                    .enabled(defVal)
                    .updatedBy("SYSTEM_BOOTSTRAP")
                    .build();
            featureFlagRepository.save(flag);
        }
    }

    public boolean isFeatureEnabled(String key) {
        return getAllFlagsMap().getOrDefault(key, false);
    }

    @Transactional
    public FeatureFlag setFeatureEnabled(String key, boolean enabled, String adminUsername) {
        var actor = access.requireRole(com.college.events.model.Role.ADMIN);
        FeatureFlag flag = featureFlagRepository.findByFeatureKeyForUpdate(key)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + key));
        if (LOCKED_FOUNDATION_FLAGS.contains(key) && !enabled) {
            throw new IllegalArgumentException("Locked foundation control cannot be disabled: " + key);
        }
        rejectUnverifiedLibraryScanEnable(key, enabled);

        boolean previous = flag.isEnabled();
        flag.setEnabled(enabled);
        flag.setUpdatedBy(actor.getUsername());
        FeatureFlag saved = featureFlagRepository.save(flag);
        if ("MODULE_NOTIFICATIONS".equals(key) && !enabled) closeNotificationStreamsAfterCommit();
        audit.logAction(actor.getUsername(), actor.getRole().name(), "FEATURE_STATE_CHANGED",
                "feature=" + key + ";previous=" + previous + ";enabled=" + enabled);

        log.warn("Admin id={} changed feature {} enabled={}", actor.getId(), key, enabled);
        return saved;
    }

    /**
     * Control-plane mutation with optimistic intent. The row is locked before the
     * revision check, so an OFF decision serializes with guarded feature mutations.
     */
    @Transactional
    public FeatureFlag setFeatureEnabled(String key, boolean enabled, long expectedRevision, String reason) {
        var actor = access.requireRole(com.college.events.model.Role.ADMIN);
        if(expectedRevision<0||reason==null||reason.isBlank()||reason.length()>300)
            throw new IllegalArgumentException("A current revision and bounded audit reason are required");
        String safeReason=reason.trim().replace('\r',' ').replace('\n',' ');
        FeatureFlag flag = featureFlagRepository.findByFeatureKeyForUpdate(key)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + key));
        if (flag.getRevision() != expectedRevision) {
            throw new IllegalStateException("Feature controls changed; refresh and retry");
        }
        if (LOCKED_FOUNDATION_FLAGS.contains(key) && !enabled) {
            throw new IllegalArgumentException("Locked foundation control cannot be disabled: " + key);
        }
        rejectUnverifiedLibraryScanEnable(key, enabled);
        boolean previous = flag.isEnabled();
        flag.setEnabled(enabled);
        flag.setUpdatedBy(actor.getUsername());
        FeatureFlag saved = featureFlagRepository.save(flag);
        if ("MODULE_NOTIFICATIONS".equals(key) && !enabled) closeNotificationStreamsAfterCommit();
        audit.logAction(actor.getUsername(), actor.getRole().name(), "FEATURE_STATE_CHANGED",
                "feature=" + key + ";previous=" + previous + ";enabled=" + enabled + ";reason=" + safeReason);
        log.warn("Admin id={} changed feature {} enabled={}", actor.getId(), key, enabled);
        return saved;
    }

    public FeatureControlDtos.Catalogue catalogue() {
        access.requireRole(com.college.events.model.Role.ADMIN);
        Map<String, Boolean> effective = getAllFlagsMap();
        return new FeatureControlDtos.Catalogue(getAllFlags().stream().map(flag -> view(flag, effective)).toList());
    }

    public FeatureControlDtos.Item view(FeatureFlag flag) {
        return view(flag, getAllFlagsMap());
    }

    private FeatureControlDtos.Item view(FeatureFlag flag, Map<String, Boolean> effective) {
        return new FeatureControlDtos.Item(flag.getFeatureKey(), flag.getDisplayName(), flag.getDescription(),
                flag.getCategory(), flag.isEnabled(), effective.getOrDefault(flag.getFeatureKey(), false),
                LOCKED_FOUNDATION_FLAGS.contains(flag.getFeatureKey()), flag.getRevision(), flag.getUpdatedAt(), flag.getUpdatedBy());
    }

    /** A rolled-back toggle must not revoke streams while the committed flag is still enabled. */
    private void closeNotificationStreamsAfterCommit() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            notifications.closeAllStreams();
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { notifications.closeAllStreams(); }
                });
    }

    public List<FeatureFlag> getAllFlags() {
        return featureFlagRepository.findAllByOrderByCategoryAscDisplayNameAsc();
    }

    public Map<String, Boolean> getAllFlagsMap() {
        Map<String, Boolean> effective = featureFlagRepository.findAll().stream().collect(java.util.stream.Collectors.toMap(
                FeatureFlag::getFeatureKey, FeatureFlag::isEnabled));
        if (!effective.getOrDefault("MODULE_EVENTS", false)) {
            EVENT_DEPENDENCIES.forEach(key -> effective.computeIfPresent(key, (unused, value) -> false));
            EVENT_ACTIONS.forEach(key -> effective.computeIfPresent(key, (unused, value) -> false));
        }
        if (!effective.getOrDefault("MODULE_LIBRARY_TURNSTILE", false)) {
            LIBRARY_ACTIONS.forEach(key -> effective.computeIfPresent(key, (unused, value) -> false));
        }
        LIBRARY_ACTIONS.forEach(key -> effective.computeIfPresent(key, (unused, value) -> false));
        if (!effective.getOrDefault("MODULE_CALENDAR", false)) {
            effective.computeIfPresent("ACTION_CALENDAR_CLASH_CHECK", (unused, value) -> false);
        }
        return Map.copyOf(effective);
    }

    public boolean isEnabled(String key) {
        return isFeatureEnabled(key);
    }

    /** Serialize a protected mutation against an administrator disabling its dependency. */
    @Transactional
    public void requireEnabledForUpdate(String key) {
        lockEnabledFeature(key);
    }

    /** Locks a parent module before its child so disabling the parent contends with every child mutation. */
    @Transactional
    public void requireEnabledForUpdate(String key, String parentKey) {
        if (key.equals(parentKey)) throw new IllegalArgumentException("Feature parent must be distinct");
        lockEnabledFeature(parentKey);
        lockEnabledFeature(key);
    }

    private void lockEnabledFeature(String key) {
        if (LIBRARY_ACTIONS.contains(key)) {
            throw new IllegalStateException("LIBRARY_VERIFICATION_UNAVAILABLE: facility QR challenge verification is not configured");
        }
        FeatureFlag flag = featureFlagRepository.findByFeatureKeyForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Required feature is unavailable"));
        if (!flag.isEnabled()) throw new IllegalStateException("Required feature is disabled");
    }

    /** Read-path guard deliberately avoids a pessimistic write lock. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void requireEnabledForRead(String key) {
        if (!isFeatureEnabled(key)) throw new IllegalStateException("Required feature is disabled");
    }

    public Map<String, Boolean> getAllFlagStatuses() {
        return getAllFlagsMap();
    }

    @Transactional
    public FeatureFlag setFeatureEnabled(String key, boolean enabled) {
        return setFeatureEnabled(key, enabled, "ADMIN");
    }

    private static void rejectUnverifiedLibraryScanEnable(String key, boolean enabled) {
        if (enabled && LIBRARY_ACTIONS.contains(key)) {
            throw new IllegalArgumentException("LIBRARY_VERIFICATION_UNAVAILABLE: facility QR challenge verification is not configured");
        }
    }
}
