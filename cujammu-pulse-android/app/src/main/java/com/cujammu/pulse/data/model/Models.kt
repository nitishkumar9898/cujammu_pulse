package com.cujammu.pulse.data.model

data class DepartmentDto(
    val id: Long,
    val code: String,
    val name: String,
    val headOfDepartment: String?,
    val contactEmail: String?,
    val building: String?,
    val active: Boolean
)

data class HostelDto(
    val id: Long,
    val name: String,
    val genderType: String,
    val wardenName: String?,
    val wardenPhone: String?,
    val curfewTime: String,
    val capacity: Int,
    val active: Boolean
)

data class FacilityDto(
    val id: Long,
    val name: String,
    val facilityType: String,
    val location: String?,
    val capacity: Int?,
    val hasProjector: Boolean,
    val hasSoundSystem: Boolean,
    val hasStageLighting: Boolean,
    val active: Boolean
)

data class EventDto(
    val id: Long,
    val title: String,
    val description: String,
    val venue: String,
    val eventDate: String,
    val maxSeats: Int,
    val fees: Double,
    val bannerImage: String?,
    val status: String,
    val targetDepartment: String?,
    val eventScope: String?,
    val attendanceMode: String?
)

data class LibraryGateLogDto(
    val id: Long,
    val sessionType: String, // STUDY, BOOK_ISSUE_RETURN
    val entryTime: String,
    val exitTime: String?,
    val durationFormatted: String?,
    val status: String, // ACTIVE, COMPLETED, AUTO_CLOSED_UNSCANNED
    val logDate: String,
    val academicLevel: String?,
    val departmentName: String?,
    val rollNumberOrEmpId: String?
)

data class ScoreCardDto(
    val id: Long,
    val rollNumber: String,
    val studentName: String,
    val departmentName: String,
    val programName: String?,
    val semester: String,
    val academicYear: String,
    val sgpa: Double?,
    val cgpa: Double?,
    val resultStatus: String,
    val scoreCardPdfUrl: String?,
    val pushedAt: String?
)

data class ChatGroupDto(
    val id: Long,
    val name: String,
    val groupType: String,
    val relatedEventId: Long?,
    val relatedDepartment: String?,
    val onlyAdminsCanPost: Boolean,
    val nightSilenceEnabled: Boolean
)

data class ChatMessageDto(
    val id: Long,
    val senderName: String,
    val senderRole: String,
    val content: String,
    val mediaUrl: String?,
    val mediaType: String,
    val createdAt: String
)

data class HostelGatePassDto(
    val id: Long,
    val passType: String,
    val destination: String,
    val reason: String,
    val expectedExitTime: String,
    val expectedReturnTime: String,
    val status: String,
    val qrToken: String?
)

data class TeamDto(
    val id: Long,
    val name: String,
    val inviteCode: String,
    val minSize: Int,
    val maxSize: Int,
    val locked: Boolean
)

data class TeamMemberDto(
    val id: Long,
    val role: String,
    val status: String,
    val joinedAt: String?
)

data class JudgeScoreDto(
    val id: Long,
    val eventId: Long,
    val participantId: Long,
    val participantName: String,
    val roundNumber: Int,
    val innovationScore: Double,
    val technicalDepth: Double,
    val executionScore: Double,
    val presentationScore: Double,
    val totalScore: Double,
    val feedbackNotes: String?
)

data class LeaderboardEntryDto(
    val rank: Int,
    val participantId: Long,
    val participantName: String,
    val averageScore: Double
)

data class VolunteerTaskDto(
    val id: Long,
    val eventId: Long,
    val title: String,
    val description: String?,
    val category: String,
    val assignedUserName: String?,
    val status: String,
    val hoursLogged: Double,
    val certificateAwarded: Boolean
)

data class LostAndFoundItemDto(
    val id: Long,
    val title: String,
    val category: String,
    val description: String,
    val locationFound: String,
    val reportedByName: String?,
    val reportedByPhone: String?,
    val status: String,
    val claimantName: String?,
    val imageUrl: String?,
    val reportedAt: String?,
    val imageUrls: List<String> = emptyList()
)

data class SupportTicketDto(
    val id: Long,
    val ticketNumber: String,
    val subject: String,
    val description: String,
    val category: String,
    val priority: String,
    val status: String,
    val createdByName: String?,
    val assignedTo: String?,
    val resolutionNotes: String?,
    val createdAt: String?,
    val resolvedAt: String?
)

/** Wire contract for the authenticated helpdesk API. Kept separate from legacy demo UI models. */
data class HelpdeskTicketDto(
    val id: String,
    val version: Long,
    val ticketNumber: String,
    val subject: String,
    val description: String,
    val category: String,
    val priority: String,
    val requestedPriority: String,
    val status: String,
    val assignedOffice: String?,
    val assignedStaffId: Long?,
    val escalated: Boolean,
    val resolutionNotes: String?,
    val createdAt: String?,
    val resolvedAt: String?,
    val canManage: Boolean,
    val canReopen: Boolean
)

data class HelpdeskPageDto<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean
)

data class CreateHelpdeskTicketRequest(
    val requestKey: String,
    val subject: String,
    val description: String,
    val category: String,
    val requestedPriority: String
)

data class MatchFixtureDto(
    val id: Long,
    val eventId: Long,
    val tournamentName: String?,
    val roundNumber: Int,
    val roundName: String?,
    val matchNumber: Int,
    val team1Name: String,
    val team2Name: String,
    val scoreTeam1: String,
    val scoreTeam2: String,
    val winnerName: String?,
    val status: String,
    val matchTime: String?,
    val courtOrVenue: String?
)

data class EventPollDto(
    val id: Long,
    val eventId: Long,
    val question: String,
    val options: String,
    val active: Boolean
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)
