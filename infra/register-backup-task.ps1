# 역할: Windows 작업 스케줄러에 "매일 04:00 backup.ps1 실행"을 등록한다(현재 사용자, 로그인 상태일 때 실행).
# 실행: powershell -NoProfile -ExecutionPolicy Bypass -File infra\register-backup-task.ps1
$ErrorActionPreference = 'Stop'

$backupScript = Join-Path $PSScriptRoot 'backup.ps1'
$taskName = 'LOL-Inhouse-Backup'

$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`""
$trigger = New-ScheduledTaskTrigger -Daily -At '04:00'
# 04:00에 PC가 꺼져 있었다면 켜진 뒤 가능한 때 실행한다
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "등록 완료: '$taskName' (매일 04:00, $backupScript)"
