# ============================================================================
# GitHub CLI Installation and Authentication Script (Windows PowerShell)
# ============================================================================
# Purpose: Check / install / authenticate GitHub CLI (gh)
# Usage: Run .\setup_cli.ps1 in project root
# ============================================================================

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  GitHub CLI Install and Auth Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# Step 1: Check if gh is installed
# ---------------------------------------------------------------------------
function Test-GhInstalled {
    $cmd = Get-Command gh -ErrorAction SilentlyContinue
    if ($cmd) {
        $ver = (gh --version | Select-Object -First 1)
        Write-Host "[OK] Detected $ver" -ForegroundColor Green
        return $true
    }
    return $false
}

if ((Test-GhInstalled) -and -not $Force) {
    Write-Host "GitHub CLI already installed, skip installation." -ForegroundColor Yellow
} else {
    Write-Host "`n[INSTALL] Starting GitHub CLI install..." -ForegroundColor Cyan

    $installed = $false

    # Option A: winget (Windows 10 1809+ / Windows 11 built-in)
    if (-not $installed) {
        $winget = Get-Command winget -ErrorAction SilentlyContinue
        if ($winget) {
            Write-Host "  Installing via winget..." -ForegroundColor Gray
            try {
                winget install --id GitHub.cli -e --source winget --accept-package-agreements --accept-source-agreements
                $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
                $installed = Test-GhInstalled
            } catch {
                Write-Host "  winget install failed: $_" -ForegroundColor Yellow
            }
        } else {
            Write-Host "  winget not available, try direct msi download..." -ForegroundColor Gray
        }
    }

    # Option B: Direct msi download
    if (-not $installed) {
        Write-Host "  Downloading latest msi from GitHub official..." -ForegroundColor Gray
        $apiUrl = "https://api.github.com/repos/cli/cli/releases/latest"
        try {
            $release = Invoke-RestMethod -Uri $apiUrl -UseBasicParsing
            $asset = $release.assets | Where-Object { $_.name -like "*windows_amd64.msi" } | Select-Object -First 1
            if (-not $asset) {
                $asset = $release.assets | Where-Object { $_.name -like "*.msi" } | Select-Object -First 1
            }
            if ($asset) {
                $msiPath = Join-Path $env:TEMP $asset.name
                Write-Host "  Downloading $($asset.name) ..." -ForegroundColor Gray
                Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $msiPath -UseBasicParsing
                Write-Host "  Installing (UAC prompt will appear, please allow)..." -ForegroundColor Gray
                Start-Process msiexec.exe -ArgumentList "/i `"$msiPath`" /quiet /norestart" -Wait -Verb RunAs
                $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
                Start-Sleep -Seconds 2
                $installed = Test-GhInstalled
                Remove-Item $msiPath -ErrorAction SilentlyContinue
            } else {
                Write-Host "  No suitable msi package found." -ForegroundColor Red
            }
        } catch {
            Write-Host "  Download failed: $_" -ForegroundColor Red
        }
    }

    if (-not $installed) {
        Write-Host "`n[FAIL] Auto install unsuccessful, please install manually:" -ForegroundColor Red
        Write-Host "  Option 1: winget install --id GitHub.cli" -ForegroundColor Yellow
        Write-Host "  Option 2: Download msi from https://cli.github.com/" -ForegroundColor Yellow
        Write-Host "  After install, reopen PowerShell and rerun this script." -ForegroundColor Yellow
        exit 1
    }

    Write-Host "[OK] GitHub CLI installed successfully" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# Step 2: Check auth status
# ---------------------------------------------------------------------------
Write-Host "`n[AUTH] Checking gh login status..." -ForegroundColor Cyan
$authOk = $false
try {
    $status = gh auth status 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host $status
        $authOk = $true
    }
} catch {}

if ($authOk) {
    Write-Host "[OK] Already logged in" -ForegroundColor Green
} else {
    Write-Host "Not logged in or expired, starting interactive login..." -ForegroundColor Yellow
    Write-Host "`nSelect login method (Option 1 recommended):" -ForegroundColor Cyan
    Write-Host "  1. Browser login (recommended, simplest)" -ForegroundColor White
    Write-Host "  2. Paste Personal Access Token" -ForegroundColor White
    $choice = Read-Host "Enter 1 or 2"

    if ($choice -eq "2") {
        Write-Host "`nCreate classic token at https://github.com/settings/tokens" -ForegroundColor Yellow
        Write-Host "  Required scopes: repo, read:org, workflow" -ForegroundColor Yellow
        $token = Read-Host "Paste token" -AsSecureString
        $plain = [System.Net.NetworkCredential]::new("", $token).Password
        $token | Out-Null
        echo $plain | gh auth login --with-token
    } else {
        gh auth login --hostname github.com --git-protocol https --web
    }

    try {
        gh auth status
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] Login successful" -ForegroundColor Green
        } else {
            Write-Host "[FAIL] Login unsuccessful, please retry" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "[FAIL] Auth verify exception: $_" -ForegroundColor Red
        exit 1
    }
}

# ---------------------------------------------------------------------------
# Step 3: Verify usability
# ---------------------------------------------------------------------------
Write-Host "`n[VERIFY] Testing gh usability..." -ForegroundColor Cyan
$repoInfo = gh repo view --json nameWithOwner 2>&1 | Out-String
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] gh ready, current repo: $repoInfo" -ForegroundColor Green
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  Install and auth complete!" -ForegroundColor Green
    Write-Host "  Now use: .\scripts\fetch_latest_run_log.ps1" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
} else {
    Write-Host "[WARN] gh logged in but current dir is not a git repo" -ForegroundColor Yellow
    Write-Host "  Please run this script inside a git repo" -ForegroundColor Yellow
}
