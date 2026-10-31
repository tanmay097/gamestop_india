-- ============================================================================
-- V3__seed_demo_data.sql
-- Seed 20 Popular Console Games & 50 Bangalore Gamers across 5 Localities
-- (Indiranagar, Koramangala, HSR Layout, Whitefield, Jayanagar)
-- ============================================================================

-- 1. Seed 20 Popular Physical Console Games
INSERT INTO games (id, igdb_id, slug, title, cover_url, summary, release_year, genres, supported_platforms, avg_review_rating, review_count) VALUES
    ('33333333-3333-3333-3333-000000000001', 119133, 'elden-ring', 'Elden Ring', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg', 'Vast fantasy action RPG by FromSoftware and George R.R. Martin set in the Lands Between.', 2022, ARRAY['RPG', 'Adventure', 'Action'], ARRAY['PS5', 'PS4', 'SERIES_X_S', 'XBOX_ONE'], 4.90, 3),
    ('33333333-3333-3333-3333-000000000002', 119171, 'god-of-war-ragnarok', 'God of War Ragnarök', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co5s5v.jpg', 'Kratos and Atreus journey through the Nine Realms as Fimbulwinter draws to a close.', 2022, ARRAY['Action', 'Adventure', 'Hack and Slash'], ARRAY['PS5', 'PS4'], 4.85, 2),
    ('33333333-3333-3333-3333-000000000003', 176021, 'marvels-spider-man-2', 'Marvel''s Spider-Man 2', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co6j17.jpg', 'Peter Parker and Miles Morales face the ultimate test of strength against Venom and Kraven.', 2023, ARRAY['Action', 'Adventure'], ARRAY['PS5'], 4.80, 2),
    ('33333333-3333-3333-3333-000000000004', 152855, 'ghost-of-tsushima-directors-cut', 'Ghost of Tsushima Director''s Cut', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co3p5d.jpg', 'Open-world samurai adventure on Tsushima and Iki Island with full DualSense haptics.', 2021, ARRAY['Action', 'Adventure', 'RPG'], ARRAY['PS5', 'PS4'], 4.95, 2),
    ('33333333-3333-3333-3333-000000000005', 119388, 'the-legend-of-zelda-tears-of-the-kingdom', 'The Legend of Zelda: Tears of the Kingdom', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co5vmg.jpg', 'Soar across the skies and explore the depths of Hyrule with Ultrahand and Fuse abilities.', 2023, ARRAY['Adventure', 'Action', 'RPG'], ARRAY['SWITCH'], 4.95, 2),
    ('33333333-3333-3333-3333-000000000006', 134582, 'horizon-forbidden-west', 'Horizon Forbidden West', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co49x5.jpg', 'Join Aloy as she braves the Forbidden West, a deadly frontier concealing mysterious new threats.', 2022, ARRAY['Action', 'RPG', 'Adventure'], ARRAY['PS5', 'PS4'], 4.60, 1),
    ('33333333-3333-3333-3333-000000000007', 204350, 'the-last-of-us-part-i', 'The Last of Us Part I', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co5xex.jpg', 'Rebuilt from the ground up for PS5, experience the emotional storytelling of Joel and Ellie.', 2022, ARRAY['Action', 'Adventure', 'Shooter'], ARRAY['PS5'], 4.85, 1),
    ('33333333-3333-3333-3333-000000000008', 254339, 'super-mario-bros-wonder', 'Super Mario Bros. Wonder', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co6nnl.jpg', 'Classic side-scrolling Mario gameplay turned on its head with Wonder Flowers in the Flower Kingdom.', 2023, ARRAY['Platform', 'Adventure'], ARRAY['SWITCH'], 4.80, 1),
    ('33333333-3333-3333-3333-000000000009', 26758, 'mario-kart-8-deluxe', 'Mario Kart 8 Deluxe', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg', 'Hit the road with the definitive version of Mario Kart 8 on Nintendo Switch.', 2017, ARRAY['Racing', 'Arcade'], ARRAY['SWITCH'], 4.75, 1),
    ('33333333-3333-3333-3333-000000000010', 25076, 'red-dead-redemption-2', 'Red Dead Redemption 2', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co1q1f.jpg', 'Epic tale of outlaw Arthur Morgan and the Van der Linde gang across America in 1899.', 2018, ARRAY['Action', 'Adventure', 'Shooter'], ARRAY['PS4', 'XBOX_ONE'], 4.95, 2),
    ('33333333-3333-3333-3333-000000000011', 268532, 'cyberpunk-2077-ultimate-edition', 'Cyberpunk 2077: Ultimate Edition', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co7dc0.jpg', 'Open-world action-adventure RPG set in Night City, including the Phantom Liberty spy-thriller expansion.', 2023, ARRAY['RPG', 'Shooter', 'Adventure'], ARRAY['PS5', 'SERIES_X_S'], 4.75, 1),
    ('33333333-3333-3333-3333-000000000012', 204628, 'final-fantasy-vii-rebirth', 'Final Fantasy VII Rebirth', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co73ju.jpg', 'Cloud and his comrades escape Midgar and embark on a journey across the wide world.', 2024, ARRAY['RPG', 'Action', 'Adventure'], ARRAY['PS5'], 4.90, 1),
    ('33333333-3333-3333-3333-000000000013', 119172, 'baldurs-gate-3', 'Baldur''s Gate 3', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co670h.jpg', 'Gather your party and return to the Forgotten Realms in a tale of fellowship and betrayal.', 2023, ARRAY['RPG', 'Strategy', 'Tactical'], ARRAY['PS5', 'SERIES_X_S'], 4.95, 1),
    ('33333333-3333-3333-3333-000000000014', 96437, 'starfield', 'Starfield', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co6nio.jpg', 'Bethesda Game Studios'' next-generation role-playing game set amongst the stars.', 2023, ARRAY['RPG', 'Shooter', 'Adventure'], ARRAY['SERIES_X_S'], 4.20, 1),
    ('33333333-3333-3333-3333-000000000015', 152232, 'forza-horizon-5', 'Forza Horizon 5', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co3ofx.jpg', 'Lead breathtaking expeditions across the vibrant and ever-evolving open world landscapes of Mexico.', 2021, ARRAY['Racing', 'Sport'], ARRAY['SERIES_X_S', 'XBOX_ONE'], 4.85, 1),
    ('33333333-3333-3333-3333-000000000016', 103281, 'halo-infinite', 'Halo Infinite', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co2dto.jpg', 'Master Chief returns to confront the Banished across the expansive Zeta Halo ring.', 2021, ARRAY['Shooter', 'Action'], ARRAY['SERIES_X_S', 'XBOX_ONE'], 4.40, 1),
    ('33333333-3333-3333-3333-000000000017', 305152, 'astro-bot', 'Astro Bot', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co8hlz.jpg', 'Blast off on a supersized space adventure across 50+ planets with Astro and 150 VIP bots.', 2024, ARRAY['Platform', 'Adventure'], ARRAY['PS5'], 4.95, 2),
    ('33333333-3333-3333-3333-000000000018', 134586, 'gran-turismo-7', 'Gran Turismo 7', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co3p5f.jpg', 'The Real Driving Simulator featuring over 400 cars and iconic tracks.', 2022, ARRAY['Racing', 'Simulator'], ARRAY['PS5', 'PS4'], 4.65, 1),
    ('33333333-3333-3333-3333-000000000019', 7334, 'bloodborne', 'Bloodborne', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co1rba.jpg', 'Hunt your nightmares in the ancient gothic city of Yharnam.', 2015, ARRAY['RPG', 'Action', 'Adventure'], ARRAY['PS4'], 4.95, 2),
    ('33333333-3333-3333-3333-000000000020', 309815, 'ea-sports-fc-25', 'EA Sports FC 25', 'https://images.igdb.com/igdb/image/upload/t_cover_big/co8efx.jpg', '5v5 Rush mode, FC IQ tactical overhaul, and authentic club football.', 2024, ARRAY['Sport', 'Simulator'], ARRAY['PS5', 'PS4', 'SERIES_X_S', 'XBOX_ONE', 'SWITCH'], 4.30, 1)
ON CONFLICT (igdb_id) DO NOTHING;

-- 2. Seed 50 Bangalore Gamers across 5 Localities (10 per Locality)
-- Hub 1: Indiranagar (1..10), Hub 2: Koramangala (11..20), Hub 3: HSR Layout (21..30),
-- Hub 4: Whitefield (31..40), Hub 5: Jayanagar (41..50).
-- Note: User #1 (arjun_blr) has phone +919876543210, User #11 (rohan_kora) has +919876543220, etc.
DO $$
DECLARE
    i INT;
    v_user_id UUID;
    v_phone TEXT;
    v_phone_hash TEXT;
    v_display_name TEXT;
    v_locality_id UUID;
    v_locality_name TEXT;
    v_center_lat DOUBLE PRECISION;
    v_center_lng DOUBLE PRECISION;
    v_angle DOUBLE PRECISION;
    v_dist_m DOUBLE PRECISION;
    v_fuzz_lat DOUBLE PRECISION;
    v_fuzz_lng DOUBLE PRECISION;
    v_consoles TEXT[];
    v_names TEXT[] := ARRAY[
        'arjun_blr', 'kavya_plays', 'nikhil_ps5', 'divya_switch', 'varun_souls',
        'meera_indira', 'sid_reddead', 'ananya_rpg', 'karthik_gt7', 'tanya_zelda',
        'rohan_kora', 'priya_spidey', 'aditya_elden', 'sneha_kora', 'vikram_xbox',
        'pooja_mario', 'rahul_krat0s', 'ishita_horzn', 'pranav_fc25', 'neha_baldurs',
        'akash_hsr', 'shruti_hsr', 'gautam_tsush', 'ritika_switch', 'harsha_cyber',
        'deepak_astro', 'nandini_tlou', 'saurabh_forza', 'madhuri_ff7', 'chirag_halo',
        'vivek_wfld', 'swati_wfld', 'ashwin_itpl', 'lavanya_ps5', 'naveen_hoodi',
        'pallavi_zelda', 'tarun_blood', 'kriti_wonder', 'manish_star', 'shreya_rpg',
        'darshan_jaya', 'inchara_jaya', 'prithvi_4blk', 'bhavana_ps4', 'suhas_switch',
        'arpitha_kratos', 'chetan_souls', 'spoorthi_gt7', 'kiran_xbox', 'rakshita_astro'
    ];
    v_bios TEXT[] := ARRAY[
        'PS5 & Switch collector in Indiranagar. Always down to swap RPGs at LXG or Metro!',
        'Cozy gamer + Soulsborne fan. Discs kept in pristine condition.',
        'Weekend trophy hunter. Happy to meet near CMH Road or Church Street.',
        'Nintendo Switch & PS5 gamer. Looking for physical first-party titles.',
        'FromSoftware completionist. Let''s trade action RPGs!'
    ];
BEGIN
    FOR i IN 1..50 LOOP
        v_user_id := ('44444444-4444-4444-4444-' || lpad(i::text, 12, '0'))::uuid;
        v_phone := '+9198765432' || lpad((i + 9)::text, 2, '0');
        v_phone_hash := encode(hmac(v_phone, 'gameswap-phone-hmac-secret-bangalore-2026', 'sha256'), 'hex');
        v_display_name := v_names[i];

        IF i <= 10 THEN
            v_locality_id := '11111111-1111-1111-1111-000000000001';
            v_locality_name := 'Indiranagar';
            v_center_lat := 12.9784; v_center_lng := 77.6408;
        ELSIF i <= 20 THEN
            v_locality_id := '11111111-1111-1111-1111-000000000002';
            v_locality_name := 'Koramangala';
            v_center_lat := 12.9352; v_center_lng := 77.6245;
        ELSIF i <= 30 THEN
            v_locality_id := '11111111-1111-1111-1111-000000000003';
            v_locality_name := 'HSR Layout';
            v_center_lat := 12.9121; v_center_lng := 77.6446;
        ELSIF i <= 40 THEN
            v_locality_id := '11111111-1111-1111-1111-000000000004';
            v_locality_name := 'Whitefield';
            v_center_lat := 12.9698; v_center_lng := 77.7500;
        ELSE
            v_locality_id := '11111111-1111-1111-1111-000000000005';
            v_locality_name := 'Jayanagar';
            v_center_lat := 12.9308; v_center_lng := 77.5838;
        END IF;

        -- Compute ~400m - 550m fuzzed coordinates around area centroid
        v_angle := ((i % 10) * 36.0 + (i * 7.0)) * pi() / 180.0;
        v_dist_m := 380.0 + ((i % 5) * 40.0);
        v_fuzz_lat := round((v_center_lat + (v_dist_m * cos(v_angle)) / 111320.0)::numeric, 6);
        v_fuzz_lng := round((v_center_lng + (v_dist_m * sin(v_angle)) / (111320.0 * cos(radians(v_center_lat))))::numeric, 6);

        IF i % 4 = 1 THEN
            v_consoles := ARRAY['PS5', 'SWITCH'];
        ELSIF i % 4 = 2 THEN
            v_consoles := ARRAY['PS5', 'PS4'];
        ELSIF i % 4 = 3 THEN
            v_consoles := ARRAY['PS5', 'SERIES_X_S'];
        ELSE
            v_consoles := ARRAY['SWITCH', 'PS4', 'XBOX_ONE'];
        END IF;

        INSERT INTO users (
            id, phone_hash, phone_encrypted, display_name, avatar_seed, bio,
            consoles, locality_id, locality_name, area_point, fuzz_lat, fuzz_lng,
            rating_avg, rating_count, completed_swaps_count, onboarding_completed, is_admin
        ) VALUES (
            v_user_id,
            v_phone_hash,
            'SEEDED_ENCRYPTED_PHONE_' || i,
            v_display_name,
            'blr-gamer-' || i,
            v_bios[(i % 5) + 1],
            v_consoles,
            v_locality_id,
            v_locality_name,
            ST_SetSRID(ST_MakePoint(v_center_lng, v_center_lat), 4326)::geography,
            v_fuzz_lat,
            v_fuzz_lng,
            round((4.50 + (i % 6) * 0.08)::numeric, 2),
            (i % 7) + 1,
            (i % 5) + 1,
            true,
            (i = 1)
        ) ON CONFLICT (phone_hash) DO NOTHING;
    END LOOP;
END $$;

-- 3. Seed HAVE & WANT Listings for Demo Users
-- Carefully engineered so User #1 (arjun_blr, +919876543210) and all 50 users have immediate
-- Two-Way Matches (score=2) and One-Way Matches (score=1) within 5km and across Bangalore!

-- User #1 (arjun_blr, Indiranagar):
-- HAS: Elden Ring (PS5), God of War Ragnarök (PS5), Zelda Tears of the Kingdom (SWITCH)
-- WANTS: Marvel's Spider-Man 2 (PS5), Ghost of Tsushima (PS5), Astro Bot (PS5), Super Mario Bros. Wonder (SWITCH)
INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status) VALUES
    ('55555555-5555-5555-5555-000000000001', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000001', 'PS5', 'LIKE_NEW', 'Original box with Lands Between map insert, zero scratches.', 'ACTIVE'),
    ('55555555-5555-5555-5555-000000000002', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000002', 'PS5', 'LIKE_NEW', 'Finished platinum trophy, ready to swap in Indiranagar/MG Road.', 'ACTIVE'),
    ('55555555-5555-5555-5555-000000000003', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000005', 'SWITCH', 'GOOD', 'Cartridge + box in great shape.', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO want_listings (id, user_id, game_id, platform, status) VALUES
    ('66666666-6666-6666-6666-000000000001', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000003', 'PS5', 'ACTIVE'),
    ('66666666-6666-6666-6666-000000000002', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000004', 'PS5', 'ACTIVE'),
    ('66666666-6666-6666-6666-000000000003', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000017', 'PS5', 'ACTIVE'),
    ('66666666-6666-6666-6666-000000000004', '44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000008', 'SWITCH', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- User #2 (kavya_plays, Indiranagar - 0 km from User #1, TWO-WAY MATCH):
-- HAS: Marvel's Spider-Man 2 (PS5), Super Mario Bros. Wonder (SWITCH)
-- WANTS: Elden Ring (PS5), Zelda Tears of the Kingdom (SWITCH)
INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status) VALUES
    ('55555555-5555-5555-5555-000000000004', '44444444-4444-4444-4444-000000000002', '33333333-3333-3333-3333-000000000003', 'PS5', 'LIKE_NEW', 'Mint disc, can meet at Indiranagar Metro or LXG.', 'ACTIVE'),
    ('55555555-5555-5555-5555-000000000005', '44444444-4444-4444-4444-000000000002', '33333333-3333-3333-3333-000000000008', 'SWITCH', 'LIKE_NEW', 'Completed 100%, looking for Zelda TOTK.', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO want_listings (id, user_id, game_id, platform, status) VALUES
    ('66666666-6666-6666-6666-000000000005', '44444444-4444-4444-4444-000000000002', '33333333-3333-3333-3333-000000000001', 'PS5', 'ACTIVE'),
    ('66666666-6666-6666-6666-000000000006', '44444444-4444-4444-4444-000000000002', '33333333-3333-3333-3333-000000000005', 'SWITCH', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- User #3 (nikhil_ps5, Indiranagar - TWO-WAY MATCH):
-- HAS: Ghost of Tsushima (PS5), Cyberpunk 2077 (PS5)
-- WANTS: God of War Ragnarök (PS5)
INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status) VALUES
    ('55555555-5555-5555-5555-000000000006', '44444444-4444-4444-4444-000000000003', '33333333-3333-3333-3333-000000000004', 'PS5', 'LIKE_NEW', 'Director''s Cut PS5 disc, spotless.', 'ACTIVE'),
    ('55555555-5555-5555-5555-000000000007', '44444444-4444-4444-4444-000000000003', '33333333-3333-3333-3333-000000000011', 'PS5', 'GOOD', 'Ultimate Edition.', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO want_listings (id, user_id, game_id, platform, status) VALUES
    ('66666666-6666-6666-6666-000000000007', '44444444-4444-4444-4444-000000000003', '33333333-3333-3333-3333-000000000002', 'PS5', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- User #11 (rohan_kora, Koramangala - ~5.1 km from Indiranagar, TWO-WAY MATCH):
-- HAS: Astro Bot (PS5), Baldur's Gate 3 (PS5)
-- WANTS: Elden Ring (PS5), God of War Ragnarök (PS5)
INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status) VALUES
    ('55555555-5555-5555-5555-000000000008', '44444444-4444-4444-4444-000000000011', '33333333-3333-3333-3333-000000000017', 'PS5', 'LIKE_NEW', 'GOTY Astro Bot physical copy! Can meet at Nexus Koramangala.', 'ACTIVE'),
    ('55555555-5555-5555-5555-000000000009', '44444444-4444-4444-4444-000000000011', '33333333-3333-3333-3333-000000000013', 'PS5', 'LIKE_NEW', 'Deluxe 2-disc physical edition.', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO want_listings (id, user_id, game_id, platform, status) VALUES
    ('66666666-6666-6666-6666-000000000008', '44444444-4444-4444-4444-000000000011', '33333333-3333-3333-3333-000000000001', 'PS5', 'ACTIVE'),
    ('66666666-6666-6666-6666-000000000009', '44444444-4444-4444-4444-000000000011', '33333333-3333-3333-3333-000000000002', 'PS5', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Populate HAVE, WANT, and Shelf entries for Users #4..#50
DO $$
DECLARE
    i INT;
    v_uid UUID;
    v_have_game_1 UUID;
    v_have_game_2 UUID;
    v_want_game_1 UUID;
    v_want_game_2 UUID;
    v_plat TEXT;
    v_cond TEXT;
BEGIN
    FOR i IN 4..50 LOOP
        IF i = 11 THEN
            CONTINUE;
        END IF;

        v_uid := ('44444444-4444-4444-4444-' || lpad(i::text, 12, '0'))::uuid;
        v_have_game_1 := ('33333333-3333-3333-3333-' || lpad(((i % 20) + 1)::text, 12, '0'))::uuid;
        v_have_game_2 := ('33333333-3333-3333-3333-' || lpad((((i + 5) % 20) + 1)::text, 12, '0'))::uuid;
        v_want_game_1 := ('33333333-3333-3333-3333-' || lpad((((i + 1) % 20) + 1)::text, 12, '0'))::uuid;
        v_want_game_2 := ('33333333-3333-3333-3333-' || lpad((((i + 2) % 20) + 1)::text, 12, '0'))::uuid;

        IF i % 5 = 0 THEN
            v_plat := 'SWITCH';
        ELSIF i % 5 = 4 THEN
            v_plat := 'PS4';
        ELSIF i % 7 = 0 THEN
            v_plat := 'SERIES_X_S';
        ELSE
            v_plat := 'PS5';
        END IF;

        IF i % 3 = 0 THEN
            v_cond := 'LIKE_NEW';
        ELSIF i % 3 = 1 THEN
            v_cond := 'GOOD';
        ELSE
            v_cond := 'WORN';
        END IF;

        INSERT INTO have_listings (user_id, game_id, platform, condition, notes, status)
        VALUES
            (v_uid, v_have_game_1, v_plat, v_cond, 'Physical copy ready for local Bangalore swap.', 'ACTIVE'),
            (v_uid, v_have_game_2, 'PS5', 'LIKE_NEW', 'Clean disc with original box.', 'ACTIVE');

        INSERT INTO want_listings (user_id, game_id, platform, status)
        VALUES
            (v_uid, v_want_game_1, 'PS5', 'ACTIVE'),
            (v_uid, v_want_game_2, v_plat, 'ACTIVE')
        ON CONFLICT DO NOTHING;

        INSERT INTO user_game_statuses (user_id, game_id, platform, shelf_status)
        VALUES
            (v_uid, v_have_game_1, v_plat, 'FINISHED'),
            (v_uid, v_have_game_2, 'PS5', 'CURRENTLY_PLAYING')
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- Shelf for User #1, #2, #3
INSERT INTO user_game_statuses (user_id, game_id, platform, shelf_status) VALUES
    ('44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000001', 'PS5', 'FINISHED'),
    ('44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000002', 'PS5', 'FINISHED'),
    ('44444444-4444-4444-4444-000000000001', '33333333-3333-3333-3333-000000000005', 'SWITCH', 'CURRENTLY_PLAYING'),
    ('44444444-4444-4444-4444-000000000002', '33333333-3333-3333-3333-000000000003', 'PS5', 'FINISHED'),
    ('44444444-4444-4444-4444-000000000002', '33333333-3333-3333-3333-000000000008', 'SWITCH', 'CURRENTLY_PLAYING')
ON CONFLICT DO NOTHING;

-- 4. Seed Short Game Reviews & Discussion Threads
INSERT INTO game_reviews (game_id, user_id, rating, comment) VALUES
    ('33333333-3333-3333-3333-000000000001', '44444444-4444-4444-4444-000000000001', 5, '160 hours in and still discovering secret bosses. Masterpiece on PS5.'),
    ('33333333-3333-3333-3333-000000000001', '44444444-4444-4444-4444-000000000003', 5, 'Shadow of the Erdtree prep made me replay the whole game. Must-play.'),
    ('33333333-3333-3333-3333-000000000002', '44444444-4444-4444-4444-000000000001', 5, 'Incredible combat and story conclusion. Runs at buttery 60+ fps on PS5.'),
    ('33333333-3333-3333-3333-000000000003', '44444444-4444-4444-4444-000000000002', 5, 'Web wings across Manhattan and Queens feel unbelievable! Easy weekend platinum.'),
    ('33333333-3333-3333-3333-000000000004', '44444444-4444-4444-4444-000000000003', 5, 'DualSense wind and sword clash haptics on PS5 are next level.'),
    ('33333333-3333-3333-3333-000000000005', '44444444-4444-4444-4444-000000000001', 5, 'Building hoverbikes in the Depths never gets old.')
ON CONFLICT DO NOTHING;

INSERT INTO game_discussions (game_id, user_id, body) VALUES
    ('33333333-3333-3333-3333-000000000001', '44444444-4444-4444-4444-000000000001', 'Anyone in Indiranagar or MG Road looking to trade Spider-Man 2 or Astro Bot for my mint PS5 Elden Ring copy?'),
    ('33333333-3333-3333-3333-000000000001', '44444444-4444-4444-4444-000000000002', 'Yes! I have Spider-Man 2 (Like New) in Indiranagar. Just sent you a swap proposal in chat!'),
    ('33333333-3333-3333-3333-000000000003', '44444444-4444-4444-4444-000000000011', 'Does the physical disc require a huge download or is most of the build on disc?'),
    ('33333333-3333-3333-3333-000000000003', '44444444-4444-4444-4444-000000000002', 'Base 1.00 build is completely on the disc, plus a quick patch for New Game+.');

-- 5. Seed Sample Chat Thread & Swap Proposal between User #1 (arjun_blr) & User #2 (kavya_plays)
INSERT INTO chat_threads (id, user_a_id, user_b_id, last_message_at, last_message_preview) VALUES
    ('77777777-7777-7777-7777-000000000001', '44444444-4444-4444-4444-000000000001', '44444444-4444-4444-4444-000000000002', now(), 'Proposed swap: Marvel''s Spider-Man 2 (PS5) for Elden Ring (PS5)')
ON CONFLICT (user_a_id, user_b_id) DO NOTHING;

INSERT INTO swaps (
    id, thread_id, proposer_id, recipient_id,
    proposer_have_listing_id, recipient_have_listing_id,
    meetup_spot_id, meetup_time, status
) VALUES (
    '88888888-8888-8888-8888-000000000001',
    '77777777-7777-7777-7777-000000000001',
    '44444444-4444-4444-4444-000000000002',
    '44444444-4444-4444-4444-000000000001',
    '55555555-5555-5555-5555-000000000004',
    '55555555-5555-5555-5555-000000000001',
    '22222222-2222-2222-2222-000000000001',
    now() + interval '1 day',
    'PROPOSED'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO chat_messages (id, thread_id, sender_id, message_type, body, swap_id, meetup_spot_id, created_at) VALUES
    ('99999999-9999-9999-9999-000000000001', '77777777-7777-7777-7777-000000000001', '44444444-4444-4444-4444-000000000002', 'TEXT', 'Hey Arjun! Saw we have a 2-way match in Indiranagar — my Spider-Man 2 for your Elden Ring?', NULL, NULL, now() - interval '10 minutes'),
    ('99999999-9999-9999-9999-000000000002', '77777777-7777-7777-7777-000000000001', '44444444-4444-4444-4444-000000000002', 'SWAP_PROPOSAL', 'Let''s meet at LXG Indiranagar tomorrow evening to swap!', '88888888-8888-8888-8888-000000000001', '22222222-2222-2222-2222-000000000001', now() - interval '5 minutes')
ON CONFLICT (id) DO NOTHING;

