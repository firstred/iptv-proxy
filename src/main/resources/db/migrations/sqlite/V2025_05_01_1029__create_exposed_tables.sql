create table app_data
(
    name  varchar(255) not null
        primary key,
    value text         not null
);

create table category
(
    id                   integer
        primary key autoincrement,
    server               varchar(127)                     not null,
    external_category_id varchar(255)                     not null,
    category_name        text                             not null,
    parent_id            bigint default 0                 not null,
    type                 varchar(31)                      not null,
    created_at           text   default CURRENT_TIMESTAMP not null,
    updated_at           text   default CURRENT_TIMESTAMP not null,
    constraint chk_category_unsigned_integer_id
        check (id between 0 and 4294967295),
    constraint chk_category_unsigned_integer_parent_id
        check (parent_id between 0 and 4294967295)
);

create unique index uniq_438f002bba40a04a4e02faef0137476d
    on category (server, external_category_id);
create index idx_892d7f30bf0f5e3b716cc5ed3ebdd10e
    on category (server, category_name);

create table channel
(
    id                 integer
        primary key autoincrement,
    external_position  bigint                         not null,
    epg_channel_id     varchar(255)                   null,
    url                text                           not null,
    xtream_stream_id   bigint                         null,
    server             varchar(127)                   not null,
    icon               text,
    name               text                           not null,
    main_group         text,
    groups             json                           not null,
    catchup_days       bigint,
    m3u_props          json                           not null,
    vlc_opts           json                           not null,
    type               varchar(31)                    not null,
    created_at         text default CURRENT_TIMESTAMP not null,
    updated_at         text default CURRENT_TIMESTAMP not null,
    constraint chk_channel_unsigned_integer_catchup_days
        check (catchup_days between 0 and 4294967295),
    constraint chk_channel_unsigned_integer_external_position
        check (external_position between 0 and 4294967295),
    constraint chk_channel_unsigned_integer_external_stream_id
        check (xtream_stream_id between 0 and 4294967295),
    constraint chk_channel_unsigned_integer_id
        check (id between 0 and 4294967295)
);

create unique index uniq_037df95408f7de3c48f8ff391f86c3df
    on channel (server, xtream_stream_id, url);
create index idx_81e0d67366a2ec3bed4ad79abd8f8940
    on channel (server, external_position);
create index idx_82c78113ffde7ad2c641b8b94b4afa95
    on channel (server, main_group);

create table epg_channel
(
    epg_channel_id varchar(255)                   not null,
    icon           text,
    name           varchar(511)                   not null,
    created_at     text default CURRENT_TIMESTAMP not null,
    updated_at     text default CURRENT_TIMESTAMP not null,
    constraint pk_epg_channel
        primary key (epg_channel_id)
);

create table epg_display_name
(
    epg_channel_id varchar(255) not null,
    language       varchar(255) not null,
    name           text         not null,
    constraint pk_epg_display_name
        primary key (epg_channel_id, language)
);

create table epg_programme
(
    epg_channel_id varchar(255)                   not null,
    start          text                           not null,
    stop           text                           not null,
    title          text                           not null,
    subtitle       text                           not null,
    description    text                           not null,
    icon           text,
    created_at     text default CURRENT_TIMESTAMP not null,
    updated_at     text default CURRENT_TIMESTAMP not null,
    constraint pk_epg_programme
        primary key (epg_channel_id, start)
);

create table epg_programme_audio
(
    epg_channel_id  varchar(255) not null,
    programme_start text         not null,
    type            varchar(255) not null,
    value           varchar(255) not null,
    constraint pk_epg_programme_audio
        primary key (epg_channel_id, programme_start, type)
);

create table epg_programme_category
(
    epg_channel_id  varchar(255) not null,
    programme_start text         not null,
    language        varchar(255) not null,
    category        text         not null,
    constraint pk_epg_programme_category
        primary key (epg_channel_id, programme_start, language)
);

create table epg_programme_episode_number
(
    epg_channel_id  varchar(255) not null,
    programme_start text         not null,
    system          varchar(255),
    number          varchar(255) not null,
    constraint pk_epg_programme_episode_number
        primary key (epg_channel_id, programme_start, system)
);

create table epg_programme_previously_shown
(
    epg_channel_id  varchar(255) not null,
    programme_start text         not null,
    previous_start  text         not null,
    constraint pk_epg_programme_previously_shown
        primary key (epg_channel_id, programme_start)
);

create table epg_programme_rating
(
    epg_channel_id  varchar(255) not null,
    programme_start text         not null,
    system          varchar(255) not null,
    rating          varchar(255) not null,
    constraint pk_epg_programme_rating
        primary key (epg_channel_id, programme_start, system)
);

create table epg_programme_subtitles
(
    epg_channel_id  varchar(255) not null,
    programme_start text         not null,
    language        varchar(255) not null,
    subtitle        text         not null,
    constraint pk_epg_programme_subtitles
        primary key (epg_channel_id, programme_start, language)
);

create table live_stream
(
    num                 bigint       default 0                 not null,
    server              varchar(127)                           not null,
    name                text                                   not null,
    external_stream_id  bigint                                 not null,
    icon                text,
    epg_channel_id      varchar(255),
    added               text         default CURRENT_TIMESTAMP not null,
    is_adult            boolean      default 0                 not null,
    main_category_id    bigint,
    custom_sid          varchar(255),
    tv_archive          boolean      default 0                 not null,
    direct_source       varchar(255) default ''                not null,
    tv_archive_duration bigint       default 0                 not null,
    created_at          text         default CURRENT_TIMESTAMP not null,
    updated_at          text         default CURRENT_TIMESTAMP not null,
    thumbnail           text,
    constraint pk_live_stream
        primary key (server, external_stream_id),
    constraint chk_live_stream_unsigned_integer_external_stream_id
        check (external_stream_id between 0 and 4294967295),
    constraint chk_live_stream_unsigned_integer_main_category_id
        check (main_category_id between 0 and 4294967295),
    constraint chk_live_stream_unsigned_integer_num
        check (num between 0 and 4294967295),
    constraint chk_live_stream_unsigned_integer_tv_archive_duration
        check (tv_archive_duration between 0 and 4294967295)
);

create index idx_6c03df766175249bd3424a88c18fa99e
    on live_stream (server, num);

create unique index live_stream_server_external_stream_id
    on live_stream (server, external_stream_id);

create table live_stream_to_category
(
    server             varchar(127) not null,
    external_stream_id bigint       not null,
    category_id        bigint       not null
        constraint fk_8343e7d497dc8ffdbcc4ed31a3e66141
            references category
            on update cascade on delete cascade,
    constraint pk_live_stream_to_category
        primary key (server, external_stream_id, category_id),
    constraint chk_live_stream_to_category_unsigned_integer_category_id
        check (category_id between 0 and 4294967295),
    constraint chk_live_stream_to_category_unsigned_integer_external_stream_id
        check (external_stream_id between 0 and 4294967295)
);

create table movie
(
    num                  bigint  default 0                 not null,
    server               varchar(127)                      not null,
    name                 text                              not null,
    external_stream_id   bigint                            not null,
    external_stream_icon text,
    rating               varchar(255),
    rating_5based        single,
    tmdb                 bigint,
    youtube_trailer      varchar(255),
    added                text    default CURRENT_TIMESTAMP not null,
    is_adult             boolean default 0                 not null,
    main_category_id     bigint,
    container_extension  varchar(16)                       not null,
    custom_sid           varchar(255),
    direct_source        text,
    created_at           text    default CURRENT_TIMESTAMP not null,
    updated_at           text    default CURRENT_TIMESTAMP not null,
    constraint pk_movie
        primary key (server, external_stream_id),
    constraint chk_movie_unsigned_integer_external_stream_id
        check (external_stream_id between 0 and 4294967295),
    constraint chk_movie_unsigned_integer_main_category_id
        check (main_category_id between 0 and 4294967295),
    constraint chk_movie_unsigned_integer_num
        check (num between 0 and 4294967295),
    constraint chk_movie_unsigned_integer_tmdb
        check (tmdb between 0 and 4294967295)
);

create index idx_8aa06220d55032bf3701990defb691b6
    on movie (server, num);

create table movie_to_category
(
    server             varchar(127) not null,
    external_stream_id bigint       not null,
    category_id        bigint       not null
        constraint fk_bf79cb5f218687462a48b89ea961ead3
            references category
            on update cascade on delete cascade,
    constraint pk_movie_to_category
        primary key (server, external_stream_id, category_id),
    constraint chk_movie_to_category_unsigned_integer_category_id
        check (category_id between 0 and 4294967295),
    constraint chk_movie_to_category_unsigned_integer_external_stream_id
        check (external_stream_id between 0 and 4294967295)
);

create table playlist_source
(
    server              varchar(127)                   not null
        primary key,
    created_at          text default CURRENT_TIMESTAMP not null,
    started_import_at   text default CURRENT_TIMESTAMP not null,
    completed_import_at text default CURRENT_TIMESTAMP not null
);

create table series
(
    num              bigint default 0                 not null,
    server           varchar(127)                     not null,
    name             text                             not null,
    series_id        bigint                           not null,
    cover            text                             not null,
    plot             text                             not null,
    cast             text                             not null,
    main_category_id bigint,
    director         text                             not null,
    genre            text                             not null,
    release_date     varchar(255)                     not null,
    last_modified    varchar(255)                     not null,
    rating           text                             not null,
    rating_5based    single                           not null,
    backdrop_path    text,
    youtube_trailer  varchar(255),
    tmdb             bigint,
    episode_run_time varchar(255),
    created_at       text   default CURRENT_TIMESTAMP not null,
    updated_at       text   default CURRENT_TIMESTAMP not null,
    constraint pk_series
        primary key (server, series_id),
    constraint chk_series_unsigned_integer_main_category_id
        check (main_category_id between 0 and 4294967295),
    constraint chk_series_unsigned_integer_num
        check (num between 0 and 4294967295),
    constraint chk_series_unsigned_integer_series_id
        check (series_id between 0 and 4294967295),
    constraint chk_series_unsigned_integer_tmdb
        check (tmdb between 0 and 4294967295)
);

create index idx_01232b0855d37101b5903869e9b86d7d
    on series (server, num);

create table series_to_category
(
    server             varchar(127) not null,
    external_series_id bigint       not null,
    category_id        bigint       not null,
    constraint pk_series_to_category
        primary key (server, external_series_id, category_id),
    constraint chk_series_to_category_unsigned_integer_category_id
        check (category_id between 0 and 4294967295),
    constraint chk_series_to_category_unsigned_integer_external_series_id
        check (external_series_id between 0 and 4294967295)
);

create table xmltv_source
(
    server              varchar(127)                   not null
        primary key,
    generator_info_name text,
    generator_info_url  text,
    source_info_url     text,
    source_info_name    text,
    source_info_logo    text,
    created_at          text default CURRENT_TIMESTAMP not null,
    started_import_at   text default CURRENT_TIMESTAMP not null,
    completed_import_at text default CURRENT_TIMESTAMP not null
);

create table xtream_source
(
    server              varchar(127)                   not null
        primary key,
    created_at          text default CURRENT_TIMESTAMP not null,
    started_import_at   text default CURRENT_TIMESTAMP not null,
    completed_import_at text default CURRENT_TIMESTAMP not null
);

