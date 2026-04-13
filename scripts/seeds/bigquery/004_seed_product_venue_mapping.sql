-- Seed product_venue_mapping reference table (50 products mapped to 8 venues).
-- Matches the venue assignment in R__seed_catalog.sql: venue = venues[1 + (n % 8)]
-- Idempotent: uses MERGE.
-- Run: bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/004_seed_product_venue_mapping.sql

MERGE `asoview-clone-dev.analytics_raw.product_venue_mapping` AS target
USING (
  SELECT * FROM UNNEST([
    -- product N -> venue = venues[1 + (N % 8)]: yokohama, kyoto, osaka, sapporo, fukuoka, okinawa, nagoya, tokyo
    STRUCT('c4e00660-a232-5634-9daa-59362df77a59' AS product_id, '84d9e262-94d6-5e63-a482-f8839d2741b0' AS venue_id, 'Yokohama' AS venue_name),
    STRUCT('78a12dc6-4b97-5ed1-a46b-7bb869eb50b9', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto'),
    STRUCT('80f2518d-343d-53b7-aadd-012f90b881b0', '1021fb3b-1e05-5ce9-8ec0-9a160462593a', 'Osaka'),
    STRUCT('5b2e1005-4d79-5f8b-8a3b-baf408a9b6d5', '76ace7e2-87ef-5cf1-9ba2-6809e6ef26a5', 'Sapporo'),
    STRUCT('c8dc7ef9-9bba-51f5-a48a-e95f0c6749c0', 'd8db6d40-bd28-586f-984b-67fd9051dae4', 'Fukuoka'),
    STRUCT('bb8a11ef-8bee-58f7-b948-dbe2f50bfb42', '74316d79-e4cf-51d7-8c7d-c1cf992a64fd', 'Okinawa'),
    STRUCT('4b5e939f-b5c8-5e69-b1ab-2df406aaeb77', 'fd6cf0d3-8d35-5b69-be0a-48b2320e2feb', 'Nagoya'),
    STRUCT('91c66d69-ff03-5c25-b9b4-c039a74babe9', 'd00949aa-94d9-53a8-b0b5-d95c1a27f29f', 'Tokyo'),
    STRUCT('477b4d8d-e852-5c44-9038-c873d03a01fd', '84d9e262-94d6-5e63-a482-f8839d2741b0', 'Yokohama'),
    STRUCT('26e0743c-1b19-571a-aa3e-1a9abec5de99', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto'),
    STRUCT('2ceaa43a-2cf8-5a86-906c-325baaaeb8d6', '1021fb3b-1e05-5ce9-8ec0-9a160462593a', 'Osaka'),
    STRUCT('51c782b5-1ed9-5615-aa26-328309ff704e', '76ace7e2-87ef-5cf1-9ba2-6809e6ef26a5', 'Sapporo'),
    STRUCT('18178174-e946-5c0b-858b-1b8ab12f62a0', 'd8db6d40-bd28-586f-984b-67fd9051dae4', 'Fukuoka'),
    STRUCT('2e35de31-3068-5fd5-8be4-a739a3256b84', '74316d79-e4cf-51d7-8c7d-c1cf992a64fd', 'Okinawa'),
    STRUCT('f0a744d3-f311-5f04-b903-10c521fcb569', 'fd6cf0d3-8d35-5b69-be0a-48b2320e2feb', 'Nagoya'),
    STRUCT('82f785aa-b492-57e9-8acf-ac2f40d706f0', 'd00949aa-94d9-53a8-b0b5-d95c1a27f29f', 'Tokyo'),
    STRUCT('3f1a6f9f-da35-5b25-9411-7f3c7c8d88b3', '84d9e262-94d6-5e63-a482-f8839d2741b0', 'Yokohama'),
    STRUCT('bbe013e5-f3dd-529a-9cf5-7336d498e04f', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto'),
    STRUCT('336bbcdc-c7fb-537b-86e2-8f74494554f3', '1021fb3b-1e05-5ce9-8ec0-9a160462593a', 'Osaka'),
    STRUCT('1186c1f0-14bf-55d2-8b03-8ebc6726e830', '76ace7e2-87ef-5cf1-9ba2-6809e6ef26a5', 'Sapporo'),
    STRUCT('f12d3420-34c8-58c3-84ef-204b3c25183b', 'd8db6d40-bd28-586f-984b-67fd9051dae4', 'Fukuoka'),
    STRUCT('c6b9233d-1e58-5387-8fd8-18a085f7a9ad', '74316d79-e4cf-51d7-8c7d-c1cf992a64fd', 'Okinawa'),
    STRUCT('a208a91a-24ce-51d7-b4f0-4842906c11b6', 'fd6cf0d3-8d35-5b69-be0a-48b2320e2feb', 'Nagoya'),
    STRUCT('577b144e-4b16-5413-bc42-e37c38f417dd', 'd00949aa-94d9-53a8-b0b5-d95c1a27f29f', 'Tokyo'),
    STRUCT('b7072871-76fd-5948-bafa-3af5c632c768', '84d9e262-94d6-5e63-a482-f8839d2741b0', 'Yokohama'),
    STRUCT('5e217159-1ce0-5bf4-8750-f8e405c4cc3b', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto'),
    STRUCT('cb18f02f-c239-5029-9430-29181489d4cf', '1021fb3b-1e05-5ce9-8ec0-9a160462593a', 'Osaka'),
    STRUCT('97aa22c7-2cdc-54fa-8901-a95aac79931e', '76ace7e2-87ef-5cf1-9ba2-6809e6ef26a5', 'Sapporo'),
    STRUCT('ac9a2f63-d227-57f7-aaf4-490a25a6e690', 'd8db6d40-bd28-586f-984b-67fd9051dae4', 'Fukuoka'),
    STRUCT('a4ddce7a-0226-570a-8249-7f6f273bcb3b', '74316d79-e4cf-51d7-8c7d-c1cf992a64fd', 'Okinawa'),
    STRUCT('3f727b6f-e1f0-5b55-9170-de6fba485c83', 'fd6cf0d3-8d35-5b69-be0a-48b2320e2feb', 'Nagoya'),
    STRUCT('2a64267d-043f-50e0-a860-7debb43ce116', 'd00949aa-94d9-53a8-b0b5-d95c1a27f29f', 'Tokyo'),
    STRUCT('cd8eb4bc-9e91-5a93-96d0-c389102802a6', '84d9e262-94d6-5e63-a482-f8839d2741b0', 'Yokohama'),
    STRUCT('c7a89317-473c-5d6a-82a7-c711936fe180', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto'),
    STRUCT('7c427a3e-7cef-5a77-8585-ad6b83b6c18b', '1021fb3b-1e05-5ce9-8ec0-9a160462593a', 'Osaka'),
    STRUCT('20cbea18-3bd5-54ec-8b1c-c8fe9eb7a144', '76ace7e2-87ef-5cf1-9ba2-6809e6ef26a5', 'Sapporo'),
    STRUCT('80bf2ca6-8eca-5476-be89-937024409092', 'd8db6d40-bd28-586f-984b-67fd9051dae4', 'Fukuoka'),
    STRUCT('f9061149-6379-5ba9-aef6-658ebebc1719', '74316d79-e4cf-51d7-8c7d-c1cf992a64fd', 'Okinawa'),
    STRUCT('77ebc439-2770-53cf-821a-482fbdaf343d', 'fd6cf0d3-8d35-5b69-be0a-48b2320e2feb', 'Nagoya'),
    STRUCT('3befe340-57b7-5576-8f8b-489e5fc5f546', 'd00949aa-94d9-53a8-b0b5-d95c1a27f29f', 'Tokyo'),
    STRUCT('a263d367-c54c-5e25-bbf8-ef68ff81930b', '84d9e262-94d6-5e63-a482-f8839d2741b0', 'Yokohama'),
    STRUCT('52462038-e970-5ec3-82af-b0c0ad5593bd', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto'),
    STRUCT('2e82262d-4773-5f0e-a4cf-c17af1b4c0b3', '1021fb3b-1e05-5ce9-8ec0-9a160462593a', 'Osaka'),
    STRUCT('de0a6197-dd4b-51b1-b7ff-f63bb98758c6', '76ace7e2-87ef-5cf1-9ba2-6809e6ef26a5', 'Sapporo'),
    STRUCT('d84c7101-3a7a-52fc-8616-f6cc0bcca95f', 'd8db6d40-bd28-586f-984b-67fd9051dae4', 'Fukuoka'),
    STRUCT('0b6eb43a-e6bb-55b3-b290-98954da3d457', '74316d79-e4cf-51d7-8c7d-c1cf992a64fd', 'Okinawa'),
    STRUCT('0a2c7440-97cb-5118-85bc-c310b0720707', 'fd6cf0d3-8d35-5b69-be0a-48b2320e2feb', 'Nagoya'),
    STRUCT('3c182c29-4c3b-5a3c-89b0-8a2689412747', 'd00949aa-94d9-53a8-b0b5-d95c1a27f29f', 'Tokyo'),
    STRUCT('ffba0eac-2a9a-5347-8188-3cc9157c10b4', '84d9e262-94d6-5e63-a482-f8839d2741b0', 'Yokohama'),
    STRUCT('eec7fb2a-d455-51e7-be38-1295e1fbf300', '7450e9f8-4531-55a5-8ed5-ce4865b4d4c7', 'Kyoto')
  ])
) AS source
ON target.product_id = source.product_id
WHEN NOT MATCHED THEN
  INSERT (product_id, venue_id, venue_name)
  VALUES (source.product_id, source.venue_id, source.venue_name);
