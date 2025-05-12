create table epg_display_name_dg_tmp
(
    epg_channel_id varchar(255) not null
        constraint fk_c86aeb2cab6d79ed0a8af2540d612b58
            references epg_channel
            on update cascade on delete cascade,
    language       varchar(255) not null,
    name           text         not null,
    constraint pk_epg_display_name
        primary key (epg_channel_id, language)
);

insert into epg_display_name_dg_tmp(epg_channel_id, language, name)
select epg_channel_id, language, name
from epg_display_name;

drop table epg_display_name;

alter table epg_display_name_dg_tmp
    rename to epg_display_name;


create table epg_programme_audio_dg_tmp
(
    epg_channel_id  varchar(255) not null,
    programme_start bigint       not null,
    type            varchar(255) not null,
    value           varchar(255) not null,
    constraint pk_epg_programme_audio
        primary key (epg_channel_id, programme_start, type),
    constraint fk_90d524752a165c4f6102c4e094bf5d77
        foreign key (epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

insert into epg_programme_audio_dg_tmp(epg_channel_id, programme_start, type, value)
select epg_channel_id, programme_start, type, value
from epg_programme_audio;

drop table epg_programme_audio;

alter table epg_programme_audio_dg_tmp
    rename to epg_programme_audio;


create table epg_programme_category_dg_tmp
(
    epg_channel_id  varchar(255) not null,
    programme_start bigint       not null,
    language        varchar(255) not null,
    category        text         not null,
    constraint pk_epg_programme_category
        primary key (epg_channel_id, programme_start, language),
    constraint fk_5df66088afd5f081accb86389840f487
        foreign key (epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

insert into epg_programme_category_dg_tmp(epg_channel_id, programme_start, language, category)
select epg_channel_id, programme_start, language, category
from epg_programme_category;

drop table epg_programme_category;

alter table epg_programme_category_dg_tmp
    rename to epg_programme_category;


create table epg_programme_episode_number_dg_tmp
(
    epg_channel_id  varchar(255) not null,
    programme_start bigint       not null,
    system          varchar(255),
    number          varchar(255) not null,
    constraint pk_epg_programme_episode_number
        primary key (epg_channel_id, programme_start, system),
    constraint fk_80743019a125679f040323c328b9bf4b
        foreign key (epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

insert into epg_programme_episode_number_dg_tmp(epg_channel_id, programme_start, system, number)
select epg_channel_id, programme_start, system, number
from epg_programme_episode_number;

drop table epg_programme_episode_number;

alter table epg_programme_episode_number_dg_tmp
    rename to epg_programme_episode_number;


create table epg_programme_previously_shown_dg_tmp
(
    epg_channel_id  varchar(255) not null,
    programme_start bigint       not null,
    previous_start  bigint       not null,
    constraint pk_epg_programme_previously_shown
        primary key (epg_channel_id, programme_start),
    constraint fk_bfe203b51fd515489b5f86e99b056d6e
        foreign key (epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

insert into epg_programme_previously_shown_dg_tmp(epg_channel_id, programme_start, previous_start)
select epg_channel_id, programme_start, previous_start
from epg_programme_previously_shown;

drop table epg_programme_previously_shown;

alter table epg_programme_previously_shown_dg_tmp
    rename to epg_programme_previously_shown;


create table epg_programme_rating_dg_tmp
(
    epg_channel_id  varchar(255) not null,
    programme_start bigint       not null,
    system          varchar(255) not null,
    rating          varchar(255) not null,
    constraint pk_epg_programme_rating
        primary key (epg_channel_id, programme_start, system),
    constraint fk_48f5df6691c954f66dec552298bc5e6c
        foreign key (epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

insert into epg_programme_rating_dg_tmp(epg_channel_id, programme_start, system, rating)
select epg_channel_id, programme_start, system, rating
from epg_programme_rating;

drop table epg_programme_rating;

alter table epg_programme_rating_dg_tmp
    rename to epg_programme_rating;


create table epg_programme_subtitles_dg_tmp
(
    epg_channel_id  varchar(255) not null,
    programme_start bigint       not null,
    language        varchar(255) not null,
    subtitle        text         not null,
    constraint pk_epg_programme_subtitles
        primary key (epg_channel_id, programme_start, language),
    constraint fk_5b7c2c5ef4f7c44103c552bc9b23268f
        foreign key (epg_channel_id, programme_start) references epg_programme
            on update cascade on delete cascade
);

insert into epg_programme_subtitles_dg_tmp(epg_channel_id, programme_start, language, subtitle)
select epg_channel_id, programme_start, language, subtitle
from epg_programme_subtitles;

drop table epg_programme_subtitles;

alter table epg_programme_subtitles_dg_tmp
    rename to epg_programme_subtitles;
