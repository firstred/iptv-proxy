alter table iptv_channel
    add external_index bigint not null default 0 after id;
create index idx_kusrhyzuxpyu5uxjz3j on iptv_channel (external_index, server);
