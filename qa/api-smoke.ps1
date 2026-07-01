param(
  [string] $BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$stamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()

function Json($obj) { $obj | ConvertTo-Json -Depth 20 -Compress }
function Assert($condition, $message) { if (-not $condition) { throw $message } }
function Request($method, $path, $body = $null, $token = $null, $requestId = $null) {
  $headers = @{}
  if ($token) { $headers.Authorization = "Bearer $token" }
  if ($requestId) { $headers.'X-Request-Id' = $requestId }
  $params = @{ UseBasicParsing=$true; Method=$method; Uri="$base$path"; Headers=$headers }
  if ($null -ne $body) { $params.ContentType='application/json'; $params.Body=(Json $body) }
  $response = Invoke-WebRequest @params
  Assert $response.Headers['X-Request-Id'] "missing X-Request-Id for $method $path"
  Assert ($response.Headers['X-Content-Type-Options'] -eq 'nosniff') "missing nosniff for $method $path"
  Assert ($response.Headers['X-Frame-Options'] -eq 'DENY') "missing frame deny for $method $path"
  Assert ($response.Headers['Cache-Control'] -eq 'no-store') "missing no-store for $method $path"
  if ($requestId) { Assert ($response.Headers['X-Request-Id'] -eq $requestId) "request id not echoed for $method $path" }
  $parsed = $null
  if (-not [string]::IsNullOrWhiteSpace($response.Content)) { $parsed = $response.Content | ConvertFrom-Json }
  return [pscustomobject]@{ body=$parsed; headers=$response.Headers; status=[int]$response.StatusCode }
}
function ExpectStatus($method, $path, $expected, $body = $null, $token = $null) {
  try {
    $r = Request $method $path $body $token
    Assert ($r.status -eq $expected) "expected $expected for $method $path but got $($r.status)"
  } catch {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { throw }
    $actual = [int]$resp.StatusCode
    Assert ($actual -eq $expected) "expected $expected for $method $path but got $actual"
  }
}

$ownerName = "owner_$stamp"
$memberName = "member_$stamp"
$owner = Request POST '/api/auth/signup' @{ email="$ownerName@example.com"; username=$ownerName; displayName='Owner'; password='correct horse battery staple' } $null 'api-smoke-owner'
$member = Request POST '/api/auth/signup' @{ email="$memberName@example.com"; username=$memberName; displayName='Member'; password='correct horse battery staple' }
$ownerToken = $owner.body.accessToken
$memberToken = $member.body.accessToken
$ownerId = $owner.body.user.id
$memberId = $member.body.user.id
Assert $ownerToken 'owner token missing'
Assert $memberToken 'member token missing'

$me = Request GET '/api/users/@me' $null $ownerToken
Assert ($me.body.username -eq $ownerName) 'me endpoint username mismatch'

$guild = Request POST '/api/guilds' @{ name='Runtime Smoke Guild' } $ownerToken
$guildId = $guild.body.id
$text = Request POST "/api/guilds/$guildId/channels" @{ name='general'; type='GUILD_TEXT'; parentId=$null } $ownerToken
$voice = Request POST "/api/guilds/$guildId/channels" @{ name='war-room'; type='GUILD_VOICE'; parentId=$null } $ownerToken
$forum = Request POST "/api/guilds/$guildId/channels" @{ name='forum'; type='GUILD_FORUM'; parentId=$null } $ownerToken
$textId = $text.body.id; $voiceId = $voice.body.id; $forumId = $forum.body.id
Assert $textId 'text channel missing'
Request PUT "/api/guilds/$guildId/members/$memberId" $null $ownerToken | Out-Null
Request GET "/api/guilds/$guildId/channels/visible?memberId=$ownerId" $null $ownerToken | Out-Null

$msg = Request POST "/api/channels/$textId/messages" @{
  content='runtime smoke message <@00000000-0000-0000-0000-000000000099>'
  idempotencyKey="api-smoke-message-$stamp"
} $ownerToken
$msgId = $msg.body.id
Assert ($msg.body.content -like 'runtime smoke message*') 'message content mismatch'
Request GET "/api/channels/$textId/messages" $null $ownerToken | Out-Null
Request PUT "/api/channels/$textId/messages/$msgId/pin" $null $ownerToken | Out-Null
Request GET "/api/channels/$textId/messages/search?q=runtime&limit=5" $null $ownerToken | Out-Null

$invite = Request POST "/api/guilds/$guildId/invites" @{ channelId=$textId; maxAgeSeconds=86400; maxUses=3; temporary=$false; roleGrantIds=@() } $ownerToken
$code = $invite.body.code
Request GET "/api/invites/$code" | Out-Null

$gateway = Request POST '/api/gateway/identify' $null $ownerToken
$sessionId = $gateway.body.session.id
Assert $sessionId 'gateway session missing'
Request POST "/api/gateway/sessions/$sessionId/heartbeat" $null $ownerToken | Out-Null
Request GET "/api/gateway/sessions/$sessionId/events" $null $ownerToken | Out-Null

$emoji = Request POST "/api/guilds/$guildId/emojis" @{ name='runtime'; imageObjectKey='emoji/runtime.png' } $ownerToken
Request GET "/api/guilds/$guildId/emojis" $null $ownerToken | Out-Null
Request PUT "/api/channels/$textId/messages/$msgId/reactions/runtime" $null $ownerToken | Out-Null
Request GET "/api/channels/$textId/messages/$msgId/reactions" $null $ownerToken | Out-Null

$thread = Request POST "/api/channels/$textId/threads" @{ name='Runtime thread'; type='PUBLIC'; autoArchiveMinutes=60 } $ownerToken
$threadId = $thread.body.id
Request PUT "/api/threads/$threadId/archive" $null $ownerToken | Out-Null
Request PUT "/api/threads/$threadId/reopen" $null $ownerToken | Out-Null
$tag = Request POST "/api/channels/$forumId/forum-tags" @{ name='runtime' } $ownerToken
Request POST "/api/channels/$forumId/forum-posts" @{ title='Runtime forum post'; tagIds=@($tag.body.id); autoArchiveMinutes=60 } $ownerToken | Out-Null

Request POST "/api/guilds/$guildId/automod/rules" @{ type='KEYWORD'; name='Block secret'; keywords=@('secret') } $ownerToken | Out-Null
ExpectStatus POST "/api/channels/$textId/messages" 403 @{
  content='secret should block'
  idempotencyKey="api-smoke-automod-$stamp"
} $ownerToken
Request GET "/api/guilds/$guildId/audit-logs" $null $ownerToken | Out-Null

$voiceJoin = Request POST "/api/voice/channels/$voiceId/join" $null $ownerToken
Assert ($voiceJoin.body.token.provider -eq 'LIVEKIT_SKELETON') 'voice provider mismatch'
Request PATCH "/api/voice/channels/$voiceId/state" @{ muted=$true; deafened=$false; speaking=$true; screenSharing=$false } $ownerToken | Out-Null
Request DELETE "/api/voice/channels/$voiceId/leave" $null $ownerToken | Out-Null

$stage = Request POST "/api/stage/channels/$voiceId/sessions" @{ topic='Runtime stage' } $ownerToken
$stageId = $stage.body.id
Request POST "/api/stage/sessions/$stageId/request-to-speak" $null $memberToken | Out-Null
Request PUT "/api/stage/sessions/$stageId/speakers/$memberId" $null $ownerToken | Out-Null
Request PUT "/api/stage/sessions/$stageId/audience/$memberId" $null $ownerToken | Out-Null
$sound = Request POST "/api/soundboard/guilds/$guildId/sounds" @{ name='clap'; objectKey='sounds/clap.ogg' } $ownerToken
Request POST "/api/soundboard/channels/$voiceId/sounds/$($sound.body.id)/play" $null $ownerToken | Out-Null
Request GET '/api/premium/catalog' | Out-Null
Request GET '/api/premium/quests' | Out-Null
$locked = Request GET "/api/premium/users/$ownerId/features/hd_streaming" $null $ownerToken
Assert ($locked.body.enabled -eq $false) 'premium gate should start locked'
Request POST "/api/premium/users/$ownerId/entitlements" @{ guildId=$guildId; featureKey='hd_streaming' } $ownerToken | Out-Null
$gate = Request GET "/api/premium/users/$ownerId/features/hd_streaming" $null $ownerToken
Assert ($gate.body.enabled -eq $true) 'premium gate did not unlock'

Write-Output "API_SMOKE_PASS guild=$guildId text=$textId voice=$voiceId forum=$forumId"
