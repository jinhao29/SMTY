# ============================================================================
# Fetch Latest Failed GitHub Actions Log (Windows PowerShell)
# ============================================================================
# Purpose: Auto fetch latest failed workflow run log and print to terminal
# Usage: .\scripts\fetch_latest_run_log.ps1
#
# Workflow:
#   1. Auto detect owner/repo from git remote
#   2. Find latest failed run via gh run list
#   3. Print log to stdout via gh run view --log
#
# Prerequisite: Run .\setup_cli.ps1 first to install and auth gh
# ============================================================================

param(
    [int]$Lines = 1000,
    [int]$Wait = 0
)

$ErrorActionPreference = "Stop"

# Force UTF-8 output encoding so Chinese commit messages don't mangle into GBK garbled text
# (Windows PowerShell 5.x defaults to console codepage, often GBK on zh-CN systems)
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fetch Latest Failed Actions Log" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 0. Pre-check: gh installed and authenticated
# ---------------------------------------------------------------------------
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] GitHub CLI (gh) not detected" -ForegroundColor Red
    Write-Host "Please run first: .\setup_cli.ps1" -ForegroundColor Yellow
    exit 1
}

$authStatus = gh auth status 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] gh not authenticated" -ForegroundColor Red
    Write-Host "Please run first: .\setup_cli.ps1" -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------------------
# 1. Get owner/repo from git remote
# ---------------------------------------------------------------------------
$remoteUrl = git remote get-url origin 2>$null
if (-not $remoteUrl) {
    Write-Host "[ERROR] Current dir is not a git repo or no origin remote" -ForegroundColor Red
    exit 1
}

# Parse two formats:
#   https://github.com/OWNER/REPO.git
#   git@github.com:OWNER/REPO.git
$repo = $null
if ($remoteUrl -match "github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?$") {
    $owner = $Matches[1]
    $repo = $Matches[2]
    $repoFullName = "$owner/$repo"
    Write-Host "[REPO] $repoFullName" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Cannot parse owner/repo from remote URL: $remoteUrl" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# 2. Optional: wait for in-progress build to complete
# ---------------------------------------------------------------------------
if ($Wait -gt 0) {
    Write-Host "`n[WAIT] Sleeping $Wait seconds before query..." -ForegroundColor Yellow
    Start-Sleep -Seconds $Wait
}

# ---------------------------------------------------------------------------
# 3. Query latest failed run
# ---------------------------------------------------------------------------
# Use gh's built-in --jq to extract fields directly as TSV (tab-separated values).
# Why not ConvertFrom-Json? gh outputs multi-line commit messages in displayTitle
# as literal newlines embedded in JSON string values, which is invalid JSON.
# PowerShell's ConvertFrom-Json then fails with "expected ':' or '}'".
# Using --jq '.[0] | [.f1,.f2,...] | @tsv' sidesteps JSON parsing in PowerShell
# entirely; gh internally handles the escaping and emits clean TSV.
Write-Host "`n[QUERY] Latest failed workflow run..." -ForegroundColor Cyan
$runTsv = gh run list --repo $repoFullName --limit 1 --status failure `
    --json databaseId,name,displayTitle,headBranch,event,conclusion,createdAt,updatedAt,headSha `
    --jq '.[0] | [.databaseId, .name, .displayTitle, .headBranch, .event, .createdAt, .updatedAt, .headSha] | @tsv' 2>&1 | Out-String

if ($LASTEXITCODE -ne 0 -or -not $runTsv.Trim()) {
    Write-Host "[INFO] No failed workflow run found" -ForegroundColor Yellow
    Write-Host "`nLatest 5 workflow runs:" -ForegroundColor Gray
    gh run list --repo $repoFullName --limit 5
    exit 0
}

# Parse TSV: split on tab. Field order matches the --jq expression above.
$parts = $runTsv.Trim() -split "`t"
if ($parts.Count -lt 8) {
    Write-Host "[ERROR] Unexpected gh output format:" -ForegroundColor Red
    Write-Host $runTsv
    exit 1
}

$runId      = $parts[0]
$runName    = $parts[1]
$runTitle   = $parts[2]
$runBranch  = $parts[3]
$runEvent   = $parts[4]
$runCreated = $parts[5]
$runUpdated = $parts[6]
$runSha     = $parts[7]

Write-Host "[FOUND] Run #$runId" -ForegroundColor Green
Write-Host "  Name:    $runName" -ForegroundColor Gray
Write-Host "  Title:   $runTitle" -ForegroundColor Gray
Write-Host "  Branch:  $runBranch" -ForegroundColor Gray
Write-Host "  Event:   $runEvent" -ForegroundColor Gray
Write-Host "  Created: $runCreated" -ForegroundColor Gray
Write-Host "  Updated: $runUpdated" -ForegroundColor Gray
if ($runSha.Length -ge 7) {
    Write-Host "  Commit:  $($runSha.Substring(0,7))" -ForegroundColor Gray
}

$runUrl = "https://github.com/$repoFullName/actions/runs/$runId"
Write-Host "  URL:     $runUrl" -ForegroundColor Gray

# ---------------------------------------------------------------------------
# 4. Download full log to UTF-8 file (no BOM) for AI/teammate to read
# ---------------------------------------------------------------------------
# Why save to file instead of printing directly?
# - Terminal output can be truncated by scrollback buffer limits
# - Windows console codepage may mangle Chinese commit messages
# - File gives AI/cursor a stable, complete, re-readable artifact
# - UTF-8 without BOM is the most portable encoding for tools
$logDir = Join-Path $PSScriptRoot "..\logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$timestamp = (Get-Date -Format "yyyyMMdd-HHmmss")
$logPath = Join-Path $logDir "failure-run$runId-$timestamp.log"

Write-Host "`n[FETCH] Downloading full log..." -ForegroundColor Cyan
$rawLog = gh run view $runId --repo $repoFullName --log 2>&1 | Out-String

# Write as UTF-8 without BOM using .NET API
# (PowerShell 5.x Out-File -Encoding utf8 writes BOM, which breaks some tools)
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($logPath, $rawLog, $utf8NoBom)

$fileSize = (Get-Item $logPath).Length
$lineCount = (Get-Content $logPath -Encoding UTF8 | Measure-Object -Line).Lines
Write-Host "[SAVED] Full log written to file" -ForegroundColor Green
Write-Host "  Path:  $logPath" -ForegroundColor Gray
Write-Host "  Size:  $fileSize bytes" -ForegroundColor Gray
Write-Host "  Lines: $lineCount" -ForegroundColor Gray

# ---------------------------------------------------------------------------
# 5. Print last N lines as preview (read from file to ensure consistency)
# ---------------------------------------------------------------------------
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Log Preview (last $Lines lines)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Get-Content $logPath -Encoding UTF8 -Tail $Lines

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  End of Preview" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Full log file: $logPath" -ForegroundColor Green
Write-Host "Browser view:  $runUrl" -ForegroundColor Gray
Write-Host ""
Write-Host "Next step: paste the file path to AI, AI will read it directly." -ForegroundColor Yellow

# Also print the absolute path on its own line for easy copy
Write-Host ""
Write-Host $logPath
