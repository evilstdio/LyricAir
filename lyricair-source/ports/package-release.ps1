$ErrorActionPreference = 'Stop'
$portDirectory = $PSScriptRoot
$workspaceDirectory = Split-Path -Parent $portDirectory
$targets = Get-Content -LiteralPath (Join-Path $portDirectory 'versions.json') -Raw | ConvertFrom-Json
$propertiesText = Get-Content -LiteralPath (Join-Path $portDirectory 'gradle.properties') -Raw
$releaseVersion = [regex]::Match($propertiesText, '(?m)^mod_version=(.+)$').Groups[1].Value.Trim()
if (!$releaseVersion) { throw 'Missing mod_version.' }
$distributionDirectory = Join-Path $workspaceDirectory "dist/lyricair-$releaseVersion-all-versions"
New-Item -ItemType Directory -Path $distributionDirectory -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$records = @()
$hashLines = @()
foreach ($target in $targets) {
    $filename = "lyricair-$releaseVersion-mc$($target.minecraft).jar"
    $buildDirectory = Join-Path $portDirectory "build/$($target.minecraft)"
    $jarFile = Join-Path $buildDirectory "libs/$filename"
    if (!(Test-Path -LiteralPath $jarFile)) { throw "Missing release JAR: $filename" }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarFile)
    try {
        $reader = [IO.StreamReader]::new($archive.GetEntry('fabric.mod.json').Open())
        try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($metadata.version -ne $releaseVersion -or $metadata.depends.minecraft -ne $target.minecraft -or $metadata.depends.java -ne ">=$($target.java)" -or $metadata.depends.fabricloader -ne ">=$($target.loader)" -or $metadata.environment -ne 'client') { throw "Wrong target metadata in $filename" }
        if (@($archive.Entries | Where-Object { $_.FullName -like '*visualcheck*' -or $_.FullName -like '*lyricair.check.mixins*' }).Count) { throw "Test fixture was packaged in $filename" }
        foreach ($required in @('lyricair.mixins.json','licenses/Inter-OFL.txt','licenses/Lato-OFL.txt','assets/lyricair/fonts/inter-regular.otf')) { if (!$archive.GetEntry($required)) { throw "Missing $required in $filename" } }
        $classStream = $archive.GetEntry('dev/lyricair/LyricAirClient.class').Open()
        try {
            $header = [byte[]]::new(8)
            if ($classStream.Read($header, 0, 8) -ne 8 -or (($header[6] -shl 8) + $header[7]) -ne ($target.java + 44)) { throw "Wrong Java bytecode target in $filename" }
        } finally { $classStream.Dispose() }
    } finally { $archive.Dispose() }
    $testCount = 0
    $testReports = @(Get-ChildItem -LiteralPath (Join-Path $buildDirectory 'test-results/test') -Filter 'TEST-*.xml')
    foreach ($report in $testReports) {
        $suite = ([xml](Get-Content -LiteralPath $report.FullName -Raw)).testsuite
        if ([int]$suite.failures -or [int]$suite.errors -or [int]$suite.skipped) { throw "Tests failed or were skipped for $($target.minecraft)" }
        $testCount += [int]$suite.tests
    }
    if ($testCount -lt 19) { throw "Incomplete tests for $($target.minecraft)" }
    $gameLog = Join-Path $portDirectory "build/logs/$($target.minecraft).log"
    if (!(Select-String -LiteralPath $gameLog -Pattern 'VISUAL CHECK PASSED' -Quiet)) { throw "Missing successful game checks for $($target.minecraft)" }
    $screenshotDirectory = Join-Path $portDirectory ".run/$($target.minecraft)/screenshots"
    foreach ($name in @('world','display','world-settings','color','overlay-settings','overlay-editor','both-follow','overlay','search','expanded-sidebar')) {
        if (!(Test-Path -LiteralPath (Join-Path $screenshotDirectory "lyricair-$name.png"))) { throw "Missing $name capture for $($target.minecraft)" }
    }
    $hash = (Get-FileHash -LiteralPath $jarFile -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath $jarFile -Destination (Join-Path $distributionDirectory $filename) -Force
    $hashLines += "$hash  $filename"
    $records += [pscustomobject]@{ minecraft=$target.minecraft; java=$target.java; fabricLoader=$target.loader; fabricApi=$target.fabricApi; filename=$filename; sha256=$hash; tests=$testCount; gameChecks='passed'; screenshots=10 }
}
$records | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $distributionDirectory 'compatibility.json') -Encoding utf8
$records | Export-Csv -LiteralPath (Join-Path $distributionDirectory 'compatibility.csv') -NoTypeInformation -Encoding utf8
$hashLines | Set-Content -LiteralPath (Join-Path $distributionDirectory 'SHA256SUMS.txt') -Encoding ascii
Copy-Item -LiteralPath (Join-Path $workspaceDirectory 'LICENSE') -Destination $distributionDirectory -Force
Copy-Item -LiteralPath (Join-Path $workspaceDirectory 'CHANGELOG.md') -Destination $distributionDirectory -Force
$readmeText = @"
# LyricAir $releaseVersion

This bundle contains $($targets.Count) separate Fabric builds for all released Minecraft versions after 1.21.1, through 26.3. Install only the JAR for your exact game version.

## Install

1. Minecraft 1.21.2-1.21.11 requires Java 21 or newer; 26.x requires Java 25 or newer.
2. Install Fabric Loader 0.19.5 or newer and Fabric API for your Minecraft version. Exact tested dependencies are listed in compatibility.csv.
3. Put the matching lyricair-$releaseVersion-mc<version>.jar in that profile's mods folder. Remove the previous LyricAir JAR, then restart Minecraft.
4. Open Spotify desktop on the same Windows PC, play a song, and join a world.

Press O for settings, K to toggle lyrics, or use /lyricair. Use /lyricair demo to preview the mod without music. The menu includes world/overlay/both modes, text color, follow-player, Float/Orbit motion, particles, lyric timing, overlay size/opacity, artwork and upcoming lines. Move overlay opens its position editor.

Automatic Spotify detection needs Windows 10 1809 or later, or Windows 11, with Windows PowerShell 5.1. No Spotify API key or login is needed. Spotify on another device and browser playback are unsupported. Timed lyrics come from LRCLIB; availability depends on the song. Lyrics are line-synced. Add your own UTF-8 LRC files in config/lyricair/lyrics/Artist - Track title.lrc, then reconnect Spotify from settings. Settings are saved in config/lyricair.json.

## Verification

Each of the $($targets.Count) builds passed $($records[0].tests) shared regression tests and isolated game checks for the UI, display modes, follow, preset/hex color, overlay dragging, search, sliders and keyboard input. Ten screenshots per target were captured. The test fixture is excluded from every release JAR. SHA256SUMS.txt and compatibility.json identify each exact artifact.

The original Minecraft 1.21.1 release remains LyricAir 0.2.1 and is distributed separately. These files target Fabric. Preview screenshots and the source build scripts are available in the workspace.

MIT applies to the mod's source. Bundled fonts retain their SIL Open Font License notices inside each JAR. LyricAir is independent of Spotify and Mojang; lyrics remain the property of their respective rights holders.
"@
$readmeText | Set-Content -LiteralPath (Join-Path $distributionDirectory 'README.md') -Encoding utf8
$zipFile = Join-Path $workspaceDirectory "dist/lyricair-$releaseVersion-all-versions.zip"
Compress-Archive -Path (Join-Path $distributionDirectory '*') -DestinationPath $zipFile -Force
Write-Output "Packaged $($targets.Count) verified builds: $zipFile"
Get-FileHash -LiteralPath $zipFile -Algorithm SHA256 | Select-Object Hash,Path
