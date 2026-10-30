-- ============================================================================
-- V2__seed_bangalore_reference_data.sql
-- Seed Bangalore Localities (Area Centroids) & Public Meetup Spots
-- ============================================================================

INSERT INTO localities (id, name, pincode, latitude, longitude, center_point) VALUES
    ('11111111-1111-1111-1111-000000000001', 'Indiranagar', '560038', 12.9784, 77.6408, ST_SetSRID(ST_MakePoint(77.6408, 12.9784), 4326)::geography),
    ('11111111-1111-1111-1111-000000000002', 'Koramangala', '560034', 12.9352, 77.6245, ST_SetSRID(ST_MakePoint(77.6245, 12.9352), 4326)::geography),
    ('11111111-1111-1111-1111-000000000003', 'HSR Layout', '560102', 12.9121, 77.6446, ST_SetSRID(ST_MakePoint(77.6446, 12.9121), 4326)::geography),
    ('11111111-1111-1111-1111-000000000004', 'Whitefield', '560066', 12.9698, 77.7500, ST_SetSRID(ST_MakePoint(77.7500, 12.9698), 4326)::geography),
    ('11111111-1111-1111-1111-000000000005', 'Jayanagar', '560041', 12.9308, 77.5838, ST_SetSRID(ST_MakePoint(77.5838, 12.9308), 4326)::geography),
    ('11111111-1111-1111-1111-000000000006', 'MG Road & Church Street', '560001', 12.9756, 77.6066, ST_SetSRID(ST_MakePoint(77.6066, 12.9756), 4326)::geography),
    ('11111111-1111-1111-1111-000000000007', 'BTM Layout', '560076', 12.9166, 77.6101, ST_SetSRID(ST_MakePoint(77.6101, 12.9166), 4326)::geography),
    ('11111111-1111-1111-1111-000000000008', 'JP Nagar', '560078', 12.9063, 77.5857, ST_SetSRID(ST_MakePoint(77.5857, 12.9063), 4326)::geography),
    ('11111111-1111-1111-1111-000000000009', 'Malleshwaram', '560003', 13.0031, 77.5643, ST_SetSRID(ST_MakePoint(77.5643, 13.0031), 4326)::geography),
    ('11111111-1111-1111-1111-000000000010', 'Rajajinagar', '560010', 12.9982, 77.5530, ST_SetSRID(ST_MakePoint(77.5530, 12.9982), 4326)::geography),
    ('11111111-1111-1111-1111-000000000011', 'Marathahalli', '560037', 12.9591, 77.6974, ST_SetSRID(ST_MakePoint(77.6974, 12.9591), 4326)::geography),
    ('11111111-1111-1111-1111-000000000012', 'Bellandur', '560103', 12.9304, 77.6784, ST_SetSRID(ST_MakePoint(77.6784, 12.9304), 4326)::geography),
    ('11111111-1111-1111-1111-000000000013', 'Sarjapur Road', '560035', 12.9081, 77.6744, ST_SetSRID(ST_MakePoint(77.6744, 12.9081), 4326)::geography),
    ('11111111-1111-1111-1111-000000000014', 'Domlur', '560071', 12.9610, 77.6387, ST_SetSRID(ST_MakePoint(77.6387, 12.9610), 4326)::geography),
    ('11111111-1111-1111-1111-000000000015', 'Frazer Town', '560005', 12.9968, 77.6130, ST_SetSRID(ST_MakePoint(77.6130, 12.9968), 4326)::geography),
    ('11111111-1111-1111-1111-000000000016', 'Kammanahalli & Kalyan Nagar', '560043', 13.0159, 77.6379, ST_SetSRID(ST_MakePoint(77.6379, 13.0159), 4326)::geography),
    ('11111111-1111-1111-1111-000000000017', 'Hebbal', '560024', 13.0358, 77.5970, ST_SetSRID(ST_MakePoint(77.5970, 13.0358), 4326)::geography),
    ('11111111-1111-1111-1111-000000000018', 'Banashankari', '560070', 12.9255, 77.5468, ST_SetSRID(ST_MakePoint(77.5468, 12.9255), 4326)::geography),
    ('11111111-1111-1111-1111-000000000019', 'Basavanagudi', '560004', 12.9406, 77.5738, ST_SetSRID(ST_MakePoint(77.5738, 12.9406), 4326)::geography),
    ('11111111-1111-1111-1111-000000000020', 'Electronic City', '560100', 12.8452, 77.6602, ST_SetSRID(ST_MakePoint(77.6602, 12.8452), 4326)::geography)
ON CONFLICT (name) DO NOTHING;

-- Seeded Public Bangalore Meetup Spots
INSERT INTO meetup_spots (id, name, category, locality_id, address_hint, latitude, longitude, location) VALUES
    ('22222222-2222-2222-2222-000000000001', 'LXG (League of Extraordinary Gamers) Indiranagar', 'GAMING_CAFE', '11111111-1111-1111-1111-000000000001', '12th Main Rd, HAL 2nd Stage, Indiranagar', 12.9718, 77.6412, ST_SetSRID(ST_MakePoint(77.6412, 12.9718), 4326)::geography),
    ('22222222-2222-2222-2222-000000000002', 'Indiranagar Metro Station Concourse', 'METRO_STATION', '11111111-1111-1111-1111-000000000001', 'CMH Road, Near KFC, Indiranagar', 12.9784, 77.6386, ST_SetSRID(ST_MakePoint(77.6386, 12.9784), 4326)::geography),
    ('22222222-2222-2222-2222-000000000003', 'Nexus Mall Koramangala Central Atrium', 'MALL', '11111111-1111-1111-1111-000000000002', 'Hosur Road, Koramangala 7th Block', 12.9345, 77.6113, ST_SetSRID(ST_MakePoint(77.6113, 12.9345), 4326)::geography),
    ('22222222-2222-2222-2222-000000000004', 'ZeroLatency & Gamer Cave Koramangala', 'GAMING_CAFE', '11111111-1111-1111-1111-000000000002', '80 Feet Road, Koramangala 4th Block', 12.9355, 77.6261, ST_SetSRID(ST_MakePoint(77.6261, 12.9355), 4326)::geography),
    ('22222222-2222-2222-2222-000000000005', 'Third Wave Coffee 27th Main HSR', 'GAMING_CAFE', '11111111-1111-1111-1111-000000000003', '27th Main Road, Sector 1, HSR Layout', 12.9154, 77.6512, ST_SetSRID(ST_MakePoint(77.6512, 12.9154), 4326)::geography),
    ('22222222-2222-2222-2222-000000000006', 'Play Arena / Gaming Lounge HSR Sector 2', 'GAMING_CAFE', '11111111-1111-1111-1111-000000000003', '19th Main Road, Sector 2, HSR Layout', 12.9102, 77.6428, ST_SetSRID(ST_MakePoint(77.6428, 12.9102), 4326)::geography),
    ('22222222-2222-2222-2222-000000000007', 'Phoenix Marketcity Courtyard Whitefield', 'MALL', '11111111-1111-1111-1111-000000000004', 'Whitefield Main Road, Mahadevapura', 12.9958, 77.6964, ST_SetSRID(ST_MakePoint(77.6964, 12.9958), 4326)::geography),
    ('22222222-2222-2222-2222-000000000008', 'Nexus Shantiniketan Mall Whitefield', 'MALL', '11111111-1111-1111-1111-000000000004', 'ITPL Main Road, Hoodi, Whitefield', 12.9896, 77.7282, ST_SetSRID(ST_MakePoint(77.7282, 12.9896), 4326)::geography),
    ('22222222-2222-2222-2222-000000000009', 'Jayanagar 4th Block Shopping Complex Plaza', 'MALL', '11111111-1111-1111-1111-000000000005', '11th Main Road, Jayanagar 4th Block', 12.9293, 77.5841, ST_SetSRID(ST_MakePoint(77.5841, 12.9293), 4326)::geography),
    ('22222222-2222-2222-2222-000000000010', 'Jayanagar Metro Station Entrance A', 'METRO_STATION', '11111111-1111-1111-1111-000000000005', 'Rashtriya Vidyalaya Rd, Jayanagar', 12.9295, 77.5802, ST_SetSRID(ST_MakePoint(77.5802, 12.9295), 4326)::geography),
    ('22222222-2222-2222-2222-000000000011', 'Blossom Book House Church Street', 'BOOKSTORE', '11111111-1111-1111-1111-000000000006', 'Church Street, Near MG Road Metro Station', 12.9752, 77.6061, ST_SetSRID(ST_MakePoint(77.6061, 12.9752), 4326)::geography),
    ('22222222-2222-2222-2222-000000000012', 'MG Road Metro Plaza', 'METRO_STATION', '11111111-1111-1111-1111-000000000006', 'MG Road & Brigade Road Junction', 12.9756, 77.6066, ST_SetSRID(ST_MakePoint(77.6066, 12.9756), 4326)::geography),
    ('22222222-2222-2222-2222-000000000013', 'Vega City Mall Atrium', 'MALL', '11111111-1111-1111-1111-000000000007', 'Bannerghatta Road, BTM Layout 2nd Stage', 12.9073, 77.6011, ST_SetSRID(ST_MakePoint(77.6011, 12.9073), 4326)::geography),
    ('22222222-2222-2222-2222-000000000014', 'Mantri Square Mall Central Court', 'MALL', '11111111-1111-1111-1111-000000000009', 'Sampige Road, Malleshwaram', 12.9916, 77.5712, ST_SetSRID(ST_MakePoint(77.5712, 12.9916), 4326)::geography),
    ('22222222-2222-2222-2222-000000000015', 'Orion Mall Lakeside Promenade', 'MALL', '11111111-1111-1111-1111-000000000010', 'Brigade Gateway, Rajajinagar', 13.0110, 77.5550, ST_SetSRID(ST_MakePoint(77.5550, 13.0110), 4326)::geography),
    ('22222222-2222-2222-2222-000000000016', 'Centro Mall / Soul Space Spirit Bellandur', 'MALL', '11111111-1111-1111-1111-000000000012', 'Outer Ring Road, Bellandur', 12.9261, 77.6754, ST_SetSRID(ST_MakePoint(77.6754, 12.9261), 4326)::geography)
ON CONFLICT (name) DO NOTHING;
