# Overview

This project is a simple IPTV restreamer, rework of https://github.com/kvaster/iptv-proxy. For now, it supports only HLS (m3u8) streams.
Some IPTV providers allow to connect only one device per URL and this is not really
comfortable when you have 3+ tv. Iptv-proxy allocates such 'URLs' dynamically. I.e. your
iptv provider have two URLs (playlists) and allows only one connection per URL, but
you have 4 TVs in your house, and you never watch more than 2 tv at the same time.
In this case you can set up two playlists in iptv proxy, and they will be dynamically
allocated to active tv.

Also, iptv-proxy can combine different IPTV providers. It will combine both - playlist and xmltv data (if available).

## Configuration

```yaml
x-default-timeouts: &default-timeouts
  connect_milliseconds: 60_000
  socket_milliseconds: 300_000
  total_milliseconds: 300_000
  retry_delay_milliseconds: 5_000
  max_retries: 3

x-icon-timeouts: &icon-timeouts
  connect_milliseconds: 2_000
  socket_milliseconds: 2_000
  total_milliseconds: 10_000
  retry_delay_milliseconds: 1_000
  max_retries: 3

x-channel-timeouts: &channel-timeouts
  connect_milliseconds: 5_000
  socket_milliseconds: 30_000
  total_milliseconds: 60_000
  retry_delay_milliseconds: 2_000
  max_retries: 3

host: 127.0.0.1 # IPv4 or IPv6 address to bind to, use `::` (dual-stack) or `0.0.0.0` (IPv4 only) to bind to all interfaces
port: ${PORT:-8080}
base_url: http://127.0.0.1:8888
app_secret: "Changeme!"
log_level: ${IPTV_PROXY_LOG_LEVEL:-ERROR}
# database:
#   jdbc_url: jdbc:sqlite:iptv.db
#   maximum_pool_size: 6
#  chunk_size: 1_000
timeouts:
  playlist: *default-timeouts
  icon: *icon-timeouts
cache:
  enabled: true
  ttl:
    video_chunk: PT1M
    movie_info: P1D
    series_info: PT1H
    images: P30D # Time to live for icons
  size:
    video_chunks: 512_000_000 # 512MB - size is in bytes
    vod_info: 50_000_000 # 50MB - size is in bytes
    movie_info: 50_000_000 # 50MB - size is in bytes
client_connection_max_idle_seconds: 60 # Max idle time for client connections -- important to free up user connections
update_interval: PT6H
update_interval_on_failure: PT10M # Interval to retry failed updates
cleanup_interval: PT1H # Interval to clean up old data
channel_max_stale_period: P2D # Wait time before a stale channel is removed from the database
scheduler_thread_pool_size: 2 # Max. concurrent updater threads for the playlist and EPG
sort_channels_by_name: false
trim_epg: true  # Removes unavailable channels from the EPG
# socks_proxy: null # socks5://<username>:<password>@server_host:port
# http_proxy: null # http://<username>:<password>@server_host:port
# cors: # Configure CORS response from the server - e.g. for direct access to the API from a web browser
#   enabled: true
#   allow_origins: ["*"]
#   allow_headers:
#     - "Content-Type"
#     - "Authorization"
#     - "X-Requested-With"
#     - "Accept"
#     - "Origin"
#     - "User-Agent"
#   allow_header_prefixes:
#     - "X-Custom-"
#   allow_methods:
#     - GET
#     - OPTIONS
#   allow_credentials: true
#   max_age: 3600
#   expose_headers:
#     - "X-Total-Count"
# whitelist_iptv_client_headers:
#  - "User-Agent" # Allows additional headers from the IPTV client to be passed to the server - default: allow all
# blacklist_iptv_client_headers:
#   - l5d-dst-override
#   - l5d-connection-secure
#   - l5d-remote-ipv6
#   - l5d-client-id
# whitelist_iptv_server_headers:
#  - "X-Subscription-Expiry" # Allows additional headers from the IPTV server to be passed to the client - default: allow all
servers:
  - name: someiptv-1
    accounts:
      - url: https://someiptv.com/                # Xtream API server without the `.get.php`, `api.php`, `xmltv.php`, etc.
        xtream_username: 1234567890abcdef         # Xtream username
        xtream_password: aslkdfadsf               # Xtream password
        max_concurrent_requests: 3                # General max requests for this account
        max_concurrent_requests_per_host: 1       # Server specific max requests per hosts when requesting playback chunks
        user_agent: "TiviMate/5.1.6 (Android 14)" # Force a specific user agent for all requests with this account
      - url: https://someiptv.com/playlist.m3u # Direct playlist URL
        login: account                         # HTTP Basic Auth login
        password: password                     # HTTP Basic Auth password
        max_concurrent_requests: 3
        max_requests_per_host: 1               # Server specific max requests per hosts when requesting playback chunks
    epg_url: https://epg.example.com/epg.xml.gz
    epg_before: P7D
    epg_after: P7D
    proxy_stream: true
    proxy_forwarded_user: true
    proxy_forwarded_pass: "test"
    epg_remapping:
      "Old.News": "new.news"
    timeouts: *channel-timeouts
#    group_filters:
#      - 'movies'
#      - 'vid.*'
users:
  - username: 12345
    password: login1
    max_connections: 4 # Default is 1
  - username: 123456
    password: login2
  - username: 1234567
    password: login3
```

### Top level settings
* `host` - IPv4 or IPv6 address to bind to, use `::` (dual-stack) or `
* `port` - port to bind to
* `base_url` - base URL of your service, you may omit this (see `forwarded_pass`) - this URL will be used for every link generated in e.g. playlists
* `forwarded_pass` - password for `Forwarded` header in case iptv-proxy is behind a proxy and you'd like to use a dynamic different base_url
* `app_secret` - just random chars, they are used to create encrypted tokens
* `log_level` - log level, possible values: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`

* `client_connection_max_idle_seconds` - max idle time (no packets at all) for client connections (default is 60 seconds)
* `update_interval` - interval to update the playlist and EPG data (default is 6 hours)
* `update_interval_on_failure` - interval to retry failed updates (default is 10 minutes)
* `cleanup_interval` - interval to clean up old data (default is 1 hour)
* `update_on_startup` - whether the updater should schedule immediately (default is `true`)
* `channel_max_stale_period` - wait time before a stale channel is removed from the database (default is 2 days). A channel is considered stale if it (temporarily) wasn't on the remote playlist.

* `scheduler_thread_pool_size` - max. concurrent updater threads for the playlist and EPG (default is `2`)

* `sort_channels_by_name` - sort channels by name (default is `false`)
* `trim_epg` - removes unavailable channels from the EPG (default is `true`)

* `socks_proxy` - socks5 proxy for outgoing requests (default is `null`)
* `http_proxy` - http proxy for outgoing requests (default is `null`)

* `cors` - CORS settings for the server (default is `null`)
  * `enabled` - enable CORS (default is `true`)
  * `allow_origins` - allowed origins (default is ["*"])
  * `allow_headers` - allowed headers (default is `["Content-Type", "Authorization", "Accept", "X-Requested-With", "Origin", "User-Agent", "Referer", "Accept-Encoding", "Accept-Language", "DNT", "Cache-Control"]`)
  * `allow_header_prefixes` - allowed header prefixes (default is [])
  * `allow_methods` - allowed methods (default is `["GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"]`)
  * `allow_credentials` - allow credentials (default is `true`)
  * `max_age` - max age for preflight requests (default is `3600`)
  * `expose_headers` - exposed headers (default is `["Content-Type", "Authorization", "Accept", "X-Requested-With", "Origin", "User-Agent", "Referer", "Accept-Encoding", "Accept-Language", "DNT", "Cache-Control"]`)

* `whitelist_iptv_client_headers` - headers to whitelist from the client (default is `["*"]`)
* `blacklist_iptv_client_headers` - headers to blacklist from the client (default is `null`)
* `whitelist_iptv_server_headers` - headers to whitelist from the server (default is `["*"]`)
* `blacklist_iptv_server_headers` - headers to blacklist from the server (default is `null`)

### Database settings `database`
* `jdbc_url` - jdbc url for the database (default is `jdbc:sqlite::memory:`) - use a file path to persist the database:
    * `jdbc:sqlite:iptv.db` - sqlite database in the current directory
    * `jdbc:sqlite:/path/to/iptv.db` - sqlite database in the specified directory
* `maximum_pool_size` - maximum pool size for the database (default is `6`)
* `chunk_size` - chunk size for the database (default is `1000`)

### Cache settings
* `enabled` - enable cache (default is `true`)
* `ttl` - time to live for cached data
  * `video_chunks` - time to live for video chunks (default is 2 minutes)
  * `movie_info` - time to live for movie info (default is 1 day)
  * `series_info` - time to live for series info (default is 1 hour)
  * `images` - time to live for icons (default is 30 days)
* `size` - size of the cache (cache is disabled by default)
  * `video_chunks` - size of the cache for video chunks (default is 512MB)
  * `movie_info` - size of the cache for movie info (default is 50MB)
  * `series_info` - size of the cache for series info (default is 50MB)
### Timeout settings (`timeouts[]`)
* `playlist` - timeouts for playlist requests
* `icon` - timeouts for icon requests
* `channel` - timeouts for direct channel requests - live, movies and series

#### Timeout values (`timeouts[].*`)
* `connect_milliseconds` - timeout for the initial connection to the server
* `socket_milliseconds` - timeout for socket reads (i.e. the time between any data packets - useful to detect dead connections)
* `total_milliseconds` - total timeout for the request
* `retry_delay_milliseconds` - delay between retries
* `max_retries` - max retries for failed requests

### Server settings (`servers[]`)
* `proxy_forwarded_user` - this is useful only when you're using cascade config - iptv-proxy behind iptv-proxy.
If 'true' then iptv-proxy will send current username in a special http header: `Forwarded`.
We need this to identify device (endpoint) - this will help us to handle max connections and channel switching properly.
* `proxy_forwarded_pass` - this is useful only when you're using cascade config - iptv-proxy behind iptv-proxy.
* `proxy_stream` - `true` (default) means proxy all data through own server,
false means using direct URLs for data
* `epg_url` - URL for xmltv data, epg for different servers will be reprocessed and combined to one file for all channels
* `epg_after` - filter programmes after specified time (to reduce xmltv size), java duration format (`P1D` - one day), default - unlimited
* `epg_before` - filter programmes before specified time (to reduce xmltv size), java duration format (`P5D` - five days), default - unlimited
* `timeouts` - timeouts for this server, see `timeouts[].*`
* `group_filters` - list of regex channel filters
* `epg_remapping` - allows to remap the server's EPG IDs to new ones

#### Account settings (`servers[].accounts[]`)
* `url` - url for the server, this is either the direct playlist URL or the Xtream API URL. In the latter case the `.get.php` or `.api.php` suffix should be omitted.
* `login` - login for basic authentication (useful for tvheadend IPTV playlists)
* `password` - password for basic authentication (useful for tvheadend IPTV playlists)
* `xtream_username` - username for the xtream api
* `xtream_password` - password for the xtream api
* `max_concurrent_requests` - max concurrent requests for this account
* `max_concurrent_requests_per_host` - max concurrent requests per remote host for this account

### Users settings (`users[]`)
* `username` - username for the user
* `password` - password for the user
* `max_connections` - max connections for the user (default is 1)

IPTV proxy will embed full URLs in its lists - it means we should know the URL from which the service is accessed by the user.
URL is calculated in following way:
* If forwarded_pass is enabled URL is taken from Forwarded header
(nginx setup: `proxy_set_header Forwarded "pass=PASS;baseUrl=https://$host";`).
* Multiple headers are supported as well, e.g. `Forwarded: pass=PASS;baseUrl=https://$host` and `Forwarded: pass=PASS;proxyUser=user`
Password must match setting in iptv-proxy config
* `base_url` is used in case it defined
* Schema, host and port from the request is used

## Device setup

There are two ways to set up a device:
1. Use the direct link to a playlist
2. Use the xtream codes api

### 1. Direct link to a playlist

On a device you should use this URL to access the playlist:

`<base_url>/get.php?username=<user_name>&password=<password>&type=m3u_plus&output=m3u8`

For xmltv you should use `<base_url>/xmltv.php?username=<user_name>&password=<password>`

### 2. Xtream codes api

On a device you should use this URL to access the playlist:

- Xtream server URL: `<base_url>`
- Xtream username: `<user_name>`
- Xtream password: `<password>`
