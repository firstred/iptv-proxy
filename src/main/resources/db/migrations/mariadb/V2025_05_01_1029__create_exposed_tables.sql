create table if not exists app_data
(
    name  VARCHAR(255) not null
        primary key,
    value TEXT         not null
);

create table if not exists epg_channel
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

create index if not exists idx_hCn36jexzfx9cipkfy0l
    on epg_channel (server, name);

create table if not exists epg_display_name
(
    epg_channel_id VARCHAR(255) not null,
    server         VARCHAR(255) not null,
    language       VARCHAR(255) not null,
    name           VARCHAR(511) not null,
    constraint pk_epg_display_name
        primary key (epg_channel_id, server, language),
    index idx_klasjdfasdfw (epg_channel_id, server)
);
# alter table epg_display_name
#     add constraint fk_yZfhh62xma8rhvfdwhph
#         foreign key if not exists (epg_channel_id, server) references epg_channel (epg_channel_id, server)
#             on update cascade on delete cascade;

create table if not exists epg_programme
(
    id             BIGINT primary key auto_increment,
    epg_channel_id VARCHAR(255)                           not null,
    server         VARCHAR(255)                           not null,
    title          TEXT                                   not null,
    subtitle       TEXT                                   not null,
    description    TEXT                                   not null,
    start          VARCHAR(255)                           not null,
    stop           VARCHAR(255)                           not null,
    icon           TEXT,
    created_at     TEXT default '2025-05-01 12:10:55.189' not null,
    updated_at     TEXT default '2025-05-01 12:10:55.189' not null
);
# alter table epg_programme
#     add constraint fk_8jsdfh8sdf8h8sdfh8sdf
#         foreign key if not exists (epg_channel_id, server) references epg_channel (epg_channel_id, server)
#             on update cascade on delete cascade;

create unique index if not exists unq_ftytsdf8sdfh8sdfh8sdf
    on epg_programme (epg_channel_id, server, start);

create table if not exists epg_programme_audio
(
    programme_id BIGINT       not null,
    server       VARCHAR(255) not null,
    type         VARCHAR(255) not null,
    value        VARCHAR(255) not null,
    constraint pk_epg_programme_audio
        primary key (programme_id, type)
);
alter table epg_programme_audio
    add constraint fk_podosjdfh8sdfh8sdfh8sdf
        foreign key if not exists (programme_id) references epg_programme (id)
            on update cascade on delete cascade;

create table if not exists epg_programme_category
(
    programme_id BIGINT       not null,
    server       VARCHAR(255) not null,
    language     VARCHAR(255) not null,
    category     VARCHAR(255) not null,
    constraint pk_epg_programme_category
        primary key (programme_id, language, category)
);
alter table epg_programme_category
    add constraint fk_lakdfjh8sdfh8sdfh8sdf
        foreign key if not exists (programme_id) references epg_programme (id)
            on update cascade on delete cascade;


create table if not exists epg_programme_episode_number
(
    programme_id BIGINT       not null,
    server       VARCHAR(255) not null,
    system       VARCHAR(255),
    number       VARCHAR(255) not null,
    constraint pk_epg_programme_episode_number
        primary key (programme_id, system)
);
alter table epg_programme_episode_number
    add constraint fk_pjasdfh8sdfh8sdfh8sdf
        foreign key if not exists (programme_id) references epg_programme (id)
            on update cascade on delete cascade;

create table if not exists epg_programme_previously_shown
(
    programme_id BIGINT       not null,
    server       VARCHAR(255) not null,
    start        VARCHAR(255) not null,
    constraint pk_epg_programme_previously_shown
        primary key (programme_id, start)
);
alter table epg_programme_previously_shown
    add constraint fk_9jdfe08sdfh8sdfh8sdf
        foreign key if not exists (programme_id) references epg_programme (id)
            on update cascade on delete cascade;

create table if not exists epg_programme_rating
(
    programme_id BIGINT       not null,
    server       VARCHAR(255) not null,
    system       VARCHAR(255) not null,
    rating       VARCHAR(255) not null,
    constraint pk_epg_programme_rating
        primary key (programme_id, system)
);
alter table epg_programme_rating
    add constraint fk_plasdjolkjsmgjdfg
        foreign key if not exists (programme_id) references epg_programme (id)
            on update cascade on delete cascade;

create table if not exists epg_programme_subtitles
(
    programme_id BIGINT       not null,
    server       VARCHAR(255) not null,
    type         VARCHAR(255) not null,
    constraint pk_epg_programme_subtitles
        primary key (programme_id, type)
);
alter table epg_programme_subtitles
    add constraint fk_lkjhs45678sdfgdf
        foreign key if not exists (programme_id) references epg_programme (id)
            on update cascade on delete cascade;

create table if not exists iptv_channel
(
    id                 BIGINT
        primary key auto_increment,
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

create index if not exists idx_kljsadfh08sdfh8sdfh8sdf
    on iptv_channel (server, external_stream_id);

create unique index if not exists unq_hsdf7sadfh8sdfh8sdf
    on iptv_channel (server, url);

create table if not exists live_stream
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
    constraint pk_live_stream primary key (server, num)
);

create unique index if not exists unq_uiuoio8sdfh8sdfh8sdf
    on live_stream (server, external_stream_id);

create table if not exists live_stream_category
(
    id                   BIGINT
        primary key auto_increment,
    server               VARCHAR(255)                                   not null,
    external_category_id BIGINT                                         not null,
    category_name        TEXT                                           not null,
    parent_id            VARCHAR(255) default '0'                       not null,
    created_at           TEXT         default '2025-05-01 12:10:55.183' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.183' not null
);
create unique index if not exists unq_jhyyty8sdfh8sdfh8sdf
    on live_stream_category (server, external_category_id);

create table if not exists live_stream_to_category
(
    server      VARCHAR(255) not null,
    num         BIGINT       not null,
    category_id BIGINT       not null,
    constraint pk_live_stream_to_category
        primary key (server, num, category_id)
);
alter table live_stream_to_category
    add constraint fk_sdaoifuasd98f8sdfh8sdf
        foreign key if not exists (server, category_id) references live_stream_category (server, external_category_id)
            on update cascade on delete cascade;

create table if not exists movie
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
        primary key (server, num)
);

create unique index if not exists unq_oidfhjasdufiisdfh8sdf
    on movie (server, external_stream_id);

create table if not exists movie_category
(
    id                   BIGINT
        primary key auto_increment,
    server               VARCHAR(255)                                   not null,
    external_category_id BIGINT                                         not null,
    category_name        TEXT                                           not null,
    parent_id            VARCHAR(255) default '0'                       not null,
    created_at           TEXT         default '2025-05-01 12:10:55.186' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.186' not null
);
alter table movie_category
    add constraint fk_qasdvbnasbpo8sdfasdf
        foreign key if not exists (server) references movie (server)
            on update cascade on delete cascade;

create unique index if not exists unq_lkjohsdf8sdfh8sdfh8sdf
    on movie_category (server, external_category_id);

create table if not exists movie_to_category
(
    server      VARCHAR(255) not null,
    num         BIGINT       not null,
    category_id BIGINT       not null,
    constraint pk_movie_to_category
        primary key (server, num, category_id)
);
alter table movie_to_category
    add constraint fk_lakdsjhf8sdfh8sdfh8sdf
        foreign key if not exists (server, category_id) references movie_category (server, external_category_id)
            on update cascade on delete cascade;

create table if not exists playlist_source
(
    server       VARCHAR(255)                           not null
        primary key,
    created_at   TEXT default '2025-05-01 12:10:55.191' not null,
    started_at   TEXT default '2025-05-01 12:10:55.191' not null,
    completed_at TEXT default '2025-05-01 12:10:55.191' not null
);

create table if not exists series
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

create table if not exists series_category
(
    id                   BIGINT
        primary key auto_increment,
    server               VARCHAR(255)                                   not null,
    external_category_id BIGINT                                         not null,
    category_name        TEXT                                           not null,
    parent_id            VARCHAR(255) default '0'                       not null,
    created_at           TEXT         default '2025-05-01 12:10:55.187' not null,
    updated_at           TEXT         default '2025-05-01 12:10:55.187' not null
);

create unique index if not exists unq_oliksdjf8sdfh8sdfh8sdf
    on series_category (server, external_category_id);

create table if not exists series_to_category
(
    server      VARCHAR(255) not null,
    num         BIGINT       not null,
    category_id BIGINT       not null,
    constraint pk_series_to_category
        primary key (server, num, category_id)
);
alter table series_to_category
    add constraint fk_pohnbmdv8sdfh8sdfh8sdf
        foreign key if not exists (server, num) references series (server, num)
            on update cascade on delete cascade;
alter table series_to_category
    add constraint fk_sfdoijgj8sdfh8sdfh8sdf
        foreign key if not exists (server, category_id) references series_category (server, external_category_id)
            on update cascade on delete cascade;

create table if not exists xmltv_source
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

create table if not exists xtream_source
(
    server       VARCHAR(255)                           not null
        primary key,
    created_at   TEXT default '2025-05-01 12:10:55.191' not null,
    started_at   TEXT default '2025-05-01 12:10:55.192' not null,
    completed_at TEXT default '2025-05-01 12:10:55.192' not null
);
