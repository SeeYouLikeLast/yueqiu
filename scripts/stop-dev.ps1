param(
    [int]$BackendPort = 8088,
    [int]$FrontendPort = 5173,
    [switch]$StopDocker
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Stop-PortProcess {
    param(
        [int]$Port,
        [string]$Name
    )
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        Write-Host "$Name port $Port is not listening." -ForegroundColor Yellow
        return
    }

    $processIds = $connections |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -and $_ -gt 0 }

    foreach ($processId in $processIds) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -eq $process) {
            continue
        }
        Write-Host "Stopping $Name on port $Port, PID $processId ($($process.ProcessName))"
        Stop-Process -Id $processId -Force
    }
}

function Convert-ToWslPath {
    param([string]$WindowsPath)
    $resolved = Resolve-Path -LiteralPath $WindowsPath
    $pathText = $resolved.Path
    if ($pathText -notmatch '^([A-Za-z]):\\(.*)$') {
        throw "Only local drive paths are supported: $pathText"
    }
    $drive = $Matches[1].ToLowerInvariant()
    $rest = $Matches[2].Replace('\', '/')
    return "/mnt/$drive/$rest"
}

function Stop-WslKeepAlive {
    $command = @'
pid_file=/tmp/hm-badminton-wsl-keepalive.pid
if [ -r "$pid_file" ]; then
  pid=$(cat "$pid_file")
  kill "$pid" 2>/dev/null || true
  rm -f "$pid_file"
fi
'@
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($command))
    $bashCommand = "echo $encodedCommand | base64 -d | bash"
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        & wsl -e bash -lc $bashCommand 2>$null
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..")
$DeployDir = Join-Path $ProjectRoot "deploy"

Write-Host "HM Badminton dev stop" -ForegroundColor Green
Write-Host "Project root: $ProjectRoot"

Write-Step "Stopping frontend and backend by port"
Stop-PortProcess -Port $FrontendPort -Name "frontend"
Stop-PortProcess -Port $BackendPort -Name "backend"

if ($StopDocker) {
    Write-Step "Stopping WSL Docker services"
    $deployWslPath = Convert-ToWslPath $DeployDir
    & wsl -e bash -lc "cd '$deployWslPath' && docker compose down"
    if ($LASTEXITCODE -ne 0) {
        throw "Docker services failed to stop. Check Docker inside WSL."
    }
    Stop-WslKeepAlive
}

Write-Step "Done"
