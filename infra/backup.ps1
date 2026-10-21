# 역할: DB 덤프 + 결과창 이미지 압축을 backups\yyyyMMdd-HHmmss\ 에 만들고, 모든 단계가 성공한 뒤에만 14일 넘은 백업을 지운다.
# 실행: powershell -NoProfile -ExecutionPolicy Bypass -File infra\backup.ps1   (실패하면 종료 코드 1)
# DB 비밀번호는 호스트 명령줄에 쓰지 않는다. 컨테이너 안의 환경변수(MYSQL_ROOT_PASSWORD)를 컨테이너 안에서만 읽는다.
$ErrorActionPreference = 'Continue'   # docker가 진행 상황을 stderr로 내보내도 오류로 취급하지 않는다. 종료 코드를 직접 본다.

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'infra\compose.desktop.yml'
$envFile = Join-Path $root '.env'
$backups = Join-Path $root 'backups'
New-Item -ItemType Directory -Path $backups -Force | Out-Null

# 실행마다 고유한 폴더: 초까지 쓰고, 이미 있으면(같은 초 재실행) -2, -3 ... 을 붙인다. 기존 백업을 절대 재사용·삭제하지 않는다.
$stamp  = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $backups $stamp
for ($n = 2; Test-Path $target; $n++) { $target = Join-Path $backups "$stamp-$n" }
New-Item -ItemType Directory -Path $target | Out-Null
$tmpName = 'db-' + [guid]::NewGuid().ToString('N') + '.sql'   # 컨테이너 안 임시 파일(이번 실행 전용)

function Dc {
    & docker compose -f $compose --env-file $envFile @args 2>&1 | Out-Null
    return $LASTEXITCODE
}

function Fail($message) {
    Dc exec -T mysql rm -f "/tmp/$tmpName" | Out-Null
    Remove-Item -Recurse -Force $target -ErrorAction SilentlyContinue   # 이번 실행이 만든 폴더만
    Write-Host "백업 실패: $message"
    exit 1
}

# 1) DB: 컨테이너 안에서 덤프 파일을 만들고 docker compose cp로 꺼낸다(PowerShell 5.1의 > 리다이렉트는 UTF-16으로 바꿔 한글이 깨진다)
$dumpCmd = "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --routines --databases `"`$MYSQL_DATABASE`" > /tmp/$tmpName"
if ((Dc exec -T mysql sh -c $dumpCmd) -ne 0) { Fail 'mysqldump 실패(Docker가 켜져 있고 mysql 컨테이너가 떠 있는지 확인하세요)' }
$dbFile = Join-Path $target 'db.sql'
if ((Dc cp "mysql:/tmp/$tmpName" $dbFile) -ne 0) { Fail 'db.sql을 컨테이너에서 꺼내지 못했어요' }
Dc exec -T mysql rm -f "/tmp/$tmpName" | Out-Null
if (-not (Test-Path $dbFile) -or (Get-Item $dbFile).Length -eq 0) { Fail 'db.sql이 비어 있어요' }

# 2) 이미지
$images = Join-Path $root 'data\images'
$imageNote = '이미지 없음'
if ((Test-Path $images) -and (Get-ChildItem $images -Force | Select-Object -First 1)) {
    $zip = Join-Path $target 'images.zip'
    try {
        Compress-Archive -Path (Join-Path $images '*') -DestinationPath $zip -ErrorAction Stop
    } catch {
        Fail "이미지 압축 실패: $($_.Exception.Message)"
    }
    if (-not (Test-Path $zip) -or (Get-Item $zip).Length -eq 0) { Fail 'images.zip이 만들어지지 않았어요' }
    $imageNote = '이미지 ' + [math]::Round((Get-Item $zip).Length / 1MB, 1) + 'MB'
}

# 3) 모두 성공한 뒤에만: 14일 넘은 백업 삭제(backups 바로 아래의, 이 스크립트가 만드는 이름 형식의 폴더만)
$cutoff = (Get-Date).AddDays(-14)
Get-ChildItem $backups -Directory |
    Where-Object { $_.Name -match '^\d{8}-(\d{4}|\d{6}(-\d+)?)$' -and $_.LastWriteTime -lt $cutoff } |
    Remove-Item -Recurse -Force

$dbSize = [math]::Round((Get-Item $dbFile).Length / 1KB)
Write-Host "백업 완료: $target (DB ${dbSize}KB, $imageNote)"
exit 0
