# 역할: 내전 기록을 처음부터 다시 시작한다. 먼저 백업을 만들고, 지울 개수를 보여 준 뒤 'yes'를 입력해야 지운다.
# 지우는 것: 내전 묶음·대결·세트(선수 기록·밴 포함)·추출 작업, 미등록 선수(실명 없는 선수)와 그 닉네임, 결과창 이미지 파일.
# 남기는 것: 등록 선수(실명·닉네임·라인 티어), 점수표, 담당자 계정, 챔피언 캐시.
# 실행: powershell -NoProfile -ExecutionPolicy Bypass -File infra\reset-records.ps1
# DB 비밀번호는 호스트 명령줄에 쓰지 않는다. 컨테이너 안의 환경변수(MYSQL_ROOT_PASSWORD)를 컨테이너 안에서만 읽는다.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 저장소 맨 위(lol-inhouse)에 두고 실행해도, infra 폴더 안에서 실행해도 된다
$root = if (Test-Path (Join-Path $PSScriptRoot 'infra\compose.desktop.yml')) { $PSScriptRoot } else { Split-Path -Parent $PSScriptRoot }
$compose = Join-Path $root 'infra\compose.desktop.yml'
$envFile = Join-Path $root '.env'
$tmpName = 'reset-' + [guid]::NewGuid().ToString('N') + '.sql'

function Dc {
    & docker compose -f $compose --env-file $envFile @args 2>&1 | Out-Null
    return $LASTEXITCODE
}

# SQL 파일을 컨테이너로 복사해 실행하고 결과 글자를 돌려준다(실패하면 $null)
function RunSql($sql) {
    $local = Join-Path $env:TEMP $tmpName
    [System.IO.File]::WriteAllText($local, $sql, (New-Object System.Text.UTF8Encoding($false)))
    try {
        if ((Dc cp $local "mysql:/tmp/$tmpName") -ne 0) { return $null }
        $cmd = "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql -uroot -N --default-character-set=utf8mb4 `"`$MYSQL_DATABASE`" < /tmp/$tmpName"
        $out = & docker compose -f $compose --env-file $envFile exec -T mysql sh -c $cmd 2>&1
        $code = $LASTEXITCODE
        Dc exec -T mysql rm -f "/tmp/$tmpName" | Out-Null
        if ($code -ne 0) { return $null }
        return ($out | Out-String).Trim()
    } finally {
        Remove-Item $local -ErrorAction SilentlyContinue
    }
}

# 1) 백업(실패하면 아무것도 지우지 않는다)
Write-Host '1/3 백업을 만듭니다...'
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'infraackup.ps1')
if ($LASTEXITCODE -ne 0) { Write-Host '백업이 실패해서 중단했어요. 아무것도 지우지 않았어요.'; exit 1 }

# 2) 지울 개수 보여 주기
$counts = RunSql @'
SELECT CONCAT('내전 묶음 ', (SELECT COUNT(*) FROM inhouse_session), '개 · 대결 ', (SELECT COUNT(*) FROM duel),
  '개 · 세트 ', (SELECT COUNT(*) FROM game_set), '개 · 추출 작업 ', (SELECT COUNT(*) FROM extraction_job),
  '개 · 미등록 선수 ', (SELECT COUNT(*) FROM player WHERE real_name IS NULL), '명');
'@
if ($null -eq $counts) { Write-Host '개수를 읽지 못해 중단했어요(Docker와 mysql 컨테이너가 켜져 있는지 확인하세요).'; exit 1 }
$images = Join-Path $root 'data\images'
$imageCount = 0
if (Test-Path $images) { $imageCount = @(Get-ChildItem $images -File -Recurse -Force).Count }
Write-Host ''
Write-Host "2/3 지울 것: $counts · 결과창 이미지 ${imageCount}개"
Write-Host '    남기는 것: 등록 선수(실명·닉네임·라인 티어), 점수표, 담당자 계정'
$answer = Read-Host '정말 지울까요? 지우려면 yes 입력'
if ($answer -ne 'yes') { Write-Host '취소했어요. 아무것도 바꾸지 않았어요.'; exit 0 }

# 3) 한 트랜잭션으로 지운다(외래 키 순서: 추출 작업 → 세트(선수 기록·밴은 함께) → 대결 → 묶음 → 미등록 선수의 닉네임 → 미등록 선수)
$done = RunSql @'
START TRANSACTION;
DELETE FROM extraction_job;
DELETE FROM game_set;
DELETE FROM duel;
DELETE FROM inhouse_session;
DELETE a FROM player_alias a JOIN player p ON p.id = a.player_id WHERE p.real_name IS NULL;
DELETE FROM player WHERE real_name IS NULL;
COMMIT;
SELECT 'ok';
'@
if ($done -ne 'ok') { Write-Host 'DB 정리에 실패했어요. 변경은 되돌려졌고, 방금 만든 백업으로도 복원할 수 있어요.'; exit 1 }

if (Test-Path $images) { Get-ChildItem $images -Force | Remove-Item -Recurse -Force }
Write-Host '3/3 완료: 내전 기록을 모두 지웠어요. 사이트를 새로고침하면 빈 상태로 보여요.'
exit 0
