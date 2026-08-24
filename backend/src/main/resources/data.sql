-- ============================================================================
--  YBS AI transit demo - seed data
-- ============================================================================
--  Every lat/lon below was looked up from OpenStreetMap (Nominatim) and pasted
--  verbatim; none are invented. The `-- osm:` comment on each row records what
--  was queried and what OSM classed the result as, so any coordinate can be
--  re-checked independently.
--
--  HONEST SCOPE NOTE (say this in the viva if asked):
--  The stop COORDINATES are real. The three route itineraries are a plausible
--  simplification built from real corridors - they are not transcribed from an
--  official YBS timetable. The brief caps the demo at 3 routes / ~30 stops, so a
--  faithful copy of the ~120-route network was never the goal.
--
--  Routes chosen so that they overlap at several stops, which is what gives the
--  A* transfer logic something real to find:
--    36 x 43 share Hledan (6) and Kamayut (7)
--    43 x 61 share Kyaikkasan (14), Tamwe (15), Mingalar T.N. (20), Botataung (21)
--    36 x 61 share Sule (12)
-- ============================================================================


-- ---------------------------------------------------------------- routes ----
-- Fares are the flat per-boarding fare in kyats. Route 43 is priced slightly
-- higher because it is the long cross-town loop; this gives the Pareto front a
-- real fare/time trade-off to expose rather than a single dominating answer.
INSERT INTO route (id, code, name_my, base_fare_kyats) VALUES
  (1, '36', 'အင်းစိန် - ဆူးလေ (ပြည်လမ်း)',        300),
  (2, '43', 'လှည်းတန်း - ဗိုလ်တထောင် (ဗဟန်း)',     400),
  (3, '61', 'မြောက်ဥက္ကလာပ - ကြည့်မြင်တိုင်',      300);


-- ----------------------------------------------------------------- stops ----
INSERT INTO stop (id, name_my, name_en, lat, lon, landmark_photo_url) VALUES
  (1,  'အင်းစိန်ဈေး',          'Insein Market',            16.8866202, 96.1016507, null), -- osm: "Insein Market, Yangon" -> bus_stop
  (2,  'အင်းစိန်',              'Insein',                   16.8879884, 96.1066695, null), -- osm: "Insein, Yangon" -> district
  (3,  'မရမ်းကုန်း',            'Mayangone',                16.8660884, 96.1171103, null), -- osm: "Mayangone, Yangon" -> administrative
  (4,  'သမိုင်း',               'Thamine',                  16.8612737, 96.1146267, null), -- osm: "Thamine, Yangon" -> stop
  (5,  'လှိုင်',                'Hlaing',                   16.8430511, 96.1234366, null), -- osm: "Hlaing, Yangon" -> administrative
  (6,  'လှည်းတန်း',             'Hledan Junction',          16.8247025, 96.1247559, null), -- osm: "Hledan, Yangon" -> stop
  (7,  'ကမာရွတ်',              'Kamayut',                  16.8237948, 96.1320589, null), -- osm: "Kamayut, Yangon" -> district
  (8,  'စမ်းချောင်း',           'Sanchaung',                16.8061661, 96.1321384, null), -- osm: "Sanchaung, Yangon" -> administrative
  (9,  'မြေနီကုန်း',            'Myaynigone',               16.8036557, 96.1381191, null), -- osm: "Myaynigone, Yangon" -> place
  (10, 'ရန်ကုန်ဘူတာကြီး',       'Yangon Central Station',   16.7816069, 96.1612802, null), -- osm: "Yangon Central Railway Station" -> train_station
  (11, 'ဗိုလ်ချုပ်အောင်ဆန်းဈေး', 'Bogyoke Aung San Market',  16.7802627, 96.1555892, null), -- osm: "Bogyoke Aung San Market, Yangon" -> marketplace
  (12, 'ဆူးလေဘုရား',           'Sule Pagoda',              16.7744095, 96.1587656, null), -- osm: "Sule Pagoda, Yangon" -> place_of_worship
  (13, 'ရန်ကင်း',               'Yankin',                   16.8375171, 96.1642915, null), -- osm: "Yankin, Yangon" -> administrative
  (14, 'ကျိုက္ကစံ',             'Kyaikkasan',               16.8359137, 96.1911184, null), -- osm: "Kyaikkasan, Yangon" -> Kyaikkasan Pagoda grounds
  (15, 'တာမွေ',                'Tamwe',                    16.8062992, 96.1769272, null), -- osm: "Tamwe, Yangon" -> place
  (16, 'ကမ္ဘာအေးဘုရား',         'Kabar Aye Pagoda',         16.8086787, 96.1549828, null), -- osm: "Kabar Aye Pagoda, Yangon" -> secondary road
  (17, 'ဗဟန်း',                'Bahan',                    16.8061396, 96.1512582, null), -- osm: "Bahan, Yangon" -> place
  (18, 'ရွှေတိဂုံဘုရား',        'Shwedagon Pagoda',         16.7986474, 96.1495557, null), -- osm: "Shwedagon Pagoda, Yangon" -> religious
  (19, 'ကန်တော်ကြီး',           'Kandawgyi Lake',           16.7964245, 96.1609688, null), -- osm: "Kandawgyi Lake, Yangon" -> lake
  (20, 'မင်္ဂလာတောင်ညွန့်',     'Mingalar Taung Nyunt',     16.7905944, 96.1656183, null), -- osm: "Mingalar Taung Nyunt, Yangon" -> township
  (21, 'ဗိုလ်တထောင်ဘုရား',      'Botataung Pagoda',         16.7678308, 96.1718413, null), -- osm: "Botataung Pagoda, Yangon" -> place_of_worship
  (22, 'မြောက်ဥက္ကလာပ',         'North Okkalapa',           16.9170016, 96.1669251, null), -- osm: "North Okkalapa, Yangon" -> administrative
  (23, 'သင်္ဃန်းကျွန်း',        'Thingangyun',              16.8319649, 96.1973169, null), -- osm: "Thingangyun, Yangon" -> district
  (24, 'ပုဇွန်တောင်',           'Pazundaung',               16.7779709, 96.1755049, null), -- osm: "Pazundaung, Yangon" -> administrative
  (25, 'ကျောက်တံတား',           'Kyauktada',                16.7741060, 96.1619839, null), -- osm: "Kyauktada, Yangon" -> administrative
  (26, 'လမ်းမတော်',             'Lanmadaw',                 16.7789664, 96.1417274, null), -- osm: "Lanmadaw, Yangon" -> administrative
  (27, 'အလုံ',                 'Ahlone',                   16.7867980, 96.1287706, null), -- osm: "Ahlone, Yangon" -> district
  (28, 'ကြည့်မြင်တိုင်',        'Kyeemyindaing',            16.8129125, 96.1213050, null); -- osm: "Kyeemyindaing, Yangon" -> administrative


-- ------------------------------------------------------------ route_stop ----
-- cumulative_minutes is free-flow running time from the route's first stop.
-- The ETA service scales the per-segment differences by the traffic prior below,
-- so these are deliberately optimistic "no congestion" numbers.

-- Route 36 - Pyay Road corridor, Insein Market -> Sule (12 stops)
INSERT INTO route_stop (route_id, stop_id, sequence_no, cumulative_minutes) VALUES
  (1, 1,  1,  0), (1, 2,  2,  3), (1, 3,  3, 12), (1, 4,  4, 15),
  (1, 5,  5, 22), (1, 7,  6, 28), (1, 6,  7, 32), (1, 8,  8, 38),
  (1, 9,  9, 42), (1, 10, 10, 52), (1, 11, 11, 56), (1, 12, 12, 60);

-- Route 43 - Bahan / Kabar Aye cross-town, Hledan -> Botataung (11 stops)
INSERT INTO route_stop (route_id, stop_id, sequence_no, cumulative_minutes) VALUES
  (2, 6,  1,  0), (2, 7,  2,  4), (2, 13, 3, 12), (2, 14, 4, 18),
  (2, 15, 5, 26), (2, 16, 6, 33), (2, 17, 7, 37), (2, 18, 8, 42),
  (2, 19, 9, 46), (2, 20, 10, 50), (2, 21, 11, 58);

-- Route 61 - Eastern corridor, North Okkalapa -> Kyeemyindaing (12 stops)
INSERT INTO route_stop (route_id, stop_id, sequence_no, cumulative_minutes) VALUES
  (3, 22, 1,  0), (3, 23, 2, 14), (3, 14, 3, 20), (3, 15, 4, 28),
  (3, 20, 5, 34), (3, 24, 6, 40), (3, 21, 7, 46), (3, 25, 8, 51),
  (3, 12, 9, 54), (3, 26, 10, 59), (3, 27, 11, 64), (3, 28, 12, 72);


-- -------------------------------------------------------- traffic prior ----
-- Hand-authored, see TrafficFactor javadoc. DEFAULT applies to any segment with
-- no specific row. The named segments are the corridors that genuinely bind in
-- Yangon: Pyay Road inbound in the morning, the downtown approach in the evening.
INSERT INTO traffic_factor (segment_id, bucket, factor) VALUES
  ('DEFAULT',  'morning_peak', 1.35),
  ('DEFAULT',  'midday',       1.10),
  ('DEFAULT',  'evening_peak', 1.30),
  ('DEFAULT',  'night',        0.95),
  -- Pyay Road, Hlaing -> Kamayut -> Hledan: the worst inbound crawl in the city.
  ('36:5-7',   'morning_peak', 1.90), ('36:5-7',  'midday', 1.20),
  ('36:5-7',   'evening_peak', 1.45), ('36:5-7',  'night',  1.00),
  ('36:7-6',   'morning_peak', 1.95), ('36:7-6',  'midday', 1.25),
  ('36:7-6',   'evening_peak', 1.50), ('36:7-6',  'night',  1.00),
  -- Downtown approach past the station: jams outbound in the evening instead.
  ('36:10-11', 'morning_peak', 1.30), ('36:10-11','midday', 1.15),
  ('36:10-11', 'evening_peak', 1.85), ('36:10-11','night',  0.90),
  -- Tamwe -> Kabar Aye on route 43, heavy in both peaks.
  ('43:15-16', 'morning_peak', 1.60), ('43:15-16','midday', 1.15),
  ('43:15-16', 'evening_peak', 1.70), ('43:15-16','night',  0.95);


-- ------------------------------------------------------------------ buses ----
-- Two buses per route, started at different points so the map looks alive at
-- t=0. Buses 12 and 15 on route 43 are seeded close together on purpose - that
-- is the bunching alert the ops dashboard shows.
-- crowding_level: 0 = seats free, 1 = standing room, 2 = full.
INSERT INTO bus (id, route_id, current_lat, current_lon, current_segment_index, progress_along_segment, crowding_level) VALUES
  (1,  1, 16.8866202, 96.1016507, 0, 0.00, 0),
  (2,  1, 16.8430511, 96.1234366, 4, 0.00, 1),
  (12, 2, 16.8247025, 96.1247559, 0, 0.00, 2),
  (15, 2, 16.8243000, 96.1265000, 0, 0.25, 2),
  (21, 3, 16.9170016, 96.1669251, 0, 0.00, 0),
  (22, 3, 16.8062992, 96.1769272, 3, 0.00, 1);


-- ---------------------------------------------------------------- drivers ----
INSERT INTO driver (id, name, route_id) VALUES
  (1, 'ဦးမြင့်ဆွေ',       1),
  (2, 'ဦးကျော်ဝင်း',      1),
  (3, 'ဒေါ်ခင်မာလာ',      2),
  (4, 'ဦးသန်းထွန်း',      2),
  (5, 'ဦးအောင်မျိုး',     3),
  (6, 'ဒေါ်စန်းစန်းအေး',  3);

-- Scores for the demo month. bonus_eligible is seeded false everywhere on
-- purpose: the ops dashboard overwrites it by actually calling Prolog, so if the
-- rule engine were dead every badge would read "not eligible" and the demo would
-- visibly fail rather than silently show stale seeded answers.
--   rule: headway >= 80 AND harsh <= 5 AND no upheld complaint
--   -> drivers 1 and 5 qualify; 2 fails headway, 3 fails harsh events,
--      4 fails on an upheld complaint, 6 fails headway and harsh events.
INSERT INTO driver_monthly_score (driver_id, month_key, headway_score, harsh_events, upheld_complaints, bonus_eligible) VALUES
  (1, '2026-08', 91, 2, 0, false),
  (2, '2026-08', 74, 3, 0, false),
  (3, '2026-08', 88, 9, 0, false),
  (4, '2026-08', 84, 1, 2, false),
  (5, '2026-08', 96, 0, 0, false),
  (6, '2026-08', 68, 7, 1, false);


-- -------------------------------------------------------------- incidents ----
INSERT INTO incident (id, type, route_id, stop_id, timestamp) VALUES
  (1, 'BREAKDOWN',  1, 5,  '2026-08-03 07:40:00'),
  (2, 'CROWDING',   1, 6,  '2026-08-03 08:05:00'),
  (3, 'CROWDING',   1, 6,  '2026-08-03 08:20:00'),
  (4, 'FLOOD',      2, 15, '2026-08-03 08:15:00'),
  (5, 'BUNCHING',   2, 6,  '2026-08-03 08:30:00'),
  (6, 'CROWDING',   3, 21, '2026-08-03 17:50:00'),
  (7, 'BREAKDOWN',  3, 23, '2026-08-03 18:10:00'),
  (8, 'CROWDING',   3, 12, '2026-08-03 18:25:00');
