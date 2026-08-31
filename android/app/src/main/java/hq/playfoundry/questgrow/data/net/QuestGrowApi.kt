package hq.playfoundry.questgrow.data.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the QuestGrow API. Paths are `v1/`-prefixed — the
 * native client pins the versioned surface (Phase F / grant section 11). Every
 * method returns a Retrofit `Response` so the repository layer can map the
 * status code and the structured error `code` into `core.ApiResult`.
 */
interface QuestGrowApi {

    // ---- auth (no bearer) ----
    @POST("v1/auth/signup") suspend fun signup(@Body body: SignupBody): Response<SignupResp>
    @POST("v1/auth/login") suspend fun login(@Body body: LoginBody): Response<LoginResp>
    @POST("v1/auth/unlock") suspend fun unlock(@Body body: UnlockBody): Response<UnlockResp>
    @POST("v1/auth/child-token") suspend fun childToken(@Body body: ChildTokenBody): Response<ChildTokenResp>
    @POST("v1/auth/pairing-code") suspend fun pairingCode(@Body body: ChildTokenBody): Response<PairCodeResp>
    @POST("v1/auth/pair") suspend fun pair(@Body body: PairBody): Response<ChildTokenResp>

    @GET("health") suspend fun health(): Response<HealthResp>

    // ---- parent: children / family ----
    @GET("v1/children") suspend fun listChildren(): Response<List<ChildOut>>
    @GET("v1/children/{id}") suspend fun getChild(@Path("id") id: String): Response<ChildOut>
    @POST("v1/children") suspend fun addChild(@Body body: ChildBody): Response<ChildOut>
    @PATCH("v1/children/{id}") suspend fun editChild(@Path("id") id: String, @Body body: ChildProfileBody): Response<ChildOut>

    // ---- parent: quests / rewards ----
    @GET("v1/quests") suspend fun listQuests(): Response<List<QuestOut>>
    @POST("v1/quests") suspend fun createQuest(@Body body: QuestBody): Response<QuestOut>
    @PATCH("v1/quests/{id}") suspend fun editQuest(@Path("id") id: String, @Body body: QuestEditBody): Response<QuestOut>
    @PUT("v1/quests/{id}/schedule") suspend fun setSchedule(@Path("id") id: String, @Body body: ScheduleBody): Response<OkResp>
    @GET("v1/rewards") suspend fun listRewards(): Response<List<RewardOut>>
    @POST("v1/rewards") suspend fun createReward(@Body body: RewardBody): Response<RewardOut>
    @PATCH("v1/rewards/{id}") suspend fun editReward(@Path("id") id: String, @Body body: RewardEditBody): Response<RewardOut>

    // ---- parent: assignment / ownership ----
    @POST("v1/children/{id}/quests") suspend fun assignQuest(@Path("id") id: String, @Body body: AssignBody): Response<Map<String, String>>
    @PUT("v1/children/{cid}/quests/{qid}/ownership") suspend fun setOwnership(
        @Path("cid") childId: String, @Path("qid") questId: String, @Body body: OwnershipBody,
    ): Response<OwnershipPlanOut>
    @GET("v1/children/{id}/suggestions") suspend fun suggestions(@Path("id") id: String): Response<List<SuggestionOut>>
    @POST("v1/children/{cid}/quests/{qid}/suggestion/accept") suspend fun acceptSuggestion(
        @Path("cid") childId: String, @Path("qid") questId: String,
    ): Response<OwnershipPlanOut>
    @POST("v1/children/{cid}/quests/{qid}/suggestion/dismiss") suspend fun dismissSuggestion(
        @Path("cid") childId: String, @Path("qid") questId: String,
        @Query("permanent") permanent: Boolean = false,
    ): Response<OkResp>

    // ---- parent: verification / review / ledger ----
    @GET("v1/children/{id}/approvals") suspend fun approvals(@Path("id") id: String): Response<List<ApprovalOut>>
    @POST("v1/children/{cid}/quests/{qid}/approve") suspend fun approve(
        @Path("cid") childId: String, @Path("qid") questId: String, @Body body: DayBody,
    ): Response<Map<String, String>>
    @POST("v1/children/{cid}/quests/{qid}/not-yet") suspend fun notYet(
        @Path("cid") childId: String, @Path("qid") questId: String, @Body body: NotYetBody,
    ): Response<Map<String, String>>
    @POST("v1/children/{cid}/quests/{qid}/record") suspend fun record(
        @Path("cid") childId: String, @Path("qid") questId: String, @Body body: DayBody,
    ): Response<Map<String, String>>
    @POST("v1/children/{id}/reviews") suspend fun createReview(@Path("id") id: String, @Body body: ReviewBody): Response<Map<String, String>>
    @POST("v1/children/{id}/adjustments") suspend fun adjustment(@Path("id") id: String, @Body body: AdjustmentBody): Response<Map<String, Int>>
    @GET("v1/children/{id}/dashboard") suspend fun dashboard(
        @Path("id") id: String, @Query("day") day: String, @Query("week_start") weekStart: String? = null,
    ): Response<DashboardOut>
    @GET("v1/children/{id}/notifications") suspend fun notifications(
        @Path("id") id: String, @Query("since") since: String? = null,
    ): Response<List<NotificationDto>>
    @PUT("v1/account/notifications") suspend fun setNotifications(@Body body: NotificationsPrefBody): Response<Map<String, Boolean>>

    // ---- parent: clock (dev/admin) ----
    @POST("v1/clock/materialise") suspend fun materialise(@Body body: DayBody): Response<Map<String, Int>>

    // ---- rewards redemptions (parent) ----
    @GET("v1/redemptions") suspend fun redemptions(): Response<List<PendingRedemptionOut>>
    @POST("v1/redemptions/{id}/grant") suspend fun grantRedemption(@Path("id") id: String): Response<RedemptionOut>
    @POST("v1/redemptions/{id}/decline") suspend fun declineRedemption(@Path("id") id: String): Response<RedemptionOut>

    // ---- child: own surface only ----
    @GET("v1/me/today") suspend fun today(@Query("day") day: String): Response<TodayDto>
    @POST("v1/me/quests/{qid}/complete") suspend fun complete(
        @Path("qid") questId: String, @Body body: NotYetBody,
    ): Response<CompletionOut>
    @GET("v1/me/rewards") suspend fun meRewards(): Response<MeRewardsOut>
    @POST("v1/me/rewards/{rid}/redeem") suspend fun redeem(@Path("rid") rewardId: String): Response<RedemptionOut>
    @GET("v1/me/celebrations") suspend fun celebrations(@Query("since") since: String? = null): Response<List<CelebrationDto>>
    @GET("v1/me/progress") suspend fun progress(@Query("week_start") weekStart: String): Response<ProgressDto>
}
