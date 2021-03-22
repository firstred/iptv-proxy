# Overview

This project is a simple iptv restreamer. For now it supports only HLS (m3u8) streams.
Some iptv providers allow to connect only one device per url and this is not really
comfortable when you have 3+ tv. Iptv-proxy allocates such 'urls' dynamically. I.e. your
iptv provider have two urls (playlists) and allows only one connection per url, but
you have 4 tv in your house and you never watch more then 2 tv at the same time.
In this case you can setup two playlists in iptv proxy and they will be dynamically
allocated to active tv.

Also iptvproxy can combine different iptv providers. It will combine both - playlist and xmltv data (if available).

## Configuration

```yaml
host: 127.0.0.1
port: 8080
base_url: http://127.0.0.1:8080
forwarded_pass: password
token_salt: 6r8bt67ta5e87tg7afn
channels_timeout_sec: 5
channels_total_timeout_sec: 60
channels_retry_delay_ms: 1000
servers:
  - name: someiptv-1
    connections:
      - url: https://someiptv.com/playlist.m3u
        max_connections: 1
  - name: someiptv-2
    connections:
      - url: https://iptv-proxy.example.com/playlist.m3u
        max_connections: 4
      - url: https://iptv-proxy.example.com/playlist2.m3u
        max_connections: 2
    xmltv_url: https://epg.example.com/epg.xml.gz
    send_user: true
    proxy_stream: true
    channel_failed_ms: 1000
    info_timeout_sec: 2
    info_total_timeout_sec: 3
    info_retry_delay_ms: 500
    catchup_timeout_sec: 5
    catchup_total_timeout_sec: 10
    catchup_retry_delay_ms: 500
    stream_start_timeout_sec: 2
    stream_read_timeout_sec: 2
allow_anonymous: false
users:
  - 65182_login1
  - 97897_login2
```

* `base_url` - url of your service, you may omit this (see forwarded_pass)
* `forwarded_pass` - password for Forwarded header in case iptvproxy is behind proxy
* `token_salt` - just random chars, they are used to create encrypted tokens
* `channels_timeout_sec` - timeout for single request (default is 5 sec) 
* `channels_total_timeout_sec` - total timeout for channels loading (default is 60 sec)
* `channels_retry_delay_ms` - delay between requests (default is 1000 ms)
* `max_connections` - max active connections allowed for this playlist
* `send_user` - this is useful only when you're using cascade config - iptv-proxy behind iptv-proxy.
If 'true' then iptv-proxy will send current user name in special http header.
We need this to identify device (endpoint) - this will help us to handle max connections and
channel switching properly.
* `proxy_stream` - true (default) means proxy all data through own server,
false means using direct urls for data
* `channel_failed_ms` - on channel failure (error on downloading current m3u8 info)
it will be marked as 'failed' for some time and will be not used for any subsequent requests.
This feature should be enabled for last iptvproxy in chain (the one which connects to your iptv service)
and should be disabled in other situation
* `info_timeout_sec` - timeout for single request (default is 2 sec)
* `info_total_timeout_sec` - some providers may return 404 http error on m3u8 request. This setting
will trigger automatic request retry. We'll be trying to make additional requests for this period. (default is 3 sec).
* `info_retry_delay_ms` - delay in milliseconds between retries (default is 500 ms).
* `catchup_timeout_sec` - same as `info_timeout_sec` but used only with catchup (channel archive, timeshift, default is 5 sec).
* `catchup_total_timeout_sec` - same as `info_total_timeout_sec` but used only with catchup (default is 10 sec).
* `catchup_retry_delay_ms` - same as `info_retry_delay_ms` but used only with catchup (default is 500ms).
* `stream_start_timeout_sec` - timeout for starting actually streaming data (default is 2 sec)
* `stream_read_timeout_sec` - read timeout during streaming - time between any data packets (default is 2 sec) 
* `allow_anonymous` - allow to connect any device without specific user name.
It is not good idea to use such setup. You really should add name for each device you're using.

iptv proxy will embed full urls in it's lists - it means we should know url from which service is accessed by user.
Url is calculated in following way:
* if forwarded_pass is enabled url is taken from Forwarded header
(nginx setup: `proxy_set_header Forwarded "pass=PASS;baseUrl=https://$host";`).
Password must match setting in iptvproxy config
* base_url is used in case it defined
* schema host and port from request is used (will not work in case iptvproxy is behind proxy)

## Device setup

On device you should use next url as dynamic playlist:

`<base_url>/m3u/<user_name>`

or

`<base_url>/m3u`

for anonymous access.

For xmltv you should use `<base_url>/epg.xml.gz`
