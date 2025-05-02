create table app_data
(
    name  varchar(255) not null
        primary key,
    value text         not null
);

create table epg_channel
(
    epg_channel_id varchar(255)                           not null,
    server         varchar(255)                           not null,
    icon           text,
    name           varchar(511)                           not null,
    created_at     text default '2025-05-01 12:10:55.188' not null,
    updated_at     text default '2025-05-01 12:10:55.188' not null,
    constraint pk_epg_channel
        primary key (server, epg_channel_id)
);
create index idx_hCn36jexzfx9cipkfy0l
    on epg_channel (server, name);

create table epg_display_name
(
    epg_channel_id varchar(255) not null,
    server         varchar(255) not null,
    language       varchar(255) not null,
    name           text         not null,
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
    id             integer
        primary key autoincrement,
    epg_channel_id varchar(255)                           not null,
    server         varchar(255)                           not null,
    title          text                                   not null,
    subtitle       text                                   not null,
    description    text                                   not null,
    start          text                                   not null,
    stop           text                                   not null,
    icon           text,
    created_at     text default '2025-05-01 12:10:55.189' not null,
    updated_at     text default '2025-05-01 12:10:55.189' not null,
    constraint fk_8jsdfh8sdf8h8sdfh8sdf
        foreign key (epg_channel_id, server) references epg_channel (epg_channel_id, server)
            on update cascade on delete cascade
);
create unique index unq_ftytsdf8sdfh8sdfh8sdf
    on epg_programme (epg_channel_id, server, start);

create table epg_programme_audio
(
    programme_id bigint       not null
        constraint fk_podosjdfh8sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       varchar(255) not null,
    type         varchar(255) not null,
    value        varchar(255) not null,
    constraint pk_epg_programme_audio
        primary key (programme_id, type)
);

create table epg_programme_category
(
    programme_id bigint       not null
        constraint fk_lakdfjh8sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       varchar(255) not null,
    language     varchar(255) not null,
    category     varchar(255) not null,
    constraint pk_epg_programme_category
        primary key (programme_id, language, category)
);

create table epg_programme_episode_number
(
    programme_id bigint       not null
        constraint fk_pjasdfh8sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       varchar(255) not null,
    system       varchar(255),
    number       varchar(255) not null,
    constraint pk_epg_programme_episode_number
        primary key (programme_id, system)
);

create table epg_programme_previously_shown
(
    programme_id bigint       not null
        constraint fk_9jdfe08sdfh8sdfh8sdf
            references epg_programme
            on update cascade on delete cascade,
    server       varchar(255) not null,
    start        text         not null,
    constraint pk_epg_programme_previously_shown
        primary key (programme_id, start)
);

create table epg_programme_rating
(
    programme_id bigint       not null
        constraint fk_plasdjolkjsmgjdfg
            references epg_programme
            on update cascade on delete cascade,
    server       varchar(255) not null,
    system       varchar(255) not null,
    rating       varchar(255) not null,
    constraint pk_epg_programme_rating
        primary key (programme_id, system)
);

create table epg_programme_subtitles
(
    programme_id bigint       not null
        constraint fk_lkjhs45678sdfgdf
            references epg_programme
            on update cascade on delete cascade,
    server       varchar(255) not null,
    type         varchar(255) not null,
    constraint pk_epg_programme_subtitles
        primary key (programme_id, type)
);

create table iptv_channel
(
    id                 integer
        primary key autoincrement,
    epg_channel_id     varchar(255)                           not null,
    url                text                                   not null,
    external_stream_id varchar(255),
    server             varchar(255)                           not null,
    icon               text,
    name               text                                   not null,
    main_group         text,
    groups             text,
    catchup_days       bigint,
    type               varchar(255)                           not null,
    created_at         text default '2025-05-01 12:10:55.191' not null,
    updated_at         text default '2025-05-01 12:10:55.191' not null
);
create index idx_kljsadfh08sdfh8sdfh8sdf
    on iptv_channel (server, external_stream_id);
create unique index unq_hsdf7sadfh8sdfh8sdf
    on iptv_channel (server, url);

create table live_stream
(
    num                 bigint                                         not null,
    server              varchar(255)                                   not null,
    name                text                                           not null,
    external_stream_id  varchar(255)                                   not null,
    icon                text,
    epg_channel_id      varchar(255),
    added               varchar(255) default '0'                       not null,
    is_adult            int          default 0                         not null,
    main_category_id    bigint,
    custom_sid          varchar(255),
    tv_archive          int          default 0                         not null,
    direct_source       varchar(255) default ''                        not null,
    tv_archive_duration int          default 0                         not null,
    created_at          text         default '2025-05-01 12:10:55.185' not null,
    updated_at          text         default '2025-05-01 12:10:55.185' not null,
    constraint pk_live_stream
        primary key (server, num),
    constraint chk_live_stream_signed_integer_is_adult
        check (is_adult between -2147483648 and 2147483647),
    constraint chk_live_stream_signed_integer_tv_archive
        check (tv_archive between -2147483648 and 2147483647),
    constraint chk_live_stream_signed_integer_tv_archive_duration
        check (tv_archive_duration between -2147483648 and 2147483647)
);

create unique index unq_uiuoio8sdfh8sdfh8sdf
    on live_stream (server, external_stream_id);

create table category
(
    id                   integer
        primary key autoincrement,
    server               varchar(255)                                   not null,
    external_category_id bigint                                         not null,
    category_name        text                                           not null,
    parent_id            varchar(255) default '0'                       not null,
    type                 varchar(255)                                   not null,
    created_at           text         default '2025-05-01 12:10:55.183' not null,
    updated_at           text         default '2025-05-01 12:10:55.183' not null
);
create unique index fk_asoidfuas8df8sdfh8sdf
    on category (server, external_category_id);

create table live_stream_to_category
(
    server      varchar(255) not null,
    num         bigint       not null,
    category_id bigint       not null
        constraint fk_sdaoifuasd98f8sdfh8sdf
            references category
            on update cascade on delete cascade,
    constraint pk_live_stream_to_category
        primary key (server, num, category_id),
    constraint fk_dfopijds98f8sdfh8sdf
        foreign key (server, num) references live_stream
            on update cascade on delete cascade
);

create table movie
(
    num                  bigint                                         not null,
    server               varchar(255)                                   not null,
    name                 text                                           not null,
    external_stream_id   varchar(255)                                   not null,
    external_stream_icon text,
    rating               varchar(255),
    rating_5based        DOUBLE PRECISION,
    tmdb                 varchar(255),
    trailer              varchar(255),
    added                varchar(255) default '0'                       not null,
    is_adult             int          default 0                         not null,
    main_category_id     bigint,
    container_extension  varchar(255)                                   not null,
    custom_sid           varchar(255),
    direct_source        text,
    created_at           text         default '2025-05-01 12:10:55.186' not null,
    updated_at           text         default '2025-05-01 12:10:55.186' not null,
    constraint pk_movie
        primary key (server, num),
    constraint chk_movie_signed_integer_is_adult
        check (is_adult between -2147483648 and 2147483647)
);
create unique index unq_oidfhjasdufiisdfh8sdf
    on movie (server, external_stream_id);

create table movie_to_category
(
    server      varchar(255) not null,
    num         bigint       not null,
    category_id bigint       not null
        constraint fk_lakdsjhf8sdfh8sdfh8sdf
            references category
            on update cascade on delete cascade,
    constraint pk_movie_to_category
        primary key (server, num, category_id),
    constraint fk_lkdasjf8sdfh8sdfh8sdf
        foreign key (server, num) references movie
            on update cascade on delete cascade
);

create table playlist_source
(
    server       varchar(255)                           not null
        primary key,
    created_at   text default '2025-05-01 12:10:55.191' not null,
    started_at   text default '2025-05-01 12:10:55.191' not null,
    completed_at text default '2025-05-01 12:10:55.191' not null
);

create table series
(
    num              bigint                                 not null,
    server           varchar(255)                           not null,
    name             text                                   not null,
    series_id        varchar(255)                           not null,
    cover            text                                   not null,
    plot             text                                   not null,
    cast             text                                   not null,
    main_category_id bigint,
    director         text                                   not null,
    genre            text                                   not null,
    release_date     varchar(255)                           not null,
    last_modified    varchar(255)                           not null,
    rating           text                                   not null,
    rating_5based    text                                   not null,
    backdrop_path    text,
    youtube_trailer  varchar(255),
    tmdb             varchar(255),
    episode_run_time varchar(255),
    created_at       text default '2025-05-01 12:10:55.187' not null,
    updated_at       text default '2025-05-01 12:10:55.187' not null,
    constraint pk_series
        primary key (server, num)
);

create table series_to_category
(
    server      varchar(255) not null,
    num         bigint       not null,
    category_id bigint       not null,
    constraint pk_series_to_category
        primary key (server, num, category_id),
    constraint fk_sfdoijgj8sdfh8sdfh8sdf
        foreign key (server, category_id) references category (server, external_category_id)
            on update cascade on delete cascade,
    constraint fk_pohnbmdv8sdfh8sdfh8sdf
        foreign key (server, num) references series
            on update cascade on delete cascade
);

create table xmltv_source
(
    server              varchar(255)                           not null
        primary key,
    generator_info_name text,
    generator_info_url  text,
    source_info_url     text,
    source_info_name    text,
    source_info_logo    text,
    created_at          text default '2025-05-01 12:10:55.191' not null,
    started_at          text default '2025-05-01 12:10:55.191' not null,
    completed_at        text default '2025-05-01 12:10:55.191' not null
);

create table xtream_source
(
    server       varchar(255)                           not null
        primary key,
    created_at   text default '2025-05-01 12:10:55.191' not null,
    started_at   text default '2025-05-01 12:10:55.192' not null,
    completed_at text default '2025-05-01 12:10:55.192' not null
);
