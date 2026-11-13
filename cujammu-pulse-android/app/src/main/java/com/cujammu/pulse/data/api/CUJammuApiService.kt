package com.cujammu.pulse.data.api

import com.cujammu.pulse.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface CUJammuApiService {

    // 1. Departments
    @GET("api/v1/cujammu/departments")
    suspend fun getDepartments(): Response<List<DepartmentDto>>

    // 2. Hostels & Gate Pass
    @GET("api/v1/cujammu/hostels")
    suspend fun getHostels(): Response<List<HostelDto>>

    @POST("api/v1/cujammu/hostels/gate-pass/apply")
    suspend fun applyGatePass(@Body body: Map<String, String>): Response<HostelGatePassDto>

    @POST("api/v1/cujammu/hostels/gate-pass/scan")
    suspend fun scanGatePass(@Body body: Map<String, Any>): Response<HostelGatePassDto>

    // 3. Venues & Conflict Calendar
    @GET("api/v1/cujammu/facilities")
    suspend fun getFacilities(): Response<List<FacilityDto>>

    @GET("api/v1/cujammu/facilities/{id}/conflicts")
    suspend fun checkConflicts(
        @Path("id") facilityId: Long,
        @Query("startTime") startTime: String,
        @Query("endTime") endTime: String
    ): Response<Map<String, Any>>

    // 4. Library Digital Turnstile
    @POST("api/v1/cujammu/library/scan/entry")
    suspend fun libraryScanEntry(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/cujammu/library/scan/exit")
    suspend fun libraryScanExit(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/cujammu/library/active")
    suspend fun getActiveLibraryOccupants(): Response<List<LibraryGateLogDto>>

    // 5. Dual-QR Event Attendance
    @POST("api/v1/cujammu/events/{id}/attendance/entry")
    suspend fun recordEventEntry(
        @Path("id") eventId: Long,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("api/v1/cujammu/events/{id}/attendance/exit")
    suspend fun recordEventExit(
        @Path("id") eventId: Long,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // 6. CoE Exam Score Cards & Schedules
    @GET("api/v1/cujammu/exams/scorecards/my")
    suspend fun getMyScoreCards(): Response<List<ScoreCardDto>>

    // 7. Community & Event Chat
    @GET("api/v1/cujammu/chat/groups")
    suspend fun getChatGroups(): Response<List<ChatGroupDto>>

    @GET("api/v1/cujammu/chat/groups/{id}/messages")
    suspend fun getChatMessages(@Path("id") groupId: Long): Response<List<ChatMessageDto>>

    @POST("api/v1/cujammu/chat/groups/{id}/messages")
    suspend fun postChatMessage(
        @Path("id") groupId: Long,
        @Body body: Map<String, String>
    ): Response<ChatMessageDto>

    // 8. Teams
    @POST("api/v1/cujammu/events/{id}/teams/create")
    suspend fun createTeam(@Path("id") eventId: Long, @Body body: Map<String, Any>): Response<TeamDto>

    @POST("api/v1/cujammu/teams/join")
    suspend fun joinTeam(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/cujammu/events/{id}/teams")
    suspend fun getEventTeams(@Path("id") eventId: Long): Response<List<TeamDto>>

    // 9. Judging & Leaderboards
    @POST("api/v1/cujammu/events/{id}/judging/score")
    suspend fun submitJudgeScore(@Path("id") eventId: Long, @Body body: Map<String, Any>): Response<JudgeScoreDto>

    @GET("api/v1/cujammu/events/{id}/judging/round/{round}/leaderboard")
    suspend fun getRoundLeaderboard(@Path("id") eventId: Long, @Path("round") round: Int): Response<List<LeaderboardEntryDto>>

    // 11. Volunteers
    @GET("api/v1/cujammu/events/{id}/volunteers/tasks")
    suspend fun getEventVolunteerTasks(@Path("id") eventId: Long): Response<List<VolunteerTaskDto>>

    @GET("api/v1/cujammu/volunteers/my-tasks")
    suspend fun getMyVolunteerTasks(): Response<List<VolunteerTaskDto>>

    // 12. Lost & Found
    @GET("api/v1/cujammu/lost-and-found")
    suspend fun getLostAndFoundItems(@Query("category") category: String? = null): Response<List<LostAndFoundItemDto>>

    @POST("api/v1/cujammu/lost-and-found")
    suspend fun reportLostItem(@Body body: Map<String, String>): Response<LostAndFoundItemDto>

    @POST("api/v1/cujammu/lost-and-found/{id}/claim")
    suspend fun claimLostItem(@Path("id") itemId: Long, @Body body: Map<String, String>): Response<LostAndFoundItemDto>

    // 13. Helpdesk
    @GET("api/v1/cujammu/helpdesk/my-tickets")
    suspend fun getMyTickets(@Query("page") page: Int = 0): Response<HelpdeskPageDto<HelpdeskTicketDto>>

    @POST("api/v1/cujammu/helpdesk/tickets")
    suspend fun createTicket(@Body body: CreateHelpdeskTicketRequest): Response<HelpdeskTicketDto>

    // 14. Tournaments
    @GET("api/v1/cujammu/events/{id}/tournaments/fixtures")
    suspend fun getEventFixtures(@Path("id") eventId: Long): Response<List<MatchFixtureDto>>

    // 15. Audience Polls
    @GET("api/v1/cujammu/events/{id}/polls/active")
    suspend fun getActivePolls(@Path("id") eventId: Long): Response<List<EventPollDto>>

    @POST("api/v1/cujammu/polls/{id}/vote")
    suspend fun castVote(@Path("id") pollId: Long, @Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/v1/cujammu/polls/{id}/results")
    suspend fun getPollResults(@Path("id") pollId: Long): Response<Map<String, Any>>
}
