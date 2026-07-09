param(
    [switch]$SkipAssets,
    [switch]$SkipUpload
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$AssetsRoot = Join-Path $ProjectRoot "seed-assets\generated"
$DataSqlPath = Join-Path $ProjectRoot "backend\src\main\resources\db\data.sql"
$Invariant = [System.Globalization.CultureInfo]::InvariantCulture

function Sql([object]$Value) {
    if ($null -eq $Value) { return "null" }
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [decimal] -or $Value -is [double]) {
        return [Convert]::ToString($Value, $Invariant)
    }
    return "'" + ([string]$Value).Replace("'", "''") + "'"
}

function Money([double]$Value) {
    return $Value.ToString("0.00", $Invariant)
}

function Add-Insert([System.Text.StringBuilder]$SqlBuilder, [string]$Table, [string[]]$Columns, [object[]]$Rows) {
    if (-not $Rows -or $Rows.Count -eq 0) { return }
    [void]$SqlBuilder.AppendLine("insert into $Table($($Columns -join ', ')) values")
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $suffix = if ($i -eq $Rows.Count - 1) { ";" } else { "," }
        [void]$SqlBuilder.AppendLine("(" + ($Rows[$i] -join ", ") + ")$suffix")
    }
    [void]$SqlBuilder.AppendLine()
}

function Set-Utf8NoBomContent([string]$Path, [string]$Value) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Value, $encoding)
}

function FileUrl([int]$Id) {
    return "/api/files/$Id/download"
}

function FileObjectName([int]$Id) {
    if ($Id -le 30) { return "demo/users/avatar/avatar-$($Id.ToString('000')).png" }
    if ($Id -le 55) { return "demo/places/cover/place-$($Id.ToString('000')).png" }
    if ($Id -le 80) { return "demo/equipment/cover/equipment-$($Id.ToString('000')).png" }
    if ($Id -le 110) { return "demo/blogs/images/blog-$($Id.ToString('000')).png" }
    return "demo/reviews/images/review-$($Id.ToString('000')).png"
}

function BizTypeForFile([int]$Id) {
    if ($Id -le 30) { return "avatar" }
    if ($Id -le 55) { return "place_cover" }
    if ($Id -le 80) { return "equipment_cover" }
    if ($Id -le 110) { return "blog" }
    return "venue_review"
}

function New-DemoImage([string]$Path, [int]$Width, [int]$Height, [string]$Title, [string]$Subtitle, [string]$ColorA, [string]$ColorB) {
    $dir = Split-Path $Path -Parent
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Add-Type -AssemblyName System.Drawing
    $bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $rect = New-Object System.Drawing.Rectangle(0, 0, $Width, $Height)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.ColorTranslator]::FromHtml($ColorA),
        [System.Drawing.ColorTranslator]::FromHtml($ColorB),
        35
    )
    $graphics.FillRectangle($brush, $rect)
    $overlay = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(46, 255, 255, 255))
    for ($i = 0; $i -lt 7; $i++) {
        $x = 32 + $i * [Math]::Max(28, [int]($Width / 9))
        $graphics.FillEllipse($overlay, $x, [int]($Height * 0.18), 58, 58)
    }
    $titleFont = New-Object System.Drawing.Font("Microsoft YaHei", [Math]::Max(22, [int]($Width / 18)), [System.Drawing.FontStyle]::Bold)
    $subFont = New-Object System.Drawing.Font("Microsoft YaHei", [Math]::Max(12, [int]($Width / 44)), [System.Drawing.FontStyle]::Regular)
    $dark = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(238, 17, 36, 30))
    $muted = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(185, 17, 36, 30))
    $graphics.DrawString($Title, $titleFont, $dark, 28, [int]($Height * 0.62))
    $graphics.DrawString($Subtitle, $subFont, $muted, 30, [int]($Height * 0.78))
    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}

function Build-DemoAssets {
    if ($SkipAssets) { return }
    $colors = @(
        @("#d9f99d", "#34d399"), @("#bfdbfe", "#38bdf8"), @("#fde68a", "#fb923c"),
        @("#fecdd3", "#f472b6"), @("#ddd6fe", "#8b5cf6"), @("#bbf7d0", "#14b8a6")
    )
    for ($id = 1; $id -le 120; $id++) {
        $objectName = FileObjectName $id
        $path = Join-Path $AssetsRoot $objectName
        $pair = $colors[($id - 1) % $colors.Count]
        if ($id -le 30) {
            New-DemoImage $path 320 320 "球友 $($id.ToString('00'))" "约个球" $pair[0] $pair[1]
        } elseif ($id -le 55) {
            New-DemoImage $path 900 600 "运动场馆" "本地 MinIO 封面 $($id - 30)" $pair[0] $pair[1]
        } elseif ($id -le 80) {
            New-DemoImage $path 720 720 "装备好物" "本地 MinIO 商品图 $($id - 55)" $pair[0] $pair[1]
        } elseif ($id -le 110) {
            New-DemoImage $path 900 900 "球友动态" "训练、装备、场馆体验" $pair[0] $pair[1]
        } else {
            New-DemoImage $path 900 600 "真实评价" "场地、服务、氛围" $pair[0] $pair[1]
        }
    }
}

function Upload-DemoAssets {
    if ($SkipUpload) { return }
    $mcImage = if ($env:MINIO_CLIENT_IMAGE) { $env:MINIO_CLIENT_IMAGE } else { "quay.io/minio/mc:latest" }
    $uploadCommand = "mc alias set local http://127.0.0.1:9000 minioadmin minioadmin123 >/dev/null && mc mb --ignore-existing local/hm-badminton >/dev/null && mc anonymous set download local/hm-badminton >/dev/null && mc cp --recursive /assets/demo local/hm-badminton/"

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        $minio = docker ps --format "{{.Names}}" | Select-String -SimpleMatch "hm-badminton-minio"
        if ($minio) {
            docker run --rm --network container:hm-badminton-minio --entrypoint /bin/sh -v "${AssetsRoot}:/assets:ro" $mcImage -c $uploadCommand
            return
        }
    }

    $wsl = Get-Command wsl -ErrorAction SilentlyContinue
    if ($wsl) {
        $resolved = (Resolve-Path $AssetsRoot).Path
        if ($resolved -match "^([A-Za-z]):\\(.*)$") {
            $drive = $Matches[1].ToLowerInvariant()
            $rest = $Matches[2].Replace("\", "/")
            $wslAssetsRoot = "/mnt/$drive/$rest"
            $previousErrorAction = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            $names = & wsl sh -lc "docker ps --format '{{.Names}}'" 2>$null
            $dockerPsExitCode = $LASTEXITCODE
            $ErrorActionPreference = $previousErrorAction
            if ($dockerPsExitCode -eq 0 -and ($names | Select-String -SimpleMatch "hm-badminton-minio")) {
                $cmd = "docker run --rm --network container:hm-badminton-minio --entrypoint /bin/sh -v '${wslAssetsRoot}:/assets:ro' $mcImage -c '$uploadCommand'"
                $previousErrorAction = $ErrorActionPreference
                $ErrorActionPreference = "Continue"
                & wsl sh -lc $cmd
                $uploadExitCode = $LASTEXITCODE
                $ErrorActionPreference = $previousErrorAction
                if ($uploadExitCode -ne 0) {
                    throw "MinIO 上传失败，wsl docker run 退出码：$uploadExitCode"
                }
                return
            }
        }
    }

    Write-Warning "未找到可用 Docker，或 hm-badminton-minio 未运行，已跳过 MinIO 上传。"
}

$cities = @(
    @{ Name = "西安"; Count = 60; Area = @("雁塔区", "长安区", "未央区", "碑林区"); Lng = 108.9402; Lat = 34.3416 },
    @{ Name = "上海"; Count = 50; Area = @("浦东新区", "徐汇区", "静安区", "闵行区"); Lng = 121.4737; Lat = 31.2304 },
    @{ Name = "北京"; Count = 50; Area = @("朝阳区", "海淀区", "丰台区", "西城区"); Lng = 116.4074; Lat = 39.9042 },
    @{ Name = "成都"; Count = 40; Area = @("武侯区", "锦江区", "高新区", "成华区"); Lng = 104.0665; Lat = 30.5723 }
)

$sports = @(
    @{ Code = "badminton"; Name = "羽毛球"; Count = 75 },
    @{ Code = "table_tennis"; Name = "乒乓球"; Count = 40 },
    @{ Code = "basketball"; Name = "篮球"; Count = 35 },
    @{ Code = "football"; Name = "足球"; Count = 25 },
    @{ Code = "tennis"; Name = "网球"; Count = 15 },
    @{ Code = "volleyball"; Name = "排球"; Count = 10 }
)

function CityFor([int]$Index) {
    $cursor = 0
    foreach ($city in $cities) {
        $cursor += $city.Count
        if ($Index -le $cursor) { return $city }
    }
    return $cities[-1]
}

function SportFor([int]$Index) {
    $cursor = 0
    foreach ($sport in $sports) {
        $cursor += $sport.Count
        if ($Index -le $cursor) { return $sport }
    }
    return $sports[-1]
}

function SportByCode([string]$Code) {
    return ($sports | Where-Object { $_.Code -eq $Code })[0]
}

function EquipmentDescription([string]$SportCode, [int]$CategoryId) {
    switch ($SportCode) {
        "badminton" {
            switch ($CategoryId) {
                1 { return "中杆回弹清晰，攻防转换顺手，适合中前场连贯和后场突击。" }
                2 { return "包裹稳定，侧向支撑扎实，急停启动时脚感更稳。" }
                3 { return "飞行稳定，落点清晰，适合日常训练和俱乐部对抗。" }
                default { return "容量适中，可放球拍、球鞋和换洗衣物，通勤约球都方便。" }
            }
        }
        "table_tennis" {
            switch ($CategoryId) {
                5 { return "底板手感通透，借力和发力都容易控制，适合弧圈结合快攻。" }
                6 { return "胶面摩擦稳定，拉球吃球感明显，台内控制更细腻。" }
                default { return "弹跳均匀，旋转反馈清楚，适合多球训练和实战练习。" }
            }
        }
        "football" {
            switch ($CategoryId) {
                8 { return "鞋面贴合，抓地稳定，适合人草场地的启动和变向。" }
                9 { return "球面耐磨，脚感扎实，适合训练传接球和小场比赛。" }
                default { return "轻量防护，贴合小腿，降低对抗中的碰撞不适。" }
            }
        }
        "basketball" {
            switch ($CategoryId) {
                11 { return "缓震回弹均衡，外底抓地稳定，适合突破和急停跳投。" }
                12 { return "球面纹理清晰，控球手感稳定，室内外训练都顺手。" }
                default { return "提供膝踝基础支撑，适合日常训练和轻度对抗。" }
            }
        }
        "tennis" { return "拍面甜区友好，挥拍稳定，适合底线拉打和上网截击练习。" }
        "volleyball" { return "触球柔和，弹性稳定，适合传垫扣综合训练和团队热身。" }
        default { return "设计稳定耐用，适合日常训练、社群活动和进阶练习。" }
    }
}

function VenueProductDescription([string]$SportName, [string]$ProductType) {
    switch ($ProductType) {
        "TIME_PACKAGE" { return "上午低峰畅打套餐，适合个人练习、双人拉球和下班前补练。" }
        "COURT_SLOT" { return "黄金时段单场预订，适合约搭子开局、朋友小队训练和临时组局。" }
        "COACH_LESSON" { return "$SportName 私教体验课，包含动作评估、基础纠错和针对性训练建议。" }
        default { return "$SportName 场馆项目，可在线预订并到店核销使用。" }
    }
}

$levels = @("新手", "初级", "中级", "高级")
$styles = @("双打/防守反击", "进攻型/后场突击", "控球型/稳定多拍", "娱乐局/氛围组", "小班训练/技术提升")
$times = @("工作日 19:00 后", "周二/周四晚上", "周末上午", "周末下午", "每天晚上")
$rng = New-Object System.Random(20260707)
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("set names utf8mb4;")
[void]$sb.AppendLine()

Build-DemoAssets

$fileRows = @()
for ($id = 1; $id -le 120; $id++) {
    $objectName = FileObjectName $id
    $assetPath = Join-Path $AssetsRoot $objectName
    $fileSize = if (Test-Path -LiteralPath $assetPath) { (Get-Item -LiteralPath $assetPath).Length } else { 65536 }
    $fileRows += ,@(
        $id,
        "null",
        (Sql (BizTypeForFile $id)),
        "null",
        (Sql "hm-badminton"),
        (Sql $objectName),
        (Sql (Split-Path $objectName -Leaf)),
        (Sql "image/png"),
        $fileSize,
        (Sql "demo-$id"),
        (Sql (FileUrl $id)),
        (Sql "可用")
    )
}
Add-Insert $sb "file_metadata" @("id", "owner_user_id", "biz_type", "biz_id", "bucket_name", "object_name", "original_filename", "content_type", "file_size", "etag", "public_url", "status") $fileRows

$userRows = @()
$profileRows = @()
for ($i = 1; $i -le 200; $i++) {
    $city = CityFor $i
    $sport = SportFor $i
    $area = $city.Area[($i - 1) % $city.Area.Count]
    $level = $levels[($i + 1) % $levels.Count]
    $lng = $city.Lng + (($rng.NextDouble() - 0.5) * 0.09)
    $lat = $city.Lat + (($rng.NextDouble() - 0.5) * 0.07)
    $avatarId = (($i - 1) % 30) + 1
    $nickname = if ($i -le 10) { "$($city.Name)$($sport.Name)达人$($i.ToString('00'))" } elseif ($i -le 50) { "$($city.Name)$($sport.Name)教练$($i.ToString('00'))" } else { "$($city.Name)$($sport.Name)球友$($i.ToString('000'))" }
    $isBigV = if ($i -le 10) { 1 } else { 0 }
    $userRows += ,@(
        $i,
        (Sql ("139{0:D8}" -f $i)),
        (Sql ("demo{0:D3}@example.com" -f $i)),
        (Sql ("demo_user_{0:D3}" -f $i)),
        (Sql "{plain}123456"),
        (Sql $nickname),
        (Sql (FileUrl $avatarId)),
        (Sql $city.Name),
        (Sql $level),
        (Sql $times[$i % $times.Count]),
        $isBigV,
        1,
        (Sql ("2026-07-{0:D2} 10:{1:D2}:00" -f ((($i - 1) % 7) + 1), ($i % 60)))
    )
    $profileRows += ,@(
        $i,
        (Sql $sport.Code),
        (Sql $city.Name),
        (Sql $area),
        ($lng.ToString("0.000000", $Invariant)),
        ($lat.ToString("0.000000", $Invariant)),
        (Sql $level),
        (Sql $styles[$i % $styles.Count]),
        (Sql $times[$i % $times.Count]),
        (Sql "喜欢$($sport.Name)，希望找到稳定搭子，准时、好沟通。"),
        1
    )
}
Add-Insert $sb "users" @("id", "phone", "email", "username", "password_hash", "nickname", "avatar", "city", "level", "prefer_time", "is_big_v", "status", "created_at") $userRows
Add-Insert $sb "player_profiles" @("user_id", "sport_code", "city", "area", "longitude", "latitude", "level", "play_style", "available_time", "intro", "allow_invite") $profileRows

$followRows = @()
$seenFollows = @{}
for ($i = 1; $i -le 200; $i++) {
    $targets = @((1 + (($i * 7) % 10)), (1 + (($i + 17) % 200)), (1 + (($i + 43) % 200)))
    foreach ($target in $targets) {
        if ($target -eq $i) { $target = (($target + 11) % 200) + 1 }
        $key = "$i-$target"
        if (-not $seenFollows.ContainsKey($key)) {
            $seenFollows[$key] = $true
            $followRows += ,@($i, $target)
        }
    }
}
Add-Insert $sb "follows" @("user_id", "follow_user_id") $followRows

$placeRows = @()
for ($i = 1; $i -le 40; $i++) {
    $city = $cities[($i - 1) % $cities.Count]
    $sport = $sports[($i - 1) % $sports.Count]
    $area = $city.Area[($i - 1) % $city.Area.Count]
    $placeRows += ,@(
        $i,
        (Sql $sport.Code),
        (Sql "$($city.Name)$($sport.Name)高德缓存槽位$($i.ToString('00'))"),
        (Sql $city.Name),
        (Sql $area),
        (Sql "$area 演示路 $i 号"),
        (($city.Lng + (($i % 5) * 0.006)).ToString("0.000000", $Invariant)),
        (($city.Lat + (($i % 4) * 0.005)).ToString("0.000000", $Invariant)),
        (40 + ($i % 12) * 8),
        (Money (4.2 + (($i % 7) * 0.1))),
        (20 + $i * 3),
        (Sql "09:00-22:00"),
        (Sql (FileUrl (31 + (($i - 1) % 25)))),
        (Sql "$($sport.Name),停车,更衣室,饮水机"),
        1
    )
}
Add-Insert $sb "place" @("id", "sport_code", "name", "city", "area", "address", "longitude", "latitude", "avg_price", "score", "review_count", "open_hours", "cover_url", "facilities", "status") $placeRows

$operatorRows = @()
for ($i = 1; $i -le 20; $i++) {
    $city = $cities[($i - 1) % $cities.Count]
    $sport = $sports[($i - 1) % $sports.Count]
    $rank = (($i - 1) % 8) + 1
    $operatorRows += ,@(
        $i,
        (50 + $i),
        (Sql $city.Name),
        (Sql $sport.Code),
        $rank,
        (Sql "$($city.Name)$($sport.Name)场馆号$rank"),
        (Sql (FileUrl ((($i - 1) % 30) + 1))),
        (Sql "发布场馆团购、活动公告和$($sport.Name)约球信息。"),
        1
    )
}
Add-Insert $sb "venue_operators" @("id", "user_id", "city", "sport_code", "place_rank", "operator_name", "avatar", "intro", "status") $operatorRows

$coachRows = @()
for ($i = 1; $i -le 40; $i++) {
    $city = $cities[($i - 1) % $cities.Count]
    $sport = $sports[($i - 1) % $sports.Count]
    $rank = (($i - 1) % 8) + 1
    $coachRows += ,@(
        $i,
        $rank,
        "null",
        (Sql "$rank"),
        (Sql $sport.Code),
        (Sql "$($sport.Name)教练$($i.ToString('00'))"),
        (Sql (FileUrl ((($i + 5) % 30) + 1))),
        (Sql $levels[($i + 2) % $levels.Count]),
        (Sql "基础,进阶,陪练"),
        (Sql "擅长$($sport.Name)基础纠正和实战陪练，适合下班后训练。"),
        (Money (99 + (($i % 8) * 20))),
        1
    )
}
Add-Insert $sb "coaches" @("id", "venue_id", "amap_place_id", "venue_name", "sport_code", "name", "avatar", "level", "tags", "intro", "price_per_hour", "status") $coachRows

$categoryRows = @(
    @(1, (Sql "badminton"), (Sql "羽毛球拍"), (Sql "racquet"), 1),
    @(2, (Sql "badminton"), (Sql "羽毛球鞋"), (Sql "shoe"), 2),
    @(3, (Sql "badminton"), (Sql "羽毛球"), (Sql "shuttle"), 3),
    @(4, (Sql "badminton"), (Sql "球包/手胶"), (Sql "bag"), 4),
    @(5, (Sql "table_tennis"), (Sql "乒乓球拍"), (Sql "racquet"), 1),
    @(6, (Sql "table_tennis"), (Sql "乒乓球"), (Sql "ball"), 2),
    @(7, (Sql "table_tennis"), (Sql "胶皮/底板"), (Sql "grip"), 3),
    @(8, (Sql "football"), (Sql "足球鞋"), (Sql "shoe"), 1),
    @(9, (Sql "football"), (Sql "足球"), (Sql "ball"), 2),
    @(10, (Sql "football"), (Sql "护腿板"), (Sql "shield"), 3),
    @(11, (Sql "basketball"), (Sql "篮球鞋"), (Sql "shoe"), 1),
    @(12, (Sql "basketball"), (Sql "篮球"), (Sql "ball"), 2),
    @(13, (Sql "basketball"), (Sql "护具"), (Sql "shield"), 3),
    @(14, (Sql "tennis"), (Sql "网球拍"), (Sql "racquet"), 1),
    @(15, (Sql "volleyball"), (Sql "排球"), (Sql "ball"), 1)
)
Add-Insert $sb "equipment_categories" @("id", "sport_code", "name", "icon", "sort") $categoryRows

$categoryBySport = @{
    badminton = @(1, 2, 3, 4); table_tennis = @(5, 6, 7); basketball = @(11, 12, 13);
    football = @(8, 9, 10); tennis = @(14); volleyball = @(15)
}
$equipmentIdsBySport = @{}
$venueIdsBySport = @{}
foreach ($sport in $sports) {
    $equipmentIdsBySport[$sport.Code] = @()
    $venueIdsBySport[$sport.Code] = @()
}
$equipmentMetaById = @{}
$venueMetaById = @{}

$equipmentRows = @()
for ($i = 1; $i -le 50; $i++) {
    $sport = SportFor ([Math]::Min(200, ($i * 4)))
    $category = $categoryBySport[$sport.Code][($i - 1) % $categoryBySport[$sport.Code].Count]
    $equipmentName = "$($sport.Name)精选装备 $($i.ToString('00'))"
    $equipmentCover = FileUrl (56 + (($i - 1) % 25))
    $equipmentPrice = 49 + (($i * 17) % 520)
    $equipmentDescription = EquipmentDescription $sport.Code $category
    $equipmentIdsBySport[$sport.Code] = @($equipmentIdsBySport[$sport.Code] + $i)
    $equipmentMetaById[$i] = @{
        Title = $equipmentName
        Cover = $equipmentCover
        Price = $equipmentPrice
    }
    $equipmentRows += ,@(
        $i,
        (Sql $sport.Code),
        $category,
        (Sql $equipmentName),
        (Sql (@("YUDONG", "SHUTTLELAB", "COURTGO", "SAFEPLAY", "BASELINE")[$i % 5])),
        (Sql $equipmentDescription),
        (Sql $equipmentCover),
        (Money $equipmentPrice),
        (80 + (($i * 13) % 420)),
        (Money (4.2 + (($i % 7) * 0.1))),
        (20 + (($i * 19) % 600)),
        1
    )
}
Add-Insert $sb "equipment" @("id", "sport_code", "category_id", "name", "brand", "description", "cover_url", "price", "stock", "score", "sold", "status") $equipmentRows

$venueRows = @()
$inventoryRows = @()
$productTypes = @("TIME_PACKAGE", "COURT_SLOT", "COACH_LESSON")
for ($i = 1; $i -le 100; $i++) {
    $sport = SportFor ([Math]::Min(200, $i * 2))
    $rank = (($i - 1) % 8) + 1
    $type = $productTypes[($i - 1) % $productTypes.Count]
    $baseTitle = if ($type -eq "TIME_PACKAGE") { "08:00-12:00 单人畅打" } elseif ($type -eq "COURT_SLOT") { "黄金单场 1 小时" } else { "私教体验 60 分钟" }
    $title = "$($sport.Name) $baseTitle"
    $venueDescription = VenueProductDescription $sport.Name $type
    $price = if ($type -eq "TIME_PACKAGE") { 29 + ($i % 5) * 5 } elseif ($type -eq "COURT_SLOT") { 68 + ($i % 6) * 10 } else { 99 + ($i % 8) * 20 }
    $original = if ($i % 4 -eq 0) { $null } elseif ($i % 5 -eq 0) { $price } else { $price + 30 + ($i % 5) * 12 }
    $originalSql = if ($null -eq $original) { "null" } else { Money $original }
    $venueCover = FileUrl (31 + (($i - 1) % 25))
    $venueIdsBySport[$sport.Code] = @($venueIdsBySport[$sport.Code] + $i)
    $venueMetaById[$i] = @{
        Title = $title
        Cover = $venueCover
        Price = $price
    }
    $venueRows += ,@(
        $i,
        "null",
        "null",
        (Sql "$rank"),
        $rank,
        (Sql $sport.Code),
        (Sql $type),
        (Sql $title),
        (Sql $venueDescription),
        (Sql $venueCover),
        (Money $price),
        $originalSql,
        (Sql "$($sport.Name),可核销,本地演示"),
        (Sql "购买后按所选日期和时间入场，入场需出示核销码。"),
        (Sql "开场前 2 小时可退，过期不可退。"),
        (Sql "2026-07-01 00:00:00"),
        (Sql "2026-12-31 23:59:59"),
        1
    )
    for ($d = 1; $d -le 12; $d++) {
        $inventoryId = (($i - 1) * 12) + $d
        $stock = if ($type -eq "TIME_PACKAGE") { 20 + ($i % 12) } elseif ($type -eq "COURT_SLOT") { 1 } else { 3 + ($i % 4) }
        $sold = if ($d % 5 -eq 0) { [Math]::Min($stock, 1 + ($i % 3)) } else { 0 }
        $available = [Math]::Max(0, $stock - $sold)
        $courtName = if ($type -eq "COACH_LESSON") { "私教训练场" } else { "标准场地" }
        $coachId = if ($type -eq "COACH_LESSON") { (($i - 1) % 40) + 1 } else { "null" }
        $startTime = if ($type -eq "TIME_PACKAGE") { "08:00:00" } elseif ($type -eq "COURT_SLOT") { "19:00:00" } else { "18:00:00" }
        $endTime = if ($type -eq "TIME_PACKAGE") { "12:00:00" } elseif ($type -eq "COURT_SLOT") { "20:00:00" } else { "19:00:00" }
        $inventoryRows += ,@(
            $inventoryId,
            $i,
            "null",
            (Sql $courtName),
            $coachId,
            (Sql ("2026-07-{0:D2}" -f (7 + $d))),
            (Sql $startTime),
            (Sql $endTime),
            $stock,
            $available,
            0,
            $sold,
            (Money $price),
            (Sql "可售")
        )
    }
}
Add-Insert $sb "venue" @("id", "venue_id", "amap_place_id", "venue_name", "place_rank", "sport_code", "product_type", "title", "description", "cover_url", "price", "original_price", "tags", "use_rule", "refund_rule", "sale_start_at", "sale_end_at", "status") $venueRows
Add-Insert $sb "venue_inventory" @("id", "product_id", "venue_id", "court_name", "coach_id", "service_date", "start_time", "end_time", "total_stock", "available_stock", "locked_stock", "sold_stock", "price", "status") $inventoryRows

$reviewRows = @()
for ($i = 1; $i -le 100; $i++) {
    $reviewVenueId = (($i - 1) % 40) + 1
    $reviewUserId = (($i * 7) % 200) + 1
    $reviewRating = 4 + ($i % 2)
    $reviewRows += ,@(
        $i,
        $reviewVenueId,
        $reviewUserId,
        $reviewRating,
        (Sql "场地维护不错，灯光和动线比较舒服，适合下班后约一场。"),
        (Sql (FileUrl (111 + (($i - 1) % 10)))),
        ($i % 37),
        (Sql ("2026-07-{0:D2} 18:00:00" -f ((($i - 1) % 7) + 1)))
    )
}
Add-Insert $sb "venue_reviews" @("id", "venue_id", "user_id", "rating", "content", "image_urls", "likes", "created_at") $reviewRows

$blogRows = @()
for ($i = 1; $i -le 200; $i++) {
    $sport = SportFor $i
    $author = (($i * 13) % 200) + 1
    $isEquipment = $i % 2 -eq 1
    if ($isEquipment) {
        $relatedPool = $equipmentIdsBySport[$sport.Code]
        $relatedId = $relatedPool[($i - 1) % $relatedPool.Count]
        $relatedType = "EQUIPMENT"
        $relatedMeta = $equipmentMetaById[$relatedId]
    } else {
        $relatedPool = $venueIdsBySport[$sport.Code]
        $relatedId = $relatedPool[($i - 1) % $relatedPool.Count]
        $relatedType = "VENUE_PRODUCT"
        $relatedMeta = $venueMetaById[$relatedId]
    }
    $liked = if ($i -le 20) { 80 + $i * 3 } elseif ($i -le 80) { 15 + ($i % 45) } else { $i % 12 }
    $blogRows += ,@(
        $i,
        $author,
        (Sql $sport.Code),
        (Sql "$($sport.Name)体验分享 $($i.ToString('000'))"),
        (Sql "这次体验重点是手感、场地和搭子配合。整体适合周末约球，也适合新手逐步进阶。"),
        (Sql (FileUrl (81 + (($i - 1) % 30)))),
        (Sql $relatedType),
        $relatedId,
        (Sql $relatedMeta.Title),
        (Sql $relatedMeta.Cover),
        (Money $relatedMeta.Price),
        $liked,
        1,
        (Sql ("2026-07-{0:D2} {1:D2}:20:00" -f ((($i - 1) % 7) + 1), (8 + ($i % 12))))
    )
}
Add-Insert $sb "blogs" @("id", "user_id", "sport_code", "title", "content", "image_urls", "related_type", "related_id", "related_title", "related_cover_url", "related_price", "liked", "status", "created_at") $blogRows

$venueOrderRows = @()
for ($i = 1; $i -le 120; $i++) {
    $productId = (($i - 1) % 100) + 1
    $inventoryId = (($productId - 1) * 12) + (($i - 1) % 12) + 1
    $rank = (($productId - 1) % 8) + 1
    $venueOrderStatus = if ($i % 7 -eq 0) { "待支付" } elseif ($i % 11 -eq 0) { "已完成" } else { "已支付" }
    $venueOrderUserId = (($i * 5) % 200) + 1
    $venueOrderRows += ,@(
        $i,
        $venueOrderUserId,
        $productId,
        $inventoryId,
        "null",
        (Sql "DEMO_AMAP_$rank"),
        (Sql "高德真实场所快照 $rank"),
        (Sql "场馆服务订单 $productId"),
        (Sql $productTypes[($productId - 1) % $productTypes.Count]),
        (Sql ("2026-07-{0:D2}" -f (8 + ($i % 12)))),
        (Sql "19:00:00"),
        (Sql "20:00:00"),
        (Money (39 + ($productId % 12) * 8)),
        (Sql $venueOrderStatus),
        (Sql ("{0:D6}" -f (400000 + $i))),
        (Sql ("2026-07-{0:D2} 12:00:00" -f ((($i - 1) % 7) + 1)))
    )
}
Add-Insert $sb "order_venue" @("id", "user_id", "product_id", "inventory_id", "venue_id", "amap_place_id", "venue_name", "product_title", "product_type", "service_date", "start_time", "end_time", "amount", "status", "verify_code", "paid_at") $venueOrderRows

$equipmentOrderRows = @()
$equipmentOrderItemRows = @()
$itemId = 1
for ($i = 1; $i -le 80; $i++) {
    $equipmentId = (($i - 1) % 50) + 1
    $quantity = ($i % 3) + 1
    $amount = (49 + (($equipmentId * 17) % 520)) * $quantity
    $equipmentOrderStatus = if ($i % 9 -eq 0) { "待支付" } elseif ($i % 13 -eq 0) { "已取消" } else { "已支付" }
    $equipmentOrderUserId = (($i * 9) % 200) + 1
    $equipmentOrderRows += ,@(
        $i,
        $equipmentOrderUserId,
        (Money $amount),
        (Sql $equipmentOrderStatus),
        (Sql "演示收货地址 $i 号"),
        (Sql ("2026-07-{0:D2} 14:00:00" -f ((($i - 1) % 7) + 1)))
    )
    $equipmentOrderItemRows += ,@(
        $itemId++,
        $i,
        $equipmentId,
        (Sql "精选装备 $equipmentId"),
        (Sql (FileUrl (56 + (($equipmentId - 1) % 25)))),
        (Money (49 + (($equipmentId * 17) % 520))),
        $quantity
    )
}
Add-Insert $sb "order_equipment" @("id", "user_id", "total_amount", "status", "address", "paid_at") $equipmentOrderRows
Add-Insert $sb "order_equipment_item" @("id", "order_id", "product_id", "product_name", "cover_url", "price", "quantity") $equipmentOrderItemRows

$seckillEquipmentRows = @()
for ($i = 1; $i -le 7; $i++) {
    $equipmentId = (($i - 1) % 50) + 1
    $seckillEquipmentRows += ,@($i, $equipmentId, (Money (29 + $i * 30)), (120 + $i * 10), (Sql "2026-07-01 00:00:00"), (Sql "2026-12-31 23:59:59"), 1)
}
Add-Insert $sb "seckill_equipment" @("id", "equipment_id", "seckill_price", "stock", "start_at", "end_at", "status") $seckillEquipmentRows

$seckillVenueRows = @()
for ($i = 1; $i -le 3; $i++) {
    $seckillVenueRows += ,@($i, $i, (Money (19 + $i * 20)), (80 + $i * 10), (Sql "2026-07-01 00:00:00"), (Sql "2026-12-31 23:59:59"), 1)
}
Add-Insert $sb "seckill_venue" @("id", "venue_id", "seckill_price", "stock", "start_at", "end_at", "status") $seckillVenueRows

$seckillEquipmentOrderRows = @()
for ($i = 1; $i -le 70; $i++) {
    $seckillId = (($i - 1) % 7) + 1
    $equipmentId = $seckillId
    $seckillEquipmentOrderRows += ,@((1000 + $i), $seckillId, $equipmentId, ((($i * 3) % 200) + 1), (Money (29 + $seckillId * 30)), (Sql "已抢到"))
}
Add-Insert $sb "order_seckill_equipment" @("id", "seckill_id", "equipment_id", "user_id", "amount", "status") $seckillEquipmentOrderRows

$seckillVenueOrderRows = @()
for ($i = 1; $i -le 30; $i++) {
    $seckillId = (($i - 1) % 3) + 1
    $seckillVenueOrderRows += ,@((2000 + $i), $seckillId, $seckillId, ((($i * 11) % 200) + 1), (Money (19 + $seckillId * 20)), (Sql "已抢到"))
}
Add-Insert $sb "order_seckill_venue" @("id", "seckill_id", "venue_id", "user_id", "amount", "status") $seckillVenueOrderRows

$activityRows = @()
$memberRows = @()
$memberId = 1
for ($i = 1; $i -le 80; $i++) {
    $city = $cities[($i - 1) % $cities.Count]
    $sport = $sports[($i - 1) % $sports.Count]
    $creator = (($i * 5) % 200) + 1
    $maxPlayers = 4 + ($i % 6)
    $currentPlayers = 2 + ($i % [Math]::Max(2, $maxPlayers - 1))
    if ($currentPlayers -gt $maxPlayers) { $currentPlayers = $maxPlayers }
    $rank = (($i - 1) % 8) + 1
    $activityStatus = if ($currentPlayers -ge $maxPlayers) { "已满员" } elseif ($i % 8 -eq 0) { "已结束" } else { "招募中" }
    $activityRows += ,@(
        $i,
        (Sql $sport.Code),
        $creator,
        $rank,
        (Sql "amap"),
        (Sql "DEMO_AMAP_$rank"),
        (Sql "$($city.Name)$($sport.Name)约球场地$rank"),
        (Sql "$($sport.Name)下班后一场 $($i.ToString('00'))"),
        (Sql $city.Name),
        (Sql ("2026-07-{0:D2} 19:00:00" -f (8 + ($i % 14)))),
        (Sql ("2026-07-{0:D2} 21:00:00" -f (8 + ($i % 14)))),
        $maxPlayers,
        $currentPlayers,
        (Sql $levels[$i % $levels.Count]),
        (Sql "AA"),
        (Sql $activityStatus)
    )
    for ($m = 1; $m -le $currentPlayers; $m++) {
        $userId = if ($m -eq 1) { $creator } else { (($creator + $m * 13) % 200) + 1 }
        $role = if ($m -eq 1) { "OWNER" } else { "MEMBER" }
        $memberRows += ,@($memberId++, $i, $userId, (Sql $role), (Sql "已加入"))
    }
}
Add-Insert $sb "sport_activities" @("id", "sport_code", "creator_id", "venue_id", "place_source", "place_id", "venue_name", "title", "city", "start_time", "end_time", "max_players", "current_players", "level_required", "fee_type", "status") $activityRows
Add-Insert $sb "sport_activity_members" @("id", "activity_id", "user_id", "role", "status") $memberRows

$cartEquipmentRows = @()
for ($i = 1; $i -le 12; $i++) {
    $cartEquipmentProductId = (($i * 2) % 50) + 1
    $cartEquipmentQuantity = ($i % 3) + 1
    $cartEquipmentRows += ,@($i, $i, $cartEquipmentProductId, $cartEquipmentQuantity)
}
Add-Insert $sb "cart_equipment" @("id", "user_id", "product_id", "quantity") $cartEquipmentRows

$cartVenueRows = @()
for ($i = 1; $i -le 8; $i++) {
    $productId = (($i - 1) % 100) + 1
    $cartVenueInventoryId = (($productId - 1) * 12) + 1
    $cartVenueRows += ,@($i, $i, $productId, $cartVenueInventoryId, 1)
}
Add-Insert $sb "cart_venue" @("id", "user_id", "product_id", "inventory_id", "quantity") $cartVenueRows

Set-Utf8NoBomContent $DataSqlPath $sb.ToString()
Upload-DemoAssets

Write-Host "Light demo data generated:" -ForegroundColor Green
Write-Host "  SQL:    $DataSqlPath"
Write-Host "  Assets: $AssetsRoot"
Write-Host "Counts: users=200, venueItems=100, inventory=1200, equipment=50, blogs=200, venueOrders=120, equipmentOrders=80, seckillOrders=100, follows=$($followRows.Count), activities=80, members=$($memberRows.Count), files=120"










