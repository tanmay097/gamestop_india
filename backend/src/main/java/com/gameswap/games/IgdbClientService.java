package com.gameswap.games;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameswap.common.config.GameSwapProperties;
import com.gameswap.common.ratelimit.RedisStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IgdbClientService {

    private static final Logger log = LoggerFactory.getLogger(IgdbClientService.class);

    private final GameSwapProperties properties;
    private final RedisStoreService redisStore;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public IgdbClientService(
        GameSwapProperties properties,
        RedisStoreService redisStore,
        JdbcClient jdbcClient,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.redisStore = redisStore;
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        String id = properties.igdb().clientId();
        String secret = properties.igdb().clientSecret();
        return id != null && !id.isBlank() && secret != null && !secret.isBlank();
    }

    public void syncFromIgdbIfConfigured(String query) {
        if (!isConfigured() || query == null || query.trim().length() < 2) {
            return;
        }
        String normalized = query.trim().toLowerCase();
        String cacheKey = "igdb:synced:" + normalized;
        if (redisStore.get(cacheKey) != null) {
            return;
        }

        try {
            String token = getOrFetchAccessToken();
            if (token == null) {
                return;
            }
            String safeQuery = normalized.replace("\"", "");
            String apicalypse = "search \"" + safeQuery + "\"; "
                + "fields id,name,slug,summary,first_release_date,cover.image_id,genres.name,platforms.abbreviation; "
                + "where platforms = (48,49,130,167,169); limit 15;";

            String responseJson = restClient.post()
                .uri("https://api.igdb.com/v4/games")
                .header("Client-ID", properties.igdb().clientId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.TEXT_PLAIN)
                .body(apicalypse)
                .retrieve()
                .body(String.class);

            if (responseJson != null) {
                upsertIgdbGames(responseJson);
                redisStore.set(cacheKey, "1", Duration.ofSeconds(properties.igdb().cacheTtlSeconds()));
            }
        } catch (Exception e) {
            log.warn("IGDB API lookup failed for '{}', falling back to local DB catalogue: {}", query, e.getMessage());
        }
    }

    private String getOrFetchAccessToken() {
        String cached = redisStore.get("igdb:oauth:token");
        if (cached != null) {
            return cached;
        }
        try {
            String url = "https://id.twitch.tv/oauth2/token?client_id="
                + properties.igdb().clientId()
                + "&client_secret=" + properties.igdb().clientSecret()
                + "&grant_type=client_credentials";
            String body = restClient.post().uri(url).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body);
            String token = node.path("access_token").asText(null);
            long expiresIn = node.path("expires_in").asLong(3600);
            if (token != null) {
                redisStore.set("igdb:oauth:token", token, Duration.ofSeconds(Math.max(60, expiresIn - 120)));
            }
            return token;
        } catch (Exception e) {
            log.warn("Failed to obtain IGDB OAuth token: {}", e.getMessage());
            return null;
        }
    }

    private void upsertIgdbGames(String json) throws Exception {
        JsonNode array = objectMapper.readTree(json);
        if (!array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            long igdbId = item.path("id").asLong(0);
            String title = item.path("name").asText(null);
            if (igdbId == 0 || title == null) {
                continue;
            }
            String slug = item.path("slug").asText("igdb-" + igdbId);
            String summary = item.path("summary").asText(null);
            Integer releaseYear = null;
            if (item.hasNonNull("first_release_date")) {
                long epochSec = item.get("first_release_date").asLong();
                releaseYear = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).getYear();
            }
            String coverUrl = null;
            if (item.path("cover").hasNonNull("image_id")) {
                String imgId = item.path("cover").get("image_id").asText();
                coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/" + imgId + ".jpg";
            }

            List<String> genres = new ArrayList<>();
            if (item.path("genres").isArray()) {
                for (JsonNode g : item.path("genres")) {
                    if (g.hasNonNull("name")) {
                        genres.add(g.get("name").asText());
                    }
                }
            }

            List<String> platforms = List.of("PS5", "PS4", "SERIES_X_S", "XBOX_ONE", "SWITCH");

            jdbcClient.sql("""
                    INSERT INTO games (id, igdb_id, slug, title, cover_url, summary, release_year, genres, supported_platforms)
                    VALUES (:id, :igdbId, :slug, :title, :coverUrl, :summary, :releaseYear, :genres, :platforms)
                    ON CONFLICT (igdb_id) DO UPDATE
                    SET title = EXCLUDED.title,
                        cover_url = COALESCE(EXCLUDED.cover_url, games.cover_url),
                        summary = COALESCE(EXCLUDED.summary, games.summary),
                        updated_at = now()
                    """)
                .param("id", UUID.randomUUID())
                .param("igdbId", igdbId)
                .param("slug", slug)
                .param("title", title)
                .param("coverUrl", coverUrl)
                .param("summary", summary)
                .param("releaseYear", releaseYear)
                .param("genres", genres.toArray(new String[0]))
                .param("platforms", platforms.toArray(new String[0]))
                .update();
        }
    }
}

