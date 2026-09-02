# Restore content held back from the 0.1.0 (Sawmill-only) release.
#
# Moves files from unreleased/ back into src/main/resources, preserving the mirrored layout.
# Uses `git mv` when the file is tracked so history keeps following it, plain Move-Item otherwise.
#
#   .\unreleased\restore.ps1                          # restore everything
#   .\unreleased\restore.ps1 -WhatIf                  # preview only
#   .\unreleased\restore.ps1 -Only CookingFire        # restore one group
#   .\unreleased\restore.ps1 -Only CuttingBoard,CookingFire
#
# Groups: CookingFire, CuttingBoard, MountSpike, PerformerSpike, CookingPit, All (default)
#
# After restoring PerformerSpike, also re-add the npcSpike field, the "npcspike" dispatch case,
# and the npcspike(CommandContext) method to command/RpgStationsCommand.java (see git history;
# the old field site carries a comment pointing here).

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string[]] $Only = @('All')
)

$ErrorActionPreference = 'Stop'
$modRoot = Split-Path -Parent $PSScriptRoot
$held    = Join-Path $modRoot 'unreleased'
$target  = Join-Path $modRoot 'src/main/resources'

$groups = @{
    CookingFire    = @(
        'Server/RpgStations/Stations/CookingFire.json',
        'Server/Item/Items/RPG_Station_CookingFire.json',
        'Server/Item/RootInteractions/RPG_Station_CookingFire_Use.json'
    )
    CuttingBoard   = @(
        'Server/RpgStations/Stations/CuttingBoard.json',
        'Server/RpgStations/Actions/PrepFish.json',
        'Server/Item/Items/RPG_Station_CuttingBoard.json',
        'Server/Item/RootInteractions/RPG_Station_CuttingBoard_Use.json',
        'Server/Emote/RPG_Emote_Knife.json'
    )
    MountSpike     = @(
        'Server/RpgStations/Stations/MountSpike.json',
        'Server/Item/Items/RPG_Station_MountSpike.json',
        'Server/Item/RootInteractions/RPG_Station_MountSpike_Use.json'
    )
    PerformerSpike = @(
        'Server/NPC/Roles/RPG_Performer_Spike.json'
    )
    CookingPit     = @(
        'Server/RpgStations/Patterns/CookingPit.json',
        'Server/RpgStations/Stations/CookingPit.json',
        'Server/Item/Items/RPG_Station_CookingPit.json',
        'Server/Item/Items/RPG_Station_Cooking_Pot.json',
        'Server/Item/Items/RPG_Food_Hearty_Stew.json',
        'Server/Item/RootInteractions/RPG_Station_CookingPit_Use.json'
    )
}

if ($Only -contains 'All') { $wanted = $groups.Keys } else { $wanted = $Only }

foreach ($name in $wanted) {
    if (-not $groups.ContainsKey($name)) {
        throw "Unknown group '$name'. Valid groups: $($groups.Keys -join ', '), All"
    }
}

$restored = 0
$missing  = 0

foreach ($name in $wanted) {
    foreach ($rel in $groups[$name]) {
        $src = Join-Path $held $rel
        $dst = Join-Path $target $rel

        if (-not (Test-Path $src)) {
            Write-Warning "already restored or absent: $rel"
            $missing++
            continue
        }
        if (Test-Path $dst) {
            throw "refusing to overwrite an existing file: $dst"
        }

        if ($PSCmdlet.ShouldProcess($rel, 'restore')) {
            $dstDir = Split-Path -Parent $dst
            if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Force -Path $dstDir | Out-Null }

            # Prefer git mv so the file's history keeps following it.
            $tracked = $false
            try {
                & git -C $modRoot ls-files --error-unmatch -- "unreleased/$rel" *> $null
                if ($LASTEXITCODE -eq 0) { $tracked = $true }
            } catch { $tracked = $false }

            if ($tracked) {
                & git -C $modRoot mv -- "unreleased/$rel" "src/main/resources/$rel"
                if ($LASTEXITCODE -ne 0) { throw "git mv failed for $rel" }
            } else {
                Move-Item -LiteralPath $src -Destination $dst
            }
            Write-Host "restored $rel"
            $restored++
        }
    }
}

Write-Host ""
Write-Host "Restored $restored file(s); $missing already absent."
if ($wanted -contains 'PerformerSpike' -and -not $WhatIfPreference) {
    Write-Host "PerformerSpike: also re-wire the npcspike subcommand in command/RpgStationsCommand.java." -ForegroundColor Yellow
}
if ($wanted -contains 'CookingPit' -and -not $WhatIfPreference) {
    Write-Host "CookingPit: also re-point station/HeldCookingPitPatternTest's RESOURCES root back to src/main/resources (and rename it back to a shipped-content gate if you like)." -ForegroundColor Yellow
}
Write-Host "Next: .\build.ps1   (and bump the version if this is going into a release)"
