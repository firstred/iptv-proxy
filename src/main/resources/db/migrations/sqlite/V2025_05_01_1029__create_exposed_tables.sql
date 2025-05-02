create table app_data
(
    name  VARCHAR(255) not null
        primary key,
    value TEXT         not null
);

create table epg_channel
(
    epg_channel_id VARCHAR(255)                           not null,
    server         VARCHAR(255)                           not null,
    icon           TEXT,
    name           VARCHAR(511)                           not null,
    created_at     TEXT default '2025-05-01 12:10:55.188' not null,
    updated_at     TEXT default '2025-05-01 12:10:55.188' not null,
    constraint pk_epg_channel
        primary key (server, epg_channel_id)
);
create index idx_hCn36jexzfx9cipkfy0l
    on epg_channel (server, name);

create table epg_display_name
(
    epg_channel_id VARCHAR(255) not null,
    server         VARCHAR(255) not null,
    language       VARCHAR(255) not null,
    name           TEXT         not null,
    constraint pk_epg_display_name
        primary key (epg_channel_id, language, server),
    constraint fk_yZfhh62xma8rhvfdwhph
        foreign key (epg_channel_id, server) references epg_channel (epg_channel_id, server)
            on update cascade on delete cascade
);
create index idx_klasjdfasdfw
    on epg_display_name (epg_channel_id, server);

create table epg_programme
(
    id             INTEGER
        primary key autoincrement,
    epg_channel_id VARCHAR(255)                           not null,
    server         VARCHAR(255)                           not null,
    title          TEXT                                   not null,
    subtitle       TEXT                                   not null,
    description    TEXT                                   not null,
    start          TEXT                                   not null,
    stop           TEXT                                   not null,
    icon           TEXT,
    created_at     TEXT default '2025-05-01 12:10:55.189' not null,
    updated_at     TEXT default '2025-05-01 12:10:55.189' not null,
    constraint fk_8jsdfh8sdf8h8sdfh8sdf
        foreign key (epg_channel_id, server) references epg_channel (epg_channel_id, server)
            on update cascade on delete cascade
);
create unique index unq_ftytsdf8sdfh8sdfh8sdf
    on epg_programme (epg_channel_id, server, start);

create table epg_programme_audio
(
    programme_id BIGINT       not null
        constraint fk_podosjdfh8sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       VARCHAR(255) not null,
    type         VARCHAR(255) not null,
    value        VARCHAR(255) not null,
    constraint pk_epg_programme_audio
        primary key (programme_id, type)
);

create table epg_programme_category
(
    programme_id BIGINT       not null
        constraint fk_lakdfjh8sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       VARCHAR(255) not null,
    language     VARCHAR(255) not null,
    category     VARCHAR(255) not null,
    constraint pk_epg_programme_category
        primary key (programme_id, language, category)
);

create table epg_programme_episode_number
(
    programme_id BIGINT       not null
        constraint fk_pjasdfh8sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       VARCHAR(255) not null,
    system       VARCHAR(255),
    number       VARCHAR(255) not null,
    constraint pk_epg_programme_episode_number
        primary key (programme_id, system)
);

create table epg_programme_previously_shown
(
    programme_id BIGINT       not null
        constraint fk_9jdfe08sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       VARCHAR(255) not null,
    start        TEXT         not null,
    constraint pk_epg_programme_previously_shown
        primary key (programme_id, start)
);

create table epg_programme_rating
(
    programme_id BIGINT       not null
        constraint fk_plasdjolkjsmgjdfg
            references epg_programme
            on update cascade on delete cascade,
    server       VARCHAR(255) not null,
    system       VARCHAR(255) not null,
    rating       VARCHAR(255) not null,
    constraint pk_epg_programme_rating
        primary key (programme_id, system)
);

create table epg_programme_subtitles
(
    programme_id BIGINT       not null
        constraint fk_lkjhs45678sdfgdf
            references epg_programme
            on update cascade on delete cascade,
    server       VARCHAR(255) not null,
    type         VARCHAR(255) not null,
    constraint pk_epg_programme_subtitles
        primary key (programme_id, type)
);

create table iptv_channel
(
    id                 INTEGER
        primary key autoincrement,
    epg_channel_id     VARCHAR(255)                           not null,
    url                TEXT                                   not null,
    external_stream_id VARCHAR(255),
    server             VARCHAR(255)                           not null,
    icon               TEXT,
    name               TEXT                                   not null,
    main_group         TEXT,
    groups             TEXT,
    catchup_days       BIGINT,
    type               VARCHAR(255)                           not null,
    created_at         TEXT default '2025-05-01 12:10:55.191' not null,
    updated_at         TEXT default '2025-05-01 12:10:55.191' not null
);
create index idx_kljsadfh08sdfh8sdfh8sdf
    on iptv_channel (server, external_stream_id);
create unique index unq_hsdf7sadfh8sdfh8sdf
    on iptv_channel (server, url);

create table live_stream
(
    num                 BIGINT                                         not null,
    server              VARCHAR(255)                                   not null,
    name                TEXT                                           not null,
    external_stream_id  VARCHAR(255)                                   not null,
    icon                TEXT,
    epg_channel_id      VARCHAR(255),
    added               VARCHAR(255) default '0'                       not null,
    is_adult            INT          default 0                         not null,
    main_category_id    BIGINT,
    custom_sid          VARCHAR(255),
    tv_archive          INT          default 0                         not null,
    direct_source       VARCHAR(255) default ''                        not null,
    tv_archive_duration INT          default 0                         not null,
    created_at          TEXT         default '2025-05-01 12:10:55.185' not null,
    updated_at          TEXT         default '2025-05-01 12:10:55.185' not null,
    constraint pk_live_stream
        primary key (server, num),
    constraint chk_live_stream_signed_integer_is_adult
        check (is_adult BETWEEN -2147483648 AND 2147483647),
    constraint chk_live_stream_signed_integer_tv_archive
        check (tv_archive BETWEEN -2147483648 AND 2147483647),
    constraint chk_live_stream_signed_integer_tv_archive_duration
        check (tv_archive_duration BETWEEN -2147483648 AND 2147483647)
);

create unique index unq_uiuoio8sdfh8sdfh8sdf
    on live_stream (server, external_stream_id);

create table live_stream_category
(
    id                   INTEGER
        primary key autoincrement,
    server               VARCHAR(255)                                   not null,
    external_category_id BIGINT                                         not null,
    category_name        TEXT                                           not null,
    parent_id            VARCHAR(255) default '0'                       not null,
    created_at           TEXT         default '2025-05-01 12:10:55.183' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.183' not null
);
create unique index unq_jhyyty8sdfh8sdfh8sdf
    on live_stream_category (server, external_category_id);

create table live_stream_to_category
(
    server      VARCHAR(255) not null,
    num         BIGINT       not null,
    category_id BIGINT       not null
        constraint fk_sdaoifuasd98f8sdfh8sdf
            references live_stream_category
            on update cascade on delete cascade,
    constraint pk_live_stream_to_category
        primary key (server, num, category_id),
    constraint fk_dfopijds98f8sdfh8sdf
        foreign key (server, num) references live_stream
            on update cascade on delete cascade
);

create table movie
(
    num                  BIGINT                                         not null,
    server               VARCHAR(255)                                   not null,
    name                 TEXT                                           not null,
    external_stream_id   VARCHAR(255)                                   not null,
    external_stream_icon TEXT,
    rating               VARCHAR(255),
    rating_5based        DOUBLE PRECISION,
    tmdb                 VARCHAR(255),
    trailer              VARCHAR(255),
    added                VARCHAR(255) default '0'                       not null,
    is_adult             INT          default 0                         not null,
    main_category_id     BIGINT,
    container_extension  VARCHAR(255)                                   not null,
    custom_sid           VARCHAR(255),
    direct_source        TEXT,
    created_at           TEXT         default '2025-05-01 12:10:55.186' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.186' not null,
    constraint pk_movie
        primary key (server, num),
    constraint chk_movie_signed_integer_is_adult
        check (is_adult BETWEEN -2147483648 AND 2147483647)
);
create unique index unq_oidfhjasdufiisdfh8sdf
    on movie (server, external_stream_id);

create table movie_category
(
    id                   INTEGER
        primary key autoincrement,
    server               VARCHAR(255)                                   not null
        constraint fk_qasdvbnasbpo8sdfasdf
            references movie (server)
            on update cascade on delete cascade,
    external_category_id BIGINT                                         not null,
    category_name        TEXT                                           not null,
    parent_id            VARCHAR(255) default '0'                       not null,
    created_at           TEXT         default '2025-05-01 12:10:55.186' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.186' not null
);

create unique index unq_lkjohsdf8sdfh8sdfh8sdf
    on movie_category (server, external_category_id);

create table movie_to_category
(
    server      VARCHAR(255) not null,
    num         BIGINT       not null,
    category_id BIGINT       not null
        constraint fk_lakdsjhf8sdfh8sdfh8sdf
            references movie_category
            on update cascade on delete cascade,
    constraint pk_movie_to_category
        primary key (server, num, category_id),
    constraint fk_lkdasjf8sdfh8sdfh8sdf
        foreign key (server, num) references movie
            on update cascade on delete cascade
);

create table playlist_source
(
    server       VARCHAR(255)                           not null
        primary key,
    created_at   TEXT default '2025-05-01 12:10:55.191' not null,
    started_at   TEXT default '2025-05-01 12:10:55.191' not null,
    completed_at TEXT default '2025-05-01 12:10:55.191' not null
);

create table series
(
    num              BIGINT                                 not null,
    server           VARCHAR(255)                           not null,
    name             TEXT                                   not null,
    series_id        VARCHAR(255)                           not null,
    cover            TEXT                                   not null,
    plot             TEXT                                   not null,
    cast             TEXT                                   not null,
    main_category_id BIGINT,
    director         TEXT                                   not null,
    genre            TEXT                                   not null,
    release_date     VARCHAR(255)                           not null,
    last_modified    VARCHAR(255)                           not null,
    rating           TEXT                                   not null,
    rating_5based    TEXT                                   not null,
    backdrop_path    TEXT,
    youtube_trailer  VARCHAR(255),
    tmdb             VARCHAR(255),
    episode_run_time VARCHAR(255),
    created_at       TEXT default '2025-05-01 12:10:55.187' not null,
    updated_at       TEXT default '2025-05-01 12:10:55.187' not null,
    constraint pk_series
        primary key (server, num)
);

create table series_category
(
    id                   INTEGER
        primary key autoincrement,
    server               VARCHAR(255)                                   not null,
    external_category_id BIGINT                                         not null,
    category_name        TEXT                                           not null,
    parent_id            VARCHAR(255) default '0'                       not null,
    created_at           TEXT         default '2025-05-01 12:10:55.187' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.187' not null
);
create unique index unq_oliksdjf8sdfh8sdfh8sdf
    on series_category (server, external_category_id);

create table series_to_category
(
    server      VARCHAR(255) not null,
    num         BIGINT       not null,
    category_id BIGINT       not null,
    constraint pk_series_to_category
        primary key (server, num, category_id),
    constraint fk_sfdoijgj8sdfh8sdfh8sdf
        foreign key (server, category_id) references series_category (server, external_category_id)
            on update cascade on delete cascade,
    constraint fk_pohnbmdv8sdfh8sdfh8sdf
        foreign key (server, num) references series
            on update cascade on delete cascade
);

create table xmltv_source
(
    server              VARCHAR(255)                           not null
        primary key,
    generator_info_name TEXT,
    generator_info_url  TEXT,
    source_info_url     TEXT,
    source_info_name    TEXT,
    source_info_logo    TEXT,
    created_at          TEXT default '2025-05-01 12:10:55.191' not null,
    started_at          TEXT default '2025-05-01 12:10:55.191' not null,
    completed_at        TEXT default '2025-05-01 12:10:55.191' not null
);

create table xtream_source
(
    server       VARCHAR(255)                           not null
        primary key,
    created_at   TEXT default '2025-05-01 12:10:55.191' not null,
    started_at   TEXT default '2025-05-01 12:10:55.192' not null,
    completed_at TEXT default '2025-05-01 12:10:55.192' not null
);
