package com.gameswap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameswap.common.ratelimit.RedisStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GameSwapIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RedisStoreService redisStoreService;

    private static final UUID INDIRANAGAR_ID = UUID.fromString("11111111-1111-1111-1111-000000000001");
    private static final UUID KORAMANGALA_ID = UUID.fromString("11111111-1111-1111-1111-000000000002");
    private static final UUID LXG_MEETUP_ID = UUID.fromString("22222222-2222-2222-2222-000000000001");

    private static final UUID GAME_ELDEN_RING = UUID.fromString("33333333-3333-3333-3333-000000000001");
    private static final UUID GAME_GOW_RAGNAROK = UUID.fromString("33333333-3333-3333-3333-000000000002");
    private static final UUID GAME_SPIDERMAN_2 = UUID.fromString("33333333-3333-3333-3333-000000000003");
    private static final UUID GAME_GHOST_TSUSHIMA = UUID.fromString("33333333-3333-3333-3333-000000000004");

    @BeforeEach
    void clearRateLimitStore() {
        redisStoreService.clearAll();
        jdbcClient.sql("DELETE FROM chat_messages WHERE sender_id::text NOT LIKE '44444444-%'").update();
        jdbcClient.sql("DELETE FROM chat_threads WHERE user_a_id::text NOT LIKE '44444444-%' OR user_b_id::text NOT LIKE '44444444-%'").update();
        jdbcClient.sql("DELETE FROM users WHERE id::text NOT LIKE '44444444-%'").update();
    }

    @Test
    @DisplayName("1. Auth Flow: OTP request, verify, JWT refresh rotation, OTP rate limit, and onboarding privacy")
    void testAuthOnboardingAndPrivacy() throws Exception {
        String phone = "+919111222333";

        // 1. Request OTP
        MvcResult otpRes = mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("mock"))
            .andExpect(jsonPath("$.devOtpHint").isNotEmpty())
            .andReturn();

        String otpCode = objectMapper.readTree(otpRes.getResponse().getContentAsString())
            .get("devOtpHint").asText();

        // 2. Verify OTP -> Receive Access & Refresh tokens
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", phone, "otp", otpCode))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.onboardingCompleted").value(false))
            .andReturn();

        JsonNode authJson = objectMapper.readTree(verifyRes.getResponse().getContentAsString());
        String accessToken = authJson.get("accessToken").asText();
        String refreshToken = authJson.get("refreshToken").asText();
        String userId = authJson.get("userId").asText();

        // 3. Rotate Refresh Token & verify old token is rejected
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isUnauthorized());

        String rotatedAccessToken = objectMapper.readTree(refreshRes.getResponse().getContentAsString())
            .get("accessToken").asText();

        // 4. Complete Onboarding in Indiranagar
        String uniqueDisplayName = "test_gamer_" + userId.substring(0, 6);
        mockMvc.perform(put("/api/v1/users/me/onboarding")
                .header("Authorization", "Bearer " + rotatedAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "displayName", uniqueDisplayName,
                    "consoles", List.of("PS5", "SWITCH"),
                    "localityId", INDIRANAGAR_ID,
                    "bio", "Testing hyperlocal swaps in Bangalore"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onboardingCompleted").value(true))
            .andExpect(jsonPath("$.localityName").value("Indiranagar"))
            .andExpect(jsonPath("$.phone").doesNotExist());

        // 5. Verify Public Profile never exposes phone number or exact coordinates
        MvcResult profileRes = mockMvc.perform(get("/api/v1/users/" + userId + "/profile")
                .header("Authorization", "Bearer " + rotatedAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value(uniqueDisplayName))
            .andExpect(jsonPath("$.localityName").value("Indiranagar"))
            .andReturn();

        String profileBody = profileRes.getResponse().getContentAsString();
        assertThat(profileBody).doesNotContain(phone).doesNotContain("9111222333");

        // 6. Verify fuzzed coordinates are ~350m - 550m away from Indiranagar centroid
        Double fuzzDistanceMeters = jdbcClient.sql("""
                SELECT ST_Distance(
                    area_point,
                    ST_SetSRID(ST_MakePoint(fuzz_lng, fuzz_lat), 4326)::geography
                )
                FROM users WHERE id = :id
                """)
            .param("id", UUID.fromString(userId))
            .query(Double.class)
            .single();
        assertThat(fuzzDistanceMeters).isBetween(300.0, 600.0);

        // 7. Verify OTP Rate Limiting (max 5 per 10m)
        String rateLimitPhone = "+919444555666";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/otp/request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("phone", rateLimitPhone))))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", rateLimitPhone))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("OTP_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("2. Listings Constraint: Max 10 active WANTs per user enforced")
    void testMaxTenActiveWantsConstraint() throws Exception {
        String token = loginAndOnboard("+919222333444", "want_tester_" + UUID.randomUUID().toString().substring(0, 5), INDIRANAGAR_ID);

        // Add 10 active wants using seeded games (1..10)
        for (int i = 1; i <= 10; i++) {
            UUID gameId = UUID.fromString(String.format("33333333-3333-3333-3333-%012d", i));
            mockMvc.perform(post("/api/v1/listings/want")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "gameId", gameId,
                        "platform", "PS5"
                    ))))
                .andExpect(status().isOk());
        }

        // 11th active want must be rejected with 400 MAX_WANTS_EXCEEDED
        UUID eleventhGameId = UUID.fromString("33333333-3333-3333-3333-000000000011");
        mockMvc.perform(post("/api/v1/listings/want")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "gameId", eleventhGameId,
                    "platform", "PS5"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MAX_WANTS_EXCEEDED"));
    }

    @Test
    @DisplayName("3. PostGIS Matching, Cursor Pagination, Map Fuzzing, and Blocking Users")
    void testPostgisMatchingRankingPaginationAndBlocking() throws Exception {
        // Log in as seeded User #1 (arjun_blr, +919876543210, Indiranagar)
        String arjunToken = loginSeededUser("+919876543210");

        // 1. Fetch ranked matches within 10 km
        MvcResult matchRes = mockMvc.perform(get("/api/v1/matches")
                .param("radiusKm", "10")
                .param("limit", "2")
                .header("Authorization", "Bearer " + arjunToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        JsonNode page1 = objectMapper.readTree(matchRes.getResponse().getContentAsString());
        JsonNode items1 = page1.get("items");
        assertThat(items1.size()).isEqualTo(2);

        // Verify Two-Way matches (matchTier == 2) come first
        assertThat(items1.get(0).get("matchTier").asInt()).isEqualTo(2);
        assertThat(items1.get(0).get("matchType").asText()).isEqualTo("TWO_WAY");
        assertThat(items1.get(0).get("theyHaveGamesIWant")).isNotEmpty();
        assertThat(items1.get(0).get("theyWantGamesIHave")).isNotEmpty();

        // 2. Verify Cursor Pagination works cleanly
        if (page1.get("hasMore").asBoolean()) {
            String nextCursor = page1.get("nextCursor").asText();
            MvcResult page2Res = mockMvc.perform(get("/api/v1/matches")
                    .param("radiusKm", "10")
                    .param("limit", "2")
                    .param("cursor", nextCursor)
                    .header("Authorization", "Bearer " + arjunToken))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode items2 = objectMapper.readTree(page2Res.getResponse().getContentAsString()).get("items");
            assertThat(items2).isNotEmpty();
            String firstPageIds = items1.get(0).get("userId").asText() + "," + items1.get(1).get("userId").asText();
            assertThat(firstPageIds).doesNotContain(items2.get(0).get("userId").asText());
        }

        // 3. Verify Map Pins return fuzzed coordinates (never exact Indiranagar centroid 12.9784, 77.6408)
        MvcResult pinsRes = mockMvc.perform(get("/api/v1/map/pins")
                .param("radiusKm", "10")
                .header("Authorization", "Bearer " + arjunToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isNotEmpty())
            .andReturn();

        JsonNode firstPin = objectMapper.readTree(pinsRes.getResponse().getContentAsString()).get("items").get(0);
        double pinLat = firstPin.get("fuzzedLatitude").asDouble();
        double pinLng = firstPin.get("fuzzedLongitude").asDouble();
        assertThat(pinLat).isNotEqualTo(12.9784);
        assertThat(pinLng).isNotEqualTo(77.6408);

        // 4. Test Blocking: Block top matched user and verify they disappear from matches immediately
        String topMatchedUserId = items1.get(0).get("userId").asText();
        mockMvc.perform(post("/api/v1/safety/blocks/" + topMatchedUserId)
                .header("Authorization", "Bearer " + arjunToken))
            .andExpect(status().isOk());

        MvcResult afterBlockRes = mockMvc.perform(get("/api/v1/matches")
                .param("radiusKm", "10")
                .param("limit", "50")
                .header("Authorization", "Bearer " + arjunToken))
            .andExpect(status().isOk())
            .andReturn();

        String afterBlockBody = afterBlockRes.getResponse().getContentAsString();
        assertThat(afterBlockBody).doesNotContain(topMatchedUserId);

        // Unblock to restore state
        mockMvc.perform(delete("/api/v1/safety/blocks/" + topMatchedUserId)
                .header("Authorization", "Bearer " + arjunToken))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("4. Full Swap Lifecycle: Propose -> Accept -> Dual Confirm -> Automatic HAVE List Transfer -> Ratings")
    void testFullSwapLifecycleAndRatings() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 5);
        AuthSession userA = loginAndOnboardSession("+919333111222", "swap_user_a_" + suffix, INDIRANAGAR_ID);
        AuthSession userB = loginAndOnboardSession("+919333111333", "swap_user_b_" + suffix, KORAMANGALA_ID);

        // User A lists HAVE: Elden Ring (PS5) and WANT: Ghost of Tsushima (PS5)
        UUID listingA = createHaveListing(userA.token(), GAME_ELDEN_RING, "PS5", "LIKE_NEW");
        createWantListing(userA.token(), GAME_GHOST_TSUSHIMA, "PS5");

        // User B lists HAVE: Ghost of Tsushima (PS5) and WANT: Elden Ring (PS5)
        UUID listingB = createHaveListing(userB.token(), GAME_GHOST_TSUSHIMA, "PS5", "GOOD");
        createWantListing(userB.token(), GAME_ELDEN_RING, "PS5");

        // 1. User A opens a chat thread with User B
        MvcResult threadRes = mockMvc.perform(post("/api/v1/chats/threads")
                .header("Authorization", "Bearer " + userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("recipientId", userB.userId()))))
            .andExpect(status().isOk())
            .andReturn();
        String threadId = objectMapper.readTree(threadRes.getResponse().getContentAsString()).get("id").asText();

        // 2. User A proposes swap: Elden Ring for Ghost of Tsushima at LXG Indiranagar
        MvcResult proposeRes = mockMvc.perform(post("/api/v1/swaps/propose")
                .header("Authorization", "Bearer " + userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "threadId", threadId,
                    "myHaveListingId", listingA,
                    "theirHaveListingId", listingB,
                    "meetupSpotId", LXG_MEETUP_ID,
                    "message", "Let's swap Elden Ring for your Ghost of Tsushima at LXG!"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROPOSED"))
            .andExpect(jsonPath("$.meetupSpotName").value("LXG (League of Extraordinary Gamers) Indiranagar"))
            .andReturn();

        String swapId = objectMapper.readTree(proposeRes.getResponse().getContentAsString()).get("id").asText();

        // 3. User B accepts the swap -> status becomes ACCEPTED
        mockMvc.perform(post("/api/v1/swaps/" + swapId + "/accept")
                .header("Authorization", "Bearer " + userB.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 4. User A confirms physical handoff -> proposerConfirmed = true, status remains ACCEPTED
        mockMvc.perform(post("/api/v1/swaps/" + swapId + "/confirm")
                .header("Authorization", "Bearer " + userA.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.proposerConfirmed").value(true))
            .andExpect(jsonPath("$.recipientConfirmed").value(false));

        // 5. User B confirms physical handoff -> status becomes COMPLETED and games swap between HAVE lists!
        mockMvc.perform(post("/api/v1/swaps/" + swapId + "/confirm")
                .header("Authorization", "Bearer " + userB.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.proposerConfirmed").value(true))
            .andExpect(jsonPath("$.recipientConfirmed").value(true));

        // 6. Verify User A's active HAVE list now contains Ghost of Tsushima, and User B's contains Elden Ring!
        MvcResult haveARes = mockMvc.perform(get("/api/v1/listings/have")
                .header("Authorization", "Bearer " + userA.token()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode haveAItems = objectMapper.readTree(haveARes.getResponse().getContentAsString()).get("items");
        assertThat(haveAItems).hasSize(1);
        assertThat(haveAItems.get(0).get("gameId").asText()).isEqualTo(GAME_GHOST_TSUSHIMA.toString());
        assertThat(haveAItems.get(0).get("status").asText()).isEqualTo("ACTIVE");

        MvcResult haveBRes = mockMvc.perform(get("/api/v1/listings/have")
                .header("Authorization", "Bearer " + userB.token()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode haveBItems = objectMapper.readTree(haveBRes.getResponse().getContentAsString()).get("items");
        assertThat(haveBItems).hasSize(1);
        assertThat(haveBItems.get(0).get("gameId").asText()).isEqualTo(GAME_ELDEN_RING.toString());
        assertThat(haveBItems.get(0).get("status").asText()).isEqualTo("ACTIVE");

        // 7. Submit 5-star rating from User A to User B
        mockMvc.perform(post("/api/v1/swaps/" + swapId + "/ratings")
                .header("Authorization", "Bearer " + userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "stars", 5,
                    "comment", "Super punctual at LXG Indiranagar, disc in great shape!"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stars").value(5));

        // Duplicate rating on the same swap by User A must fail with 409 CONFLICT
        mockMvc.perform(post("/api/v1/swaps/" + swapId + "/ratings")
                .header("Authorization", "Bearer " + userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("stars", 4, "comment", "Duplicate"))))
            .andExpect(status().isConflict());

        // Verify User B's profile reflects ratingAvg = 5.0 and completedSwapsCount = 1
        mockMvc.perform(get("/api/v1/users/" + userB.userId() + "/profile")
                .header("Authorization", "Bearer " + userA.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratingAvg").value(5.0))
            .andExpect(jsonPath("$.ratingCount").value(1))
            .andExpect(jsonPath("$.completedSwapsCount").value(1));
    }

    private record AuthSession(UUID userId, String token) {}

    private String loginSeededUser(String phone) throws Exception {
        MvcResult otpRes = mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
            .andExpect(status().isOk())
            .andReturn();
        String otp = objectMapper.readTree(otpRes.getResponse().getContentAsString()).get("devOtpHint").asText();

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", phone, "otp", otp))))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(verifyRes.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String loginAndOnboard(String phone, String displayName, UUID localityId) throws Exception {
        return loginAndOnboardSession(phone, displayName, localityId).token();
    }

    private AuthSession loginAndOnboardSession(String phone, String displayName, UUID localityId) throws Exception {
        MvcResult otpRes = mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
            .andExpect(status().isOk())
            .andReturn();
        String otp = objectMapper.readTree(otpRes.getResponse().getContentAsString()).get("devOtpHint").asText();

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("phone", phone, "otp", otp))))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = objectMapper.readTree(verifyRes.getResponse().getContentAsString());
        String token = json.get("accessToken").asText();
        UUID userId = UUID.fromString(json.get("userId").asText());

        mockMvc.perform(put("/api/v1/users/me/onboarding")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "displayName", displayName,
                    "consoles", List.of("PS5", "SWITCH"),
                    "localityId", localityId,
                    "bio", "Bangalore console gamer"
                ))))
            .andExpect(status().isOk());

        return new AuthSession(userId, token);
    }

    private UUID createHaveListing(String token, UUID gameId, String platform, String condition) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/listings/have")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "gameId", gameId,
                    "platform", platform,
                    "condition", condition,
                    "notes", "Mint disc"
                ))))
            .andExpect(status().isOk())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private void createWantListing(String token, UUID gameId, String platform) throws Exception {
        mockMvc.perform(post("/api/v1/listings/want")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "gameId", gameId,
                    "platform", platform
                ))))
            .andExpect(status().isOk());
    }
}

