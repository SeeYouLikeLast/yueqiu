param(
  [string]$BaseUrl = "http://localhost:8088",
  [string]$Account = "13800000001",
  [string]$Password = "123456",
  [int]$ActivityId = 1
)

$loginBody = @{ account = $Account; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody
$token = $login.data.token

Write-Host "Token acquired for $Account"

$headers = @{ Authorization = "Bearer $token" }
$result = Invoke-RestMethod -Method Post -Uri "$BaseUrl/seckill/activities/$ActivityId/orders" -Headers $headers
$result | ConvertTo-Json -Depth 5
