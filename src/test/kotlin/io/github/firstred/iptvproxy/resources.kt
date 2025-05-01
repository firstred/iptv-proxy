package io.github.firstred.iptvproxy

val testConfig = """
x-default-timeouts: &default-timeouts
  connect_milliseconds: 600_000
  socket_milliseconds: 6_000_000
  total_milliseconds: 3_000_000
  retry_delay_milliseconds: 1_000
  max_retries: 3

x-icon-timeouts: &icon-timeouts
  connect_milliseconds: 500_000
  socket_milliseconds: 6_000_000
  total_milliseconds: 3_000_000
  retry_delay_milliseconds: 1_000
  max_retries: 3

x-channel-timeouts: &channel-timeouts
  connect_milliseconds: 200_000
  socket_milliseconds: 500_000
  total_milliseconds: 1_000_000
  retry_delay_milliseconds: 1_000
  max_retries: 3

host: "127.0.0.1"
port: 8888
healthcheck_port: 9999
metrics_port: 9090
base_url: http://127.0.0.1:8888
app_secret: lkdjfasdfuasd
log_level: INFO
database:
  jdbc_url: "jdbc:sqlite::memory:"
timeouts:
  playlist: *default-timeouts
  icon: *default-timeouts
client_connection_max_idle_seconds: 60
update_interval: PT1H
update_interval_on_failure: PT10M
cleanup_interval: PT1H
scheduler_thread_pool_size: 2
sort_channels: false
http_proxy: http://localhost:8080
blacklist_iptv_client_headers:
  - l5d-dst-override
  - l5d-connection-secure
  - l5d-remote-ipv6
  - l5d-client-id
  - x-forwarded-server
cors: # Configure CORS response from the server - e.g. for direct access to the API from a web browser
  enabled: true
  allow_origins: ["*"]
  allow_headers:
    - "Content-Type"
    - "Authorization"
    - "X-Requested-With"
    - "Accept"
    - "Origin"
    - "User-Agent"
  allow_header_prefixes:
    - "X-Custom-"
  allow_methods:
    - GET
    - OPTIONS
  allow_credentials: true
  max_age: 3600
  expose_headers:
    - "X-Total-Count"
servers:
  - name: iptvtest
    accounts:
      - url: http://test.localhost
        xtream_username: 3287432d
        xtream_password: 3894723d
        max_concurrent_requests: 3          # General max requests for this account
        max_concurrent_requests_per_host: 1 # Server specific max requests per hosts when requesting playback chunks
    epg_url: http://test.localhost
    epg_before: P7D
    epg_after: P7D
    user_agent: "iptv-proxy-test/1.0.0"
    proxy_stream: true
    proxy_send_user: true
    timeouts: *channel-timeouts
users:
  - username: 98442
    password: login1
    max_connections: 4
  - username: 24423
    password: login2
  - username: 34454
    password: login3
"""
