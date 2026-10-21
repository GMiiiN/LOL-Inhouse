# 역할: 백업 폴더(db.sql, images.zip)로 현재 DB와 결과창 이미지를 되돌린다. 현재 데이터는 덮어써진다.
# 실행: powershell -NoProfile -ExecutionPolicy Bypass -File infra\restore.ps1 backups\<폴더이름>   (실패하면 종료 코드 1)
# 순서: 이미지를 임시 폴더에 먼저 풀어 검증 -> 앱 중지 -> DB 복원 -> 이미지 교체 -> 앱 재시작.
# 실패하면 앱을 일부러 멈춘 채로 둔다(반쯤 복원된 데이터에 새 요청이 쌓이는 것을 막기 위해). 안내 메시지를 읽고 원인을 해결한 뒤 같은 백업으로 다시 실행한다.
param(
    [Parameter(Mandatory = $true)][string]$BackupDir
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'infra\compose.desktop.yml'
$envFile = Join-Path $root '.env'

function Dc {
    & docker compose -f $compose --env-file $envFile @args 2>&1 | Out-Null
    return $LASTEXITCODE
}

if (-not [System.IO.Path]::IsPathRooted($BackupDir)) { $BackupDir = Join-Path (Get-Location) $BackupDir }
$dbFile = Join-Path $BackupDir 'db.sql'
$zip    = Join-Path $BackupDir 'images.zip'
if (-not (Test-Path $dbFile)) { Write-Host "db.sql이 없어요: $BackupDir"; exit 1 }

Write-Host "복원할 백업: $BackupDir"
Write-Host '현재 DB와 결과창 이미지가 이 백업 내용으로 바뀝니다.'
$answer = Read-Host '정말 덮어쓸까요? (yes 입력)'
if ($answer -ne 'yes') { Write-Host '취소했어요. 아무것도 바꾸지 않았어요.'; exit 0 }

$images  = Join-Path $root 'data\images'
$tmpName = 'restore-' + [guid]::NewGuid().ToString('N') + '.sql'
$staging = Join-Path $root ('data\images.restore-tmp-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))

function Abort($message, $state) {
    Dc exec -T mysql rm -f "/tmp/$tmpName" | Out-Null
    Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
    Write-Host "복원 실패: $message"
    Write-Host "상태: $state"
    Write-Host '앱(web, backend)은 멈춘 채로 둡니다. 원인을 해결하고 같은 백업으로 다시 실행하세요. 켜려면: docker compose -f infra/compose.desktop.yml --env-file .env up -d'
    exit 1
}

# 1) 이미지를 임시 폴더에 풀어 둔다(현재 데이터는 아직 안 건드림)
if (Test-Path $zip) {
    try {
        New-Item -ItemType Directory -Path $staging -ErrorAction Stop | Out-Null
        Expand-Archive -Path $zip -DestinationPath $staging -ErrorAction Stop
    } catch {
        Abort "이미지 압축 해제 실패: $($_.Exception.Message)" '아무것도 바뀌지 않았어요(앱은 아직 켜져 있어요).'
    }
}

# 2) 복원 중에 새 데이터가 들어오지 않게 앱을 멈춘다(mysql은 켜 둔다)
if ((Dc stop web backend) -ne 0) { Abort '앱 중지 실패' '아무것도 바뀌지 않았어요.' }

# 3) DB
if ((Dc cp $dbFile "mysql:/tmp/$tmpName") -ne 0) { Abort 'SQL 파일을 컨테이너로 복사하지 못했어요' 'DB는 그대로예요(앱은 멈춤).' }
$restoreCmd = "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql -uroot --default-character-set=utf8mb4 < /tmp/$tmpName"
if ((Dc exec -T mysql sh -c $restoreCmd) -ne 0) { Abort 'DB 복원 실패' 'DB가 중간까지만 복원됐을 수 있어요(이미지는 그대로). 다시 실행하면 처음부터 덮어씁니다.' }
Dc exec -T mysql rm -f "/tmp/$tmpName" | Out-Null

# 4) 이미지 교체: 기존 폴더는 지우지 않고 data\ 안에 옮겨 둔다(문제없으면 직접 지우세요)
if (Test-Path $zip) {
    try {
        if (Test-Path $images) {
            Rename-Item $images ('images.before-restore-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) -ErrorAction Stop
        }
        Rename-Item $staging 'images' -ErrorAction Stop
    } catch {
        Abort "이미지 교체 실패: $($_.Exception.Message)" 'DB는 복원됐고 이미지는 이전 상태이거나 data\images.before-restore-* 에 있어요.'
    }
}

# 5) 앱 재시작
if ((Dc up -d) -ne 0) { Abort '앱 재시작 실패' 'DB와 이미지는 복원됐어요.' }
Write-Host '복원 완료. 앱을 다시 시작했어요(몇십 초 뒤 접속 가능).'
exit 0
