param(
    [switch]$SkipDocker,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$InstallFrontendDeps,
    [switch]$OpenWindows
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Escape-SingleQuote {
    param([string]$Value)
    return $Value.Replace("'", "''")
}

function Test-CommandExists {
    param([string]$Command)
    return [bool](Get-Command $Command -ErrorAction SilentlyContinue)
}

function Test-ListeningPort {
    param([int]$Port)
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Invoke-WslQuiet {
    param([string]$Command)

    # WSL may emit a localhost proxy/NAT warning on stderr. It is unrelated to
    # the invoked command, but PowerShell treats it as an error when ErrorAction is Stop.
    # Base64 avoids Windows argument parsing from stripping Bash quotes and substitutions.
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Command))
    $bashCommand = "echo $encodedCommand | base64 -d | bash"
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        return & wsl -e bash -lc $bashCommand 2>$null
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
}

function Start-WslKeepAlive {
    $command = @'
pid_file=/tmp/hm-badminton-wsl-keepalive.pid
if [ -r "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  cat "$pid_file"
else
  nohup sleep infinity >/tmp/hm-badminton-wsl-keepalive.log 2>&1 &
  echo $! > "$pid_file"
  cat "$pid_file"
fi
'@
    $keepAlivePidText = (Invoke-WslQuiet -Command $command) | Select-Object -First 1
    $keepAlivePid = if ($null -eq $keepAlivePidText) { "" } else { ([string]$keepAlivePidText).Trim() }
    if (-not $keepAlivePid) {
        throw "Unable to create the WSL keep-alive process."
    }
    Write-Host "WSL keep-alive PID: $keepAlivePid" -ForegroundColor DarkGray
}

function Wait-WslContainerReady {
    param(
        [string]$ContainerName,
        [int]$TimeoutSeconds = 60,
        [switch]$RequireHealth
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $format = "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}"
        $command = "docker inspect --format '$format' '$ContainerName' 2>/dev/null"
        $output = Invoke-WslQuiet -Command $command
        $state = (($output | Select-Object -First 1) -as [string]).Trim()

        if (($RequireHealth -and $state -eq "healthy") -or (-not $RequireHealth -and $state -eq "running")) {
            Write-Host "  $ContainerName is ready ($state)" -ForegroundColor DarkGray
            return
        }
        if ($state -in @("exited", "dead")) {
            throw "$ContainerName exited before becoming ready. Run: wsl -e bash -lc 'docker logs $ContainerName'"
        }
        Start-Sleep -Seconds 2
    }
    throw "$ContainerName did not become ready within $TimeoutSeconds seconds."
}

function Import-ConfiguredEnvironment {
    param([string[]]$Names)
    foreach ($name in $Names) {
        if ([Environment]::GetEnvironmentVariable($name, "Process")) {
            continue
        }
        $value = [Environment]::GetEnvironmentVariable($name, "User")
        if (-not $value) {
            $value = [Environment]::GetEnvironmentVariable($name, "Machine")
        }
        if ($value) {
            Set-Item -Path "env:$name" -Value $value
        }
    }
}

function Convert-ToWslPath {
    param([string]$WindowsPath)
    if (-not (Test-CommandExists "wsl")) {
        throw "wsl command not found. Please install and start WSL first."
    }
    $resolved = Resolve-Path -LiteralPath $WindowsPath
    $pathText = $resolved.Path
    if ($pathText -notmatch '^([A-Za-z]):\\(.*)$') {
        throw "Only local drive paths are supported: $pathText"
    }
    $drive = $Matches[1].ToLowerInvariant()
    $rest = $Matches[2].Replace('\', '/')
    return "/mnt/$drive/$rest"
}

function Start-PowerShellWindow {
    param(
        [string]$Title,
        [string]$WorkingDirectory,
        [string]$Command
    )
    $escapedDir = Escape-SingleQuote $WorkingDirectory
    $fullCommand = "`$Host.UI.RawUI.WindowTitle = '$Title'; Set-Location -LiteralPath '$escapedDir'; $Command"
    Start-Process powershell.exe -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $fullCommand
    )
}

function Start-BackgroundPowerShell {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Command,
        [string]$LogPath
    )
    $escapedDir = Escape-SingleQuote $WorkingDirectory
    $escapedLog = Escape-SingleQuote $LogPath
    $fullCommand = @"
`$Host.UI.RawUI.WindowTitle = '$Name'
Set-Location -LiteralPath '$escapedDir'
`$ErrorActionPreference = 'Continue'
$Command *> '$escapedLog'
"@
    Start-Process powershell.exe -WindowStyle Hidden -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-Command", $fullCommand
    )
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..")
$DeployDir = Join-Path $ProjectRoot "deploy"
$BackendDir = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend"
$LogsDir = Join-Path $ProjectRoot "logs"
$BackendLog = Join-Path $LogsDir "backend.log"
$FrontendLog = Join-Path $LogsDir "frontend.log"

New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null

Write-Host "HM Badminton dev startup" -ForegroundColor Green
Write-Host "Project root: $ProjectRoot"
Write-Host "Logs dir:     $LogsDir"

Import-ConfiguredEnvironment @(
    "AI_DASHSCOPE_ENABLED",
    "AI_DASHSCOPE_API_KEY",
    "AI_MODEL",
    "MAIL_USERNAME",
    "MAIL_PASSWORD",
    "AMAP_KEY"
)

if ($env:AI_DASHSCOPE_ENABLED -eq "true") {
    Write-Host "AI DashScope: enabled, key configured: $([bool]$env:AI_DASHSCOPE_API_KEY)"
} else {
    Write-Host "AI DashScope: disabled, local tool mode will be used."
}

if (-not $SkipDocker) {
    Write-Step "Starting WSL Docker services: MySQL / Redis / RocketMQ / MinIO"
    Start-WslKeepAlive
    $deployWslPath = Convert-ToWslPath $DeployDir
    $dockerCommand = "cd '$deployWslPath' && docker compose up -d"
    & wsl -e bash -lc $dockerCommand
    if ($LASTEXITCODE -ne 0) {
        throw "Docker services failed to start. Check Docker inside WSL."
    }
    Write-Host "Waiting for dependent services to become ready..." -ForegroundColor DarkGray
    Wait-WslContainerReady -ContainerName "hm-badminton-mysql" -RequireHealth -TimeoutSeconds 90
    Wait-WslContainerReady -ContainerName "hm-badminton-redis" -RequireHealth -TimeoutSeconds 60
    Wait-WslContainerReady -ContainerName "hm-badminton-rocketmq-namesrv" -TimeoutSeconds 45
    Wait-WslContainerReady -ContainerName "hm-badminton-rocketmq-broker" -TimeoutSeconds 60
}

if (-not $SkipBackend) {
    if (-not (Test-CommandExists "mvn")) {
        throw "mvn command not found. Please install Maven and configure PATH."
    }
    if (Test-ListeningPort 8088) {
        Write-Host "Port 8088 is already in use. Backend may already be running." -ForegroundColor Yellow
    } else {
        if ($OpenWindows) {
            Write-Step "Opening backend window: Spring Boot 8088"
            Start-PowerShellWindow `
                -Title "hm-badminton backend :8088" `
                -WorkingDirectory $BackendDir `
                -Command "mvn spring-boot:run"
        } else {
            Write-Step "Starting backend in background: Spring Boot 8088"
            Start-BackgroundPowerShell `
                -Name "hm-badminton backend :8088" `
                -WorkingDirectory $BackendDir `
                -Command "mvn spring-boot:run" `
                -LogPath $BackendLog
            Write-Host "Backend log: $BackendLog"
        }
    }
}

if (-not $SkipFrontend) {
    if (-not (Test-CommandExists "npm")) {
        throw "npm command not found. Please install Node.js and configure PATH."
    }
    if ($InstallFrontendDeps -or -not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
        Write-Step "Installing frontend dependencies"
        Push-Location $FrontendDir
        try {
            npm install
        } finally {
            Pop-Location
        }
    }
    if (Test-ListeningPort 5173) {
        Write-Host "Port 5173 is already in use. Frontend may already be running." -ForegroundColor Yellow
    } else {
        if ($OpenWindows) {
            Write-Step "Opening frontend window: Vite 5173"
            Start-PowerShellWindow `
                -Title "hm-badminton frontend :5173" `
                -WorkingDirectory $FrontendDir `
                -Command "npm run dev"
        } else {
            Write-Step "Starting frontend in background: Vite 5173"
            Start-BackgroundPowerShell `
                -Name "hm-badminton frontend :5173" `
                -WorkingDirectory $FrontendDir `
                -Command "npm run dev" `
                -LogPath $FrontendLog
            Write-Host "Frontend log: $FrontendLog"
        }
    }
}

Write-Step "Done"
Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend:  http://localhost:8088"
Write-Host "MinIO:    http://localhost:9001"
Write-Host ""
Write-Host "Options:"
Write-Host "  .\scripts\start-dev.ps1 -SkipDocker"
Write-Host "  .\scripts\start-dev.ps1 -SkipBackend"
Write-Host "  .\scripts\start-dev.ps1 -SkipFrontend"
Write-Host "  .\scripts\start-dev.ps1 -InstallFrontendDeps"
Write-Host "  .\scripts\start-dev.ps1 -OpenWindows"
