param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
if (-not $env:MHA_TOKEN) { throw '请先设置 MHA_TOKEN 为已登录普通用户的 JWT，不要将真实 token 写进脚本。' }
$headers = @{ Authorization = "Bearer $env:MHA_TOKEN"; Accept = 'application/json, text/event-stream' }
function Invoke-Mcp([object]$Message) {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/mcp" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes(($Message | ConvertTo-Json -Depth 12)))
}
$init = Invoke-Mcp @{ jsonrpc = '2.0'; id = 1; method = 'initialize'; params = @{ protocolVersion = '2025-03-26'; capabilities = @{}; clientInfo = @{ name = 'mental-smoke'; version = '1.0' } } }
if ($init.error -or $init.result.protocolVersion -ne '2025-03-26') { throw 'MCP初始化失败' }
$null = Invoke-Mcp @{ jsonrpc = '2.0'; method = 'notifications/initialized' }
$list = Invoke-Mcp @{ jsonrpc = '2.0'; id = 2; method = 'tools/list'; params = @{} }
if ($list.error -or @($list.result.tools).Count -ne 2) { throw 'MCP工具发现失败' }
foreach ($tool in @(@{ name = 'get_my_emotion_trend'; arguments = @{ days = 7 } }, @{ name = 'search_knowledge'; arguments = @{ query = '睡眠' } })) {
    $result = Invoke-Mcp @{ jsonrpc = '2.0'; id = 3; method = 'tools/call'; params = $tool }
    if ($result.error -or $result.result.isError) { throw "工具调用失败：$($tool.name)" }
    Write-Output "工具调用通过：$($tool.name)"
}
Write-Output 'MCP初始化、工具发现与调用检查通过。'
