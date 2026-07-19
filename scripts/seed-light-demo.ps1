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

function Add-Section([System.Text.StringBuilder]$Builder, [string]$Title) {
    [void]$Builder.AppendLine("-- $Title")
}

function Add-Insert([System.Text.StringBuilder]$Builder, [string]$Table, [string[]]$Columns, [object[]]$Rows) {
    if (-not $Rows -or $Rows.Count -eq 0) { return }
    [void]$Builder.AppendLine("insert into $Table($($Columns -join ', ')) values")
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $suffix = if ($i -eq $Rows.Count - 1) { ";" } else { "," }
        [void]$Builder.AppendLine("(" + ($Rows[$i] -join ", ") + ")$suffix")
    }
    [void]$Builder.AppendLine()
}

function Set-Utf8NoBomContent([string]$Path, [string]$Value) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Value, $encoding)
}

function FileUrl([int]$Id) {
    return "/objects/hm-badminton/$(FileObjectName $Id)"
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

$SelectedFileIds = @(
    (1..24) +
    (31..42) +
    (56..73) +
    (81..98) +
    (111..118)
)

function Build-DemoAssets {
    if ($SkipAssets) { return }
    $colors = @(
        @("#d9f99d", "#34d399"), @("#bfdbfe", "#38bdf8"), @("#fde68a", "#fb923c"),
        @("#fecdd3", "#f472b6"), @("#ddd6fe", "#8b5cf6"), @("#bbf7d0", "#14b8a6")
    )
    foreach ($id in $SelectedFileIds) {
        $objectName = FileObjectName $id
        $path = Join-Path $AssetsRoot $objectName
        $pair = $colors[($id - 1) % $colors.Count]
        if ($id -le 30) {
            New-DemoImage $path 320 320 "球友 $($id.ToString('00'))" "约个球" $pair[0] $pair[1]
        } elseif ($id -le 55) {
            New-DemoImage $path 900 600 "运动场馆" "场馆服务与团购" $pair[0] $pair[1]
        } elseif ($id -le 80) {
            New-DemoImage $path 720 720 "装备好物" "训练与实战装备" $pair[0] $pair[1]
        } elseif ($id -le 110) {
            New-DemoImage $path 900 900 "球友动态" "训练、装备、场馆体验" $pair[0] $pair[1]
        } else {
            New-DemoImage $path 900 600 "场馆评价" "场地、服务、氛围" $pair[0] $pair[1]
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
    @{ Name = "西安"; Areas = @("雁塔区", "长安区", "未央区"); Lng = 108.9402; Lat = 34.3416 },
    @{ Name = "上海"; Areas = @("浦东新区", "徐汇区", "闵行区"); Lng = 121.4737; Lat = 31.2304 },
    @{ Name = "北京"; Areas = @("朝阳区", "海淀区", "丰台区"); Lng = 116.4074; Lat = 39.9042 },
    @{ Name = "成都"; Areas = @("武侯区", "锦江区", "高新区"); Lng = 104.0665; Lat = 30.5723 }
)

$sports = @(
    @{ Code = "badminton"; Name = "羽毛球" },
    @{ Code = "table_tennis"; Name = "乒乓球" },
    @{ Code = "football"; Name = "足球" },
    @{ Code = "basketball"; Name = "篮球" },
    @{ Code = "tennis"; Name = "网球" },
    @{ Code = "volleyball"; Name = "排球" }
)

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

$equipmentCatalog = @{
    badminton = @(
        @{ Category = 1; Name = "轻羽 5U 新手羽毛球拍"; Brand = "YUDONG"; Price = 89; Description = "适合：新手和力量较小的球友；特点：5U 轻量、甜区大；优点：挥拍轻松、容错高；不足：重杀力量一般。" },
        @{ Category = 2; Name = "疾风缓震羽毛球鞋"; Brand = "FEATHERX"; Price = 329; Description = "适合：每周训练 2 至 3 次的进阶球友；特点：侧向支撑和缓震均衡；优点：急停稳定；不足：鞋楦偏窄。" },
        @{ Category = 1; Name = "破风 7 Pro 进攻羽毛球拍"; Brand = "SHUTTLELAB"; Price = 699; Description = "适合：中高级后场进攻；特点：头重、硬中杆；优点：点杀和重杀有力；不足：对发力和体能要求较高。" }
    )
    table_tennis = @(
        @{ Category = 5; Name = "控旋入门乒乓球拍"; Brand = "SPINUP"; Price = 69; Description = "适合：零基础和娱乐对打；特点：控制稳定、手感柔和；优点：容易上手；不足：远台底劲有限。" },
        @{ Category = 7; Name = "弧圈均衡底板套装"; Brand = "RALLY"; Price = 259; Description = "适合：初中级弧圈结合快攻；特点：持球感清晰；优点：旋转和速度均衡；不足：胶皮需要定期养护。" },
        @{ Category = 5; Name = "极速进攻碳素乒乓球拍"; Brand = "SPINUP"; Price = 599; Description = "适合：进阶快攻球友；特点：碳层支撑、出球快；优点：借力反击直接；不足：小球控制难度较高。" }
    )
    football = @(
        @{ Category = 9; Name = "耐磨训练足球 5 号"; Brand = "COURTGO"; Price = 79; Description = "适合：日常传接球和小场训练；特点：耐磨、气密性好；优点：价格低；不足：湿地触感偏硬。" },
        @{ Category = 8; Name = "疾速 TF 人草足球鞋"; Brand = "STRIKER"; Price = 289; Description = "适合：人造草小场比赛；特点：碎钉抓地、鞋面贴合；优点：启动灵活；不足：不适合天然草。" },
        @{ Category = 10; Name = "轻量防撞护腿板"; Brand = "SAFEPLAY"; Price = 129; Description = "适合：高频对抗和联赛；特点：轻量硬壳、透气内衬；优点：防护稳定；不足：需要搭配固定袜套。" }
    )
    basketball = @(
        @{ Category = 12; Name = "室内外耐磨篮球"; Brand = "BASELINE"; Price = 99; Description = "适合：新手练运球和投篮；特点：纹路深、耐磨；优点：室内外通用；不足：初次使用手感略硬。" },
        @{ Category = 11; Name = "回弹缓震实战篮球鞋"; Brand = "JUMPHIGH"; Price = 359; Description = "适合：后卫突破和急停；特点：前掌回弹、外底抓地；优点：启动快；不足：脚踝包裹中等。" },
        @{ Category = 13; Name = "高强度护膝套装"; Brand = "SAFEPLAY"; Price = 219; Description = "适合：频繁起跳和对抗训练；特点：髌骨支撑、透气；优点：稳定性好；不足：长时间佩戴偏紧。" }
    )
    tennis = @(
        @{ Category = 14; Name = "大甜区新手网球拍"; Brand = "BASELINE"; Price = 159; Description = "适合：新手底线练习；特点：大拍面、轻量；优点：容错高；不足：高速来球稳定性一般。" },
        @{ Category = 14; Name = "全场均衡网球拍"; Brand = "RALLY"; Price = 459; Description = "适合：初中级底线和上网结合；特点：平衡点适中；优点：攻守均衡；不足：旋转上限一般。" },
        @{ Category = 14; Name = "控制型竞赛网球拍"; Brand = "BASELINE"; Price = 899; Description = "适合：进阶全场型球员；特点：小拍面、控球精准；优点：落点反馈清楚；不足：甜区小、上手要求高。" }
    )
    volleyball = @(
        @{ Category = 15; Name = "柔软训练排球"; Brand = "COURTGO"; Price = 89; Description = "适合：新手垫球和校园娱乐；特点：表皮柔软；优点：手臂冲击小；不足：比赛球感略轻。" },
        @{ Category = 15; Name = "室内比赛排球"; Brand = "RALLY"; Price = 259; Description = "适合：社团训练和正式比赛；特点：飞行稳定、触球清晰；优点：控球准确；不足：不耐粗糙室外地面。" },
        @{ Category = 15; Name = "沙排耐候排球"; Brand = "WAVE"; Price = 399; Description = "适合：沙滩和户外训练；特点：防水耐候、表面防滑；优点：潮湿环境稳定；不足：室内触感偏重。" }
    )
}

$levels = @("新手", "初级", "中级", "高级")
$times = @("工作日 19:00 后", "周二/周四晚上", "周末上午")
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("set names utf8mb4;")
[void]$sb.AppendLine()

Build-DemoAssets

Add-Section $sb "MinIO 文件元数据：仅保留页面会引用的 80 个对象"
$fileRows = @()
foreach ($id in $SelectedFileIds) {
    $objectName = FileObjectName $id
    $assetPath = Join-Path $AssetsRoot $objectName
    $fileSize = if (Test-Path -LiteralPath $assetPath) { (Get-Item -LiteralPath $assetPath).Length } else { 65536 }
    $fileRows += ,@($id, "null", (Sql (BizTypeForFile $id)), "null", (Sql "hm-badminton"), (Sql $objectName),
        (Sql (Split-Path $objectName -Leaf)), (Sql "image/png"), $fileSize, (Sql "demo-$id"), (Sql (FileUrl $id)), (Sql "可用"))
}
Add-Insert $sb "file_metadata" @("id", "owner_user_id", "biz_type", "biz_id", "bucket_name", "object_name", "original_filename", "content_type", "file_size", "etag", "public_url", "status") $fileRows

Add-Section $sb "用户和球友资料：西安每种运动 3 人，其余城市每种运动 2 人"
$userRows = @()
$profileRows = @()
$usersBySlot = @{}
$usersBySport = @{}
foreach ($sport in $sports) { $usersBySport[$sport.Code] = @() }
$userId = 1
for ($cityIndex = 0; $cityIndex -lt $cities.Count; $cityIndex++) {
    $city = $cities[$cityIndex]
    for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
        $sport = $sports[$sportIndex]
        $personaCount = if ($city.Name -eq "西安") { 3 } else { 2 }
        $slot = "$($city.Name)|$($sport.Code)"
        $usersBySlot[$slot] = @()
        for ($persona = 1; $persona -le $personaCount; $persona++) {
            $id = $userId++
            $area = $city.Areas[($sportIndex + $persona - 1) % $city.Areas.Count]
            $nickname = if ($id -eq 1) { "陈予" } elseif ($persona -eq 1) { "$($city.Name)$($sport.Name)达人" } elseif ($persona -eq 2) { "$($city.Name)$($sport.Name)搭子" } else { "$($city.Name)$($sport.Name)新秀" }
            $level = $levels[($sportIndex + $persona) % $levels.Count]
            $isBigV = if ($persona -eq 1 -and ($sportIndex -eq 0 -or $sportIndex -eq 2)) { 1 } else { 0 }
            $avatarId = (($id - 1) % 24) + 1
            $lng = $city.Lng + (($sportIndex - 2.5) * 0.004) + ($persona * 0.0005)
            $lat = $city.Lat + (($persona - 1) * 0.002) + ($sportIndex * 0.0004)
            $userRows += ,@($id, (Sql ("139{0:D8}" -f $id)), (Sql ("demo{0:D3}@example.com" -f $id)),
                (Sql ("demo_user_{0:D3}" -f $id)), (Sql "{plain}123456"), (Sql $nickname), (Sql (FileUrl $avatarId)),
                (Sql $city.Name), (Sql $level), (Sql $times[($persona - 1) % $times.Count]), $isBigV, 1,
                "date_sub(now(), interval $($id + 3) day)")
            $profileRows += ,@($id, (Sql $sport.Code), (Sql $city.Name), (Sql $area),
                $lng.ToString("0.000000", $Invariant), $lat.ToString("0.000000", $Invariant), (Sql $level),
                (Sql @("稳健多拍", "主动进攻", "轻松娱乐")[$persona - 1]), (Sql $times[($persona - 1) % $times.Count]),
                (Sql "常打$($sport.Name)，希望找到时间稳定、守时好沟通的搭子。"), 1)
            $usersBySlot[$slot] = @($usersBySlot[$slot] + $id)
            $usersBySport[$sport.Code] = @($usersBySport[$sport.Code] + $id)
        }
    }
}
Add-Insert $sb "users" @("id", "phone", "email", "username", "password_hash", "nickname", "avatar", "city", "level", "prefer_time", "is_big_v", "status", "created_at") $userRows
Add-Insert $sb "player_profiles" @("user_id", "sport_code", "city", "area", "longitude", "latitude", "level", "play_style", "available_time", "intro", "allow_invite") $profileRows

Add-Section $sb "关注关系：优先关注同城同运动达人，并补充跨城同运动内容"
$followRows = @()
$seenFollows = @{}
for ($id = 1; $id -lt $userId; $id++) {
    $own = $profileRows[$id - 1]
    $sportCode = ([string]$own[1]).Trim("'")
    $sameSport = $usersBySport[$sportCode]
    $targets = @($sameSport[0], $sameSport[[Math]::Min(2, $sameSport.Count - 1)], (($id + 6) % ($userId - 1)) + 1)
    foreach ($target in $targets) {
        if ($target -eq $id) { continue }
        $key = "$id-$target"
        if (-not $seenFollows.ContainsKey($key)) {
            $seenFollows[$key] = $true
            $followRows += ,@($id, $target)
        }
    }
}
Add-Insert $sb "follows" @("user_id", "follow_user_id") $followRows

Add-Section $sb "本地场所模板与评价：城市、运动和评价人严格匹配"
$placeRows = @()
$reviewRows = @()
$placeId = 1
$reviewId = 1
for ($cityIndex = 0; $cityIndex -lt $cities.Count; $cityIndex++) {
    $city = $cities[$cityIndex]
    for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
        $sport = $sports[$sportIndex]
        $matchedUsers = $usersBySlot["$($city.Name)|$($sport.Code)"]
        for ($rank = 1; $rank -le 2; $rank++) {
            $id = $placeId++
            $area = $city.Areas[($sportIndex + $rank - 1) % $city.Areas.Count]
            $isBudget = $rank -eq 1
            $name = if ($isBudget) { "$($city.Name)$($sport.Name)通勤馆" } else { "$($city.Name)$($sport.Name)训练中心" }
            $price = if ($isBudget) { 29 + $sportIndex * 4 } else { 68 + $sportIndex * 9 }
            $score = if ($isBudget) { 4.3 + (($sportIndex % 2) * 0.1) } else { 4.7 + (($sportIndex % 2) * 0.1) }
            $facilities = if ($isBudget) {
                "近地铁,夜场灯光,饮水机;适合:下班快打、新手练习;不足:晚高峰较拥挤、停车位少"
            } else {
                "空调,停车,淋浴,专业地胶;适合:进阶训练、亲子体验;不足:价格较高、距离市中心略远"
            }
            $openHours = if ($isBudget) { "10:00-23:00" } else { "08:00-22:00" }
            $reviewRating = if ($isBudget) { 4 } else { 5 }
            $placeRows += ,@($id, (Sql $sport.Code), (Sql $name), (Sql $city.Name), (Sql $area),
                (Sql "$area 运动路 $($sportIndex * 10 + $rank) 号"),
                ($city.Lng + ($sportIndex * 0.004) + ($rank * 0.002)).ToString("0.000000", $Invariant),
                ($city.Lat + ($sportIndex * 0.003) + ($rank * 0.001)).ToString("0.000000", $Invariant),
                $price, (Money $score), 1, (Sql $openHours),
                (Sql (FileUrl (31 + (($id - 1) % 12)))), (Sql $facilities), 1)
            $reviewContent = if ($isBudget) {
                "$($city.Name)的$($sport.Name)场地离地铁近，夜场灯光够用，价格友好；晚高峰更衣区会有些拥挤。"
            } else {
                "$($sport.Name)场地维护和淋浴都不错，教练服务专业，适合系统训练；价格比周边普通馆略高。"
            }
            $reviewRows += ,@($reviewId++, $id, $matchedUsers[($rank - 1) % $matchedUsers.Count], $reviewRating,
                (Sql $reviewContent), (Sql (FileUrl (111 + (($id - 1) % 8)))), (3 + (($id * 7) % 29)),
                "date_sub(now(), interval $($id % 18 + 1) day)")
        }
    }
}
Add-Insert $sb "place" @("id", "sport_code", "name", "city", "area", "address", "longitude", "latitude", "avg_price", "score", "review_count", "open_hours", "cover_url", "facilities", "status") $placeRows
Add-Insert $sb "venue_reviews" @("id", "venue_id", "user_id", "rating", "content", "image_urls", "likes", "created_at") $reviewRows

Add-Section $sb "场馆号与教练：按城市、运动、附近场所顺序绑定"
$operatorRows = @()
$operatorId = 1
foreach ($city in $cities) {
    foreach ($sport in $sports) {
        $matchedUsers = $usersBySlot["$($city.Name)|$($sport.Code)"]
        for ($rank = 1; $rank -le 2; $rank++) {
            $operatorRows += ,@($operatorId++, $matchedUsers[0], (Sql $city.Name), (Sql $sport.Code), $rank,
                (Sql "$($city.Name)$($sport.Name)场馆号 $rank"), (Sql (FileUrl ((($matchedUsers[0] - 1) % 24) + 1))),
                (Sql "发布$($sport.Name)团购、开放时段和约球活动，信息由平台演示数据维护。"), 1)
        }
    }
}
Add-Insert $sb "venue_operators" @("id", "user_id", "city", "sport_code", "place_rank", "operator_name", "avatar", "intro", "status") $operatorRows

$coachRows = @()
$coachId = 1
for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
    $sport = $sports[$sportIndex]
    for ($rank = 1; $rank -le 2; $rank++) {
        $coachLevel = if ($rank -eq 1) { "基础认证" } else { "高级认证" }
        $coachTags = if ($rank -eq 1) { "新手纠错,基础动作" } else { "实战战术,进阶训练" }
        $coachIntro = if ($rank -eq 1) { "耐心讲解基础动作，适合第一次体验。" } else { "侧重实战节奏和专项技术，训练强度较高。" }
        $coachPrice = if ($rank -eq 1) { 99 + $sportIndex * 10 } else { 169 + $sportIndex * 15 }
        $coachRows += ,@($coachId, $rank, "null", (Sql "$rank"), (Sql $sport.Code),
            (Sql "$($sport.Name)教练 $rank"), (Sql (FileUrl ((($coachId + 4) % 24) + 1))),
            (Sql $coachLevel), (Sql $coachTags), (Sql $coachIntro), (Money $coachPrice), 1)
        $coachId++
    }
}
Add-Insert $sb "coaches" @("id", "venue_id", "amap_place_id", "venue_name", "sport_code", "name", "avatar", "level", "tags", "intro", "price_per_hour", "status") $coachRows

Add-Section $sb "装备分类与商品：每种运动 3 件，覆盖入门、进阶和专项"
Add-Insert $sb "equipment_categories" @("id", "sport_code", "name", "icon", "sort") $categoryRows
$equipmentRows = @()
$equipmentMetaById = @{}
$equipmentIdsBySport = @{}
$equipmentId = 1
for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
    $sport = $sports[$sportIndex]
    $equipmentIdsBySport[$sport.Code] = @()
    $items = $equipmentCatalog[$sport.Code]
    for ($itemIndex = 0; $itemIndex -lt $items.Count; $itemIndex++) {
        $item = $items[$itemIndex]
        $id = $equipmentId++
        $cover = FileUrl (56 + (($id - 1) % 18))
        $score = @(4.3, 4.6, 4.8)[$itemIndex]
        $stock = @(80, 45, 20)[$itemIndex]
        $sold = @(128, 76, 35)[$itemIndex] + ($sportIndex * 7)
        $equipmentRows += ,@($id, (Sql $sport.Code), $item.Category, (Sql $item.Name), (Sql $item.Brand),
            (Sql $item.Description), (Sql $cover), (Money $item.Price), $stock, (Money $score), $sold, 1)
        $equipmentMetaById[$id] = @{ Title = $item.Name; Cover = $cover; Price = [double]$item.Price; Sport = $sport.Code }
        $equipmentIdsBySport[$sport.Code] = @($equipmentIdsBySport[$sport.Code] + $id)
    }
}
Add-Insert $sb "equipment" @("id", "sport_code", "category_id", "name", "brand", "description", "cover_url", "price", "stock", "score", "sold", "status") $equipmentRows

Add-Section $sb "场馆商品和未来库存：每种运动 2 个场所顺序，每个顺序 3 类商品"
$venueRows = @()
$inventoryRows = @()
$venueMetaById = @{}
$venueIdsBySport = @{}
$productTypes = @("TIME_PACKAGE", "COURT_SLOT", "COACH_LESSON")
$venueId = 1
$inventoryId = 1
for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
    $sport = $sports[$sportIndex]
    $venueIdsBySport[$sport.Code] = @()
    for ($rank = 1; $rank -le 2; $rank++) {
        for ($typeIndex = 0; $typeIndex -lt $productTypes.Count; $typeIndex++) {
            $type = $productTypes[$typeIndex]
            $id = $venueId++
            $basePrice = if ($type -eq "TIME_PACKAGE") { 25 + $sportIndex * 4 } elseif ($type -eq "COURT_SLOT") { 58 + $sportIndex * 12 } else { 89 + $sportIndex * 15 }
            $rankPremium = if ($type -eq "COACH_LESSON") { 50 } else { 16 }
            $price = $basePrice + (($rank - 1) * $rankPremium)
            $title = if ($type -eq "TIME_PACKAGE") { "$($sport.Name) 08:00-12:00 单人畅打" } elseif ($type -eq "COURT_SLOT") { "$($sport.Name) 晚间黄金单场 1 小时" } else { "$($sport.Name) 私教体验 60 分钟" }
            $description = if ($type -eq "TIME_PACKAGE") {
                if ($rank -eq 1) { "工作日上午低峰套餐，适合新手练习和双人拉球；价格低，但周末不可用。" } else { "周末上午畅打套餐，含更衣和淋浴；环境更舒适，但需要提前一天预约。" }
            } elseif ($type -eq "COURT_SLOT") {
                if ($rank -eq 1) { "19:00 后热门单场，靠近地铁，适合下班快打；余位较少。" } else { "专业场地黄金时段，灯光和地胶更好；适合进阶对抗，价格略高。" }
            } else {
                if ($rank -eq 1) { "新手体验课，包含动作评估和基础纠错；两人即可开课。" } else { "进阶专项课，包含实战节奏和技术训练；强度较高，适合有基础球友。" }
            }
            $isDiscount = ($typeIndex + $rank + $sportIndex) % 2 -eq 0
            $original = if ($isDiscount) { Money ($price + 20 + $typeIndex * 15) } else { "null" }
            $tags = if ($rank -eq 1) { "$($sport.Name),近地铁,预算友好,适合新手" } else { "$($sport.Name),可停车,有淋浴,进阶训练" }
            $refundRule = if ($type -eq "COURT_SLOT") { "开场前 4 小时可退，逾期不可退。" } else { "使用前 2 小时可退，已核销不可退。" }
            $cover = FileUrl (31 + (($sportIndex * 2 + $rank - 1) % 12))
            $venueRows += ,@($id, "null", "null", (Sql "$rank"), $rank, (Sql $sport.Code), (Sql $type),
                (Sql $title), (Sql $description), (Sql $cover), (Money $price), $original, (Sql $tags),
                (Sql "购买后选择日期和时段，到店出示核销码；每人每天限购 1 份。"), (Sql $refundRule),
                "date_sub(now(), interval 1 day)", "date_add(now(), interval 365 day)", 1)
            $venueMetaById[$id] = @{ Title = $title; Cover = $cover; Price = [double]$price; Sport = $sport.Code; Rank = $rank }
            $venueIdsBySport[$sport.Code] = @($venueIdsBySport[$sport.Code] + $id)
            for ($day = 1; $day -le 4; $day++) {
                $stock = if ($type -eq "TIME_PACKAGE") { 12 - $rank } elseif ($type -eq "COURT_SLOT") { 1 } else { 4 - $rank }
                $sold = if ($type -eq "COURT_SLOT") { 0 } else { ($day + $rank + $sportIndex) % 3 }
                $available = [Math]::Max(1, $stock - $sold)
                $startTime = if ($type -eq "TIME_PACKAGE") { "08:00:00" } elseif ($type -eq "COURT_SLOT") { "19:00:00" } else { "18:00:00" }
                $endTime = if ($type -eq "TIME_PACKAGE") { "12:00:00" } elseif ($type -eq "COURT_SLOT") { "20:00:00" } else { "19:00:00" }
                $courtName = if ($type -eq "COACH_LESSON") { "私教训练场" } else { "标准场地 $rank" }
                $inventoryCoachId = if ($type -eq "COACH_LESSON") { (($sportIndex * 2) + $rank) } else { "null" }
                $inventoryRows += ,@($inventoryId++, $id, "null", (Sql $courtName), $inventoryCoachId,
                    "date_add(current_date, interval $day day)", (Sql $startTime), (Sql $endTime), $stock, $available, 0, $sold, (Money $price), (Sql "可售"))
            }
        }
    }
}
Add-Insert $sb "venue" @("id", "venue_id", "amap_place_id", "venue_name", "place_rank", "sport_code", "product_type", "title", "description", "cover_url", "price", "original_price", "tags", "use_rule", "refund_rule", "sale_start_at", "sale_end_at", "status") $venueRows
Add-Insert $sb "venue_inventory" @("id", "product_id", "venue_id", "court_name", "coach_id", "service_date", "start_time", "end_time", "total_stock", "available_stock", "locked_stock", "sold_stock", "price", "status") $inventoryRows
Add-Section $sb '演示窗口覆盖今天到未来 3 天，既能演示“今晚”，也保留后续预约日期。'
[void]$sb.AppendLine("update venue_inventory set service_date = date_sub(service_date, interval 1 day);")
[void]$sb.AppendLine()

Add-Section $sb "博客：每种运动 6 篇，分别覆盖场馆、装备、预算、训练和避坑"
$blogRows = @()
$blogTitles = @("新手第一套装备怎么选", "下班后一小时场馆体验", "进阶训练装备实测", "预算内的周末场地", "训练中最容易忽略的细节", "本周约球复盘")
$blogContents = @(
    "从价格、容错和使用频率出发选择，不必一步到顶。优点是容易上手，缺点是高强度对抗上限有限。",
    "交通和可预约时段比装修更重要。这个选择离地铁近、价格友好，但晚高峰会比较拥挤。",
    "连续训练后更关注支撑和稳定。它的实战反馈直接，但对动作基础和体能有一定要求。",
    "按距离、时段和退款规则对比后，这个方案总价可控；不足是热门时间需要提前预约。",
    "热身、补水和节奏控制会直接影响体验，新手先稳定动作，进阶球友再增加对抗强度。",
    "本周组局整体顺利，场地灯光和队友沟通都不错；下次会避开晚高峰并提前确认人数。"
)
$blogId = 1
for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
    $sport = $sports[$sportIndex]
    $authors = $usersBySport[$sport.Code]
    for ($i = 0; $i -lt 6; $i++) {
        $isEquipment = $i % 2 -eq 0
        if ($isEquipment) {
            $relatedId = $equipmentIdsBySport[$sport.Code][($i / 2) % 3]
            $relatedType = "EQUIPMENT"
            $meta = $equipmentMetaById[$relatedId]
        } else {
            $relatedId = $venueIdsBySport[$sport.Code][$i % $venueIdsBySport[$sport.Code].Count]
            $relatedType = "VENUE_PRODUCT"
            $meta = $venueMetaById[$relatedId]
        }
        $blogRows += ,@($blogId, $authors[$i % $authors.Count], (Sql $sport.Code), (Sql "$($sport.Name)：$($blogTitles[$i])"),
            (Sql $blogContents[$i]), (Sql (FileUrl (81 + (($blogId - 1) % 18)))), (Sql $relatedType), $relatedId,
            (Sql $meta.Title), (Sql $meta.Cover), (Money $meta.Price), (12 + (($blogId * 17) % 180)), 1,
            "date_sub(now(), interval $($blogId * 3) hour)")
        $blogId++
    }
}
Add-Insert $sb "blogs" @("id", "user_id", "sport_code", "title", "content", "image_urls", "related_type", "related_id", "related_title", "related_cover_url", "related_price", "liked", "status", "created_at") $blogRows

Add-Section $sb "订单初始化为空：登录后的首次购买从正常下单或秒杀链路产生"

Add-Section $sb "秒杀：每种运动各 1 个装备和场馆商品，秒杀价始终低于原商品价"
$seckillEquipmentRows = @()
$seckillVenueRows = @()
for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
    $seckillId = $sportIndex + 1
    $equipmentIdForSeckill = $equipmentIdsBySport[$sports[$sportIndex].Code][0]
    $equipmentMeta = $equipmentMetaById[$equipmentIdForSeckill]
    $equipmentSeckillPrice = [Math]::Max(1, [Math]::Floor($equipmentMeta.Price * 0.72))
    $seckillEquipmentRows += ,@($seckillId, $equipmentIdForSeckill, (Money $equipmentSeckillPrice), (18 + $sportIndex * 3),
        "date_sub(now(), interval 1 day)", "date_add(now(), interval 90 day)", 1)
    $venueIdForSeckill = $venueIdsBySport[$sports[$sportIndex].Code][0]
    $venueMeta = $venueMetaById[$venueIdForSeckill]
    $venueSeckillPrice = [Math]::Max(1, [Math]::Floor($venueMeta.Price * 0.70))
    $seckillVenueRows += ,@($seckillId, $venueIdForSeckill, (Money $venueSeckillPrice), (12 + $sportIndex * 2),
        "date_sub(now(), interval 1 day)", "date_add(now(), interval 90 day)", 1)
}
Add-Insert $sb "seckill_equipment" @("id", "equipment_id", "seckill_price", "stock", "start_at", "end_at", "status") $seckillEquipmentRows
Add-Insert $sb "seckill_venue" @("id", "venue_id", "seckill_price", "stock", "start_at", "end_at", "status") $seckillVenueRows

Add-Section $sb "约球活动：每个城市、每种运动 1 场；全部安排在当天，水平、费用和余位有差异"
$activityRows = @()
$memberRows = @()
$activityId = 1
$memberId = 1
for ($cityIndex = 0; $cityIndex -lt $cities.Count; $cityIndex++) {
    $city = $cities[$cityIndex]
    for ($sportIndex = 0; $sportIndex -lt $sports.Count; $sportIndex++) {
        $sport = $sports[$sportIndex]
        $matchedUsers = $usersBySlot["$($city.Name)|$($sport.Code)"]
        $maxPlayers = @(4, 6, 8, 6, 4, 8)[$sportIndex]
        $currentPlayers = if (($activityId % 4) -eq 0) { $maxPlayers - 1 } else { [Math]::Min(3, $matchedUsers.Count) }
        $currentPlayers = [Math]::Min($currentPlayers, $matchedUsers.Count)
        $level = @("新手友好", "初级以上", "中级对抗", "不限")[$activityId % 4]
        $fee = @("AA", "免费", "场地费均摊")[$activityId % 3]
        $creator = $matchedUsers[0]
        $activityKind = if ($level -eq "新手友好") { "新手友好局" } else { "下班对抗局" }
        $activityRows += ,@($activityId, (Sql $sport.Code), $creator, (($sportIndex * 2) + (($activityId % 2) + 1)),
            (Sql "amap"), (Sql "DEMO_$($cityIndex + 1)_$($sportIndex + 1)"), (Sql "$($city.Name)$($sport.Name)附近场地"),
            (Sql "$($sport.Name)$activityKind"), (Sql $city.Name),
            "timestamp(current_date, '19:00:00')",
            "timestamp(current_date, '21:00:00')",
            $maxPlayers, $currentPlayers, (Sql $level), (Sql $fee), (Sql "招募中"))
        for ($m = 0; $m -lt $currentPlayers; $m++) {
            $memberUser = $matchedUsers[$m % $matchedUsers.Count]
            $memberRole = if ($m -eq 0) { "OWNER" } else { "MEMBER" }
            $memberRows += ,@($memberId++, $activityId, $memberUser, (Sql $memberRole), (Sql "已加入"))
        }
        $activityId++
    }
}
Add-Insert $sb "sport_activities" @("id", "sport_code", "creator_id", "venue_id", "place_source", "place_id", "venue_name", "title", "city", "start_time", "end_time", "max_players", "current_players", "level_required", "fee_type", "status") $activityRows
Add-Insert $sb "sport_activity_members" @("id", "activity_id", "user_id", "role", "status") $memberRows

Add-Section $sb "购物车初始化为空：用户主动加购后才写入"

Set-Utf8NoBomContent $DataSqlPath $sb.ToString()
Upload-DemoAssets

Write-Host "Light demo data generated:" -ForegroundColor Green
Write-Host "  SQL:    $DataSqlPath"
Write-Host "  Assets: $AssetsRoot"
Write-Host "Counts: users=$($userRows.Count), places=$($placeRows.Count), reviews=$($reviewRows.Count), venueItems=$($venueRows.Count), inventory=$($inventoryRows.Count), equipment=$($equipmentRows.Count), blogs=$($blogRows.Count), activities=$($activityRows.Count), files=$($fileRows.Count)"
