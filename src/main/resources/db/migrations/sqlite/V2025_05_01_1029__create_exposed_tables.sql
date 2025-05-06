create table app_data
(
    name  VARCHAR(255) not null
        primary key,
    value TEXT         not null
);

create table category
(
    id                   INTEGER
        primary key autoincrement,
    server               VARCHAR(127)                     not null,
    external_category_id BIGINT                           not null,
    category_name        TEXT                             not null,
    parent_id            BIGINT default 0                 not null,
    type                 VARCHAR(31)                      not null,
    created_at           TEXT   default CURRENT_TIMESTAMP not null,
    updated_at           TEXT   default CURRENT_TIMESTAMP not null,
    constraint chk_category_unsigned_integer_external_category_id
        check (external_category_id BETWEEN 0 AND 4294967295),
    constraint chk_category_unsigned_integer_id
        check (id BETWEEN 0 AND 4294967295),
    constraint chk_category_unsigned_integer_parent_id
        check (parent_id BETWEEN 0 AND 4294967295)
);

create unique index uniq_438f002bba40a04a4e02faef0137476d
    on category (server, external_category_id);

create table channel
(
    id                 INTEGER
        primary key autoincrement,
    external_position  BIGINT                         not null,
    epg_channel_id     VARCHAR(255)                   not null,
    url                TEXT                           not null,
    external_stream_id BIGINT                         not null,
    server             VARCHAR(127)                   not null,
    icon               TEXT,
    name               TEXT                           not null,
    main_group         TEXT,
    groups             TEXT,
    catchup_days       BIGINT,
    type               VARCHAR(31)                    not null,
    created_at         TEXT default CURRENT_TIMESTAMP not null,
    updated_at         TEXT default CURRENT_TIMESTAMP not null,
    constraint chk_channel_unsigned_integer_catchup_days
        check (catchup_days BETWEEN 0 AND 4294967295),
    constraint chk_channel_unsigned_integer_external_position
        check (external_position BETWEEN 0 AND 4294967295),
    constraint chk_channel_unsigned_integer_external_stream_id
        check (external_stream_id BETWEEN 0 AND 4294967295),
    constraint chk_channel_unsigned_integer_id
        check (id BETWEEN 0 AND 4294967295)
);

create index idx_81e0d67366a2ec3bed4ad79abd8f8940
    on channel (server, external_position);

create unique index uniq_037df95408f7de3c48f8ff391f86c3df
    on channel (server, external_stream_id);

create table epg_channel
(
    epg_channel_id VARCHAR(255)                   not null,
    server         VARCHAR(127)                   not null,
    icon           TEXT,
    name           VARCHAR(511)                   not null,
    created_at     TEXT default CURRENT_TIMESTAMP not null,
    updated_at     TEXT default CURRENT_TIMESTAMP not null,
    constraint pk_epg_channel
        primary key (server, epg_channel_id)
);

create index idx_2bffdfa0f6c4be29485c21a215348ff5
    on epg_channel (server, epg_channel_id);

create table epg_display_name
(
    epg_channel_id VARCHAR(255) not null,
    server         VARCHAR(127) not null,
    language       VARCHAR(255) not null,
    name           TEXT         not null,
    constraint pk_epg_display_name
        primary key (epg_channel_id, language, server),
    constraint fk_171b90b68e926fefee70c917964a5a24
        foreign key (epg_channel_id, server) references epg_channel (epg_channel_id, server)
            on update cascade on delete cascade
);

create index idx_683b6e0bd327f4f67b64152de4231de1
    on epg_display_name (epg_channel_id, server);

create table epg_programme
(
    server         VARCHAR(127)                   not null,
    epg_channel_id VARCHAR(255)                   not null,
    start          TEXT                           not null,
    stop           TEXT                           not null,
    title          TEXT                           not null,
    subtitle       TEXT                           not null,
    description    TEXT                           not null,
    icon           TEXT,
    created_at     TEXT default CURRENT_TIMESTAMP not null,
    updated_at     TEXT default CURRENT_TIMESTAMP not null,
    constraint pk_epg_programme
        primary key (server, epg_channel_id, start),
    constraint fk_89f69f1c74e354168740cbf40d6c44cf
        foreign key (epg_channel_id, server) references epg_channel (epg_channel_id, server)
            on update cascade on delete cascade
);

create table epg_programme_audio
(
    server          VARCHAR(127) not null,
    epg_channel_id  VARCHAR(255) not null,
    programme_start TEXT         not null,
    type            VARCHAR(255) not null,
    value           VARCHAR(255) not null,
    constraint pk_epg_programme_audio
        primary key (server, epg_channel_id, programme_start, type),
    constraint fk_7ee6751eace2c92dd41471ef75762c3b
        foreign key (server, epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

create table epg_programme_category
(
    server          VARCHAR(127) not null,
    epg_channel_id  VARCHAR(255) not null,
    programme_start TEXT         not null,
    language        VARCHAR(255) not null,
    category        VARCHAR(255) not null,
    constraint pk_epg_programme_category
        primary key (server, epg_channel_id, programme_start, language),
    constraint fk_90938e49cb36faa81068c23c77ce0397
        foreign key (server, epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

create table epg_programme_episode_number
(
    server          VARCHAR(127) not null,
    epg_channel_id  VARCHAR(255) not null,
    programme_start TEXT         not null,
    system          VARCHAR(255),
    number          VARCHAR(255) not null,
    constraint pk_epg_programme_episode_number
        primary key (server, epg_channel_id, programme_start, system),
    constraint fk_c3ffdceccaf6c3c11ba9b56b173c3d6e
        foreign key (server, epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

create table epg_programme_previously_shown
(
    server          VARCHAR(127) not null,
    epg_channel_id  VARCHAR(255) not null,
    programme_start TEXT         not null,
    previous_start  TEXT         not null,
    constraint pk_epg_programme_previously_shown
        primary key (server, epg_channel_id, programme_start),
    constraint fk_0752370f810c318fea40c956f446d991
        foreign key (server, epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

create table epg_programme_rating
(
    server          VARCHAR(127) not null,
    epg_channel_id  VARCHAR(255) not null,
    programme_start TEXT         not null,
    system          VARCHAR(255) not null,
    rating          VARCHAR(255) not null,
    constraint pk_epg_programme_rating
        primary key (server, epg_channel_id, programme_start, system),
    constraint fk_8afc876ec06fab3a423b20b788e2a021
        foreign key (server, programme_start, epg_channel_id) references epg_programme (server, start, epg_channel_id)
            on update cascade on delete cascade
);

create table epg_programme_subtitles
(
    server          VARCHAR(127) not null,
    epg_channel_id  VARCHAR(255) not null,
    programme_start TEXT         not null,
    language        VARCHAR(255) not null,
    subtitle        TEXT         not null,
    constraint pk_epg_programme_subtitles
        primary key (server, epg_channel_id, programme_start, language),
    constraint fk_c46efb630ad43c18420fc4359bb1dbcd
        foreign key (server, epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

create table live_stream
(
    num                 BIGINT       default 0                 not null,
    server              VARCHAR(127)                           not null,
    name                TEXT                                   not null,
    external_stream_id  BIGINT                                 not null,
    icon                TEXT,
    epg_channel_id      VARCHAR(255),
    added               TEXT         default CURRENT_TIMESTAMP not null,
    is_adult            BOOLEAN      default 0                 not null,
    main_category_id    BIGINT,
    custom_sid          VARCHAR(255),
    tv_archive          BOOLEAN      default 0                 not null,
    direct_source       VARCHAR(255) default ''                not null,
    tv_archive_duration BIGINT       default 0                 not null,
    created_at          TEXT         default CURRENT_TIMESTAMP not null,
    updated_at          TEXT         default CURRENT_TIMESTAMP not null,
    constraint pk_live_stream
        primary key (server, external_stream_id),
    constraint chk_live_stream_unsigned_integer_external_stream_id
        check (external_stream_id BETWEEN 0 AND 4294967295),
    constraint chk_live_stream_unsigned_integer_main_category_id
        check (main_category_id BETWEEN 0 AND 4294967295),
    constraint chk_live_stream_unsigned_integer_num
        check (num BETWEEN 0 AND 4294967295),
    constraint chk_live_stream_unsigned_integer_tv_archive_duration
        check (tv_archive_duration BETWEEN 0 AND 4294967295)
);

create unique index live_stream_server_external_stream_id
    on live_stream (server, external_stream_id);
create index idx_6c03df766175249bd3424a88c18fa99e
    on live_stream (server, num);

create table live_stream_to_category
(
    server             VARCHAR(127) not null,
    external_stream_id BIGINT       not null,
    category_id        BIGINT       not null
        constraint fk_8343e7d497dc8ffdbcc4ed31a3e66141
            references category
            on update cascade on delete cascade,
    constraint pk_live_stream_to_category
        primary key (server, external_stream_id, category_id),
    constraint fk_58fef7282f0ac2d4a48ccd925004370f
        foreign key (server, external_stream_id) references live_stream
            on update cascade on delete cascade,
    constraint chk_live_stream_to_category_unsigned_integer_category_id
        check (category_id BETWEEN 0 AND 4294967295),
    constraint chk_live_stream_to_category_unsigned_integer_external_stream_id
        check (external_stream_id BETWEEN 0 AND 4294967295)
);

create table movie
(
    num                  BIGINT  default 0                 not null,
    server               VARCHAR(127)                      not null,
    name                 TEXT                              not null,
    external_stream_id   BIGINT                            not null,
    external_stream_icon TEXT,
    rating               VARCHAR(255),
    rating_5based        SINGLE,
    tmdb                 BIGINT,
    trailer              VARCHAR(255),
    added                TEXT    default CURRENT_TIMESTAMP not null,
    is_adult             BOOLEAN default 0                 not null,
    main_category_id     BIGINT,
    container_extension  VARCHAR(16)                       not null,
    custom_sid           VARCHAR(255),
    direct_source        TEXT,
    created_at           TEXT    default CURRENT_TIMESTAMP not null,
    updated_at           TEXT    default CURRENT_TIMESTAMP not null,
    constraint pk_movie
        primary key (server, external_stream_id),
    constraint chk_movie_unsigned_integer_external_stream_id
        check (external_stream_id BETWEEN 0 AND 4294967295),
    constraint chk_movie_unsigned_integer_main_category_id
        check (main_category_id BETWEEN 0 AND 4294967295),
    constraint chk_movie_unsigned_integer_num
        check (num BETWEEN 0 AND 4294967295),
    constraint chk_movie_unsigned_integer_tmdb
        check (tmdb BETWEEN 0 AND 4294967295)
);

create index idx_8aa06220d55032bf3701990defb691b6
    on movie (server, num);

create table movie_to_category
(
    server             VARCHAR(127) not null,
    external_stream_id BIGINT       not null,
    category_id        BIGINT       not null
        constraint fk_bf79cb5f218687462a48b89ea961ead3
            references category
            on update cascade on delete cascade,
    constraint pk_movie_to_category
        primary key (server, external_stream_id, category_id),
    constraint fk_2b008c191bfbbd2aa631b3139f3979cc
        foreign key (server, external_stream_id) references movie (server, external_stream_id)
            on update cascade on delete cascade,
    constraint chk_movie_to_category_unsigned_integer_category_id
        check (category_id BETWEEN 0 AND 4294967295),
    constraint chk_movie_to_category_unsigned_integer_external_stream_id
        check (external_stream_id BETWEEN 0 AND 4294967295)
);

create table playlist_source
(
    server              VARCHAR(127)                   not null
        primary key,
    created_at          TEXT default CURRENT_TIMESTAMP not null,
    started_import_at   TEXT default CURRENT_TIMESTAMP not null,
    completed_import_at TEXT default CURRENT_TIMESTAMP not null
);

create table series
(
    num              BIGINT default 0                 not null,
    server           VARCHAR(127)                     not null,
    name             TEXT                             not null,
    series_id        BIGINT                           not null,
    cover            TEXT                             not null,
    plot             TEXT                             not null,
    cast             TEXT                             not null,
    main_category_id BIGINT,
    director         TEXT                             not null,
    genre            TEXT                             not null,
    release_date     VARCHAR(255)                     not null,
    last_modified    VARCHAR(255)                     not null,
    rating           TEXT                             not null,
    rating_5based    SINGLE                           not null,
    backdrop_path    TEXT,
    youtube_trailer  VARCHAR(255),
    tmdb             BIGINT,
    episode_run_time VARCHAR(255),
    created_at       TEXT   default CURRENT_TIMESTAMP not null,
    updated_at       TEXT   default CURRENT_TIMESTAMP not null,
    constraint pk_series
        primary key (server, series_id),
    constraint chk_series_unsigned_integer_main_category_id
        check (main_category_id BETWEEN 0 AND 4294967295),
    constraint chk_series_unsigned_integer_num
        check (num BETWEEN 0 AND 4294967295),
    constraint chk_series_unsigned_integer_series_id
        check (series_id BETWEEN 0 AND 4294967295),
    constraint chk_series_unsigned_integer_tmdb
        check (tmdb BETWEEN 0 AND 4294967295)
);

create index idx_01232b0855d37101b5903869e9b86d7d
    on series (server, num);

create table series_to_category
(
    server             VARCHAR(127) not null,
    external_series_id BIGINT       not null,
    category_id        BIGINT       not null,
    constraint pk_series_to_category
        primary key (server, external_series_id, category_id),
    constraint fk_8e1ea5142dfbdedb40a061ad82154727
        foreign key (server, category_id) references category (server, external_category_id)
            on update cascade on delete cascade,
    constraint fk_de4eac24e8e0ac2a832af0ae75b5467c
        foreign key (server, external_series_id) references series (server, series_id)
            on update cascade on delete cascade,
    constraint chk_series_to_category_unsigned_integer_category_id
        check (category_id BETWEEN 0 AND 4294967295),
    constraint chk_series_to_category_unsigned_integer_external_series_id
        check (external_series_id BETWEEN 0 AND 4294967295)
);

create table xmltv_source
(
    server              VARCHAR(127)                   not null
        primary key,
    generator_info_name TEXT,
    generator_info_url  TEXT,
    source_info_url     TEXT,
    source_info_name    TEXT,
    source_info_logo    TEXT,
    created_at          TEXT default CURRENT_TIMESTAMP not null,
    started_import_at   TEXT default CURRENT_TIMESTAMP not null,
    completed_import_at TEXT default CURRENT_TIMESTAMP not null
);

create table xtream_source
(
    server              VARCHAR(127)                   not null
        primary key,
    created_at          TEXT default CURRENT_TIMESTAMP not null,
    started_import_at   TEXT default CURRENT_TIMESTAMP not null,
    completed_import_at TEXT default CURRENT_TIMESTAMP not null
);
