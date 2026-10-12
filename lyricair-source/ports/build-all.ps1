param([string[]]$MinecraftVersions = @(), [switch]$GameChecks)
$ErrorActionPreference = 'Stop'
$portDirectory = $PSScriptRoot
$workspaceDirectory = Split-Path -Parent $portDirectory
$targets = Get-Content -LiteralPath (Join-Path $portDirectory 'versions.json') -Raw | ConvertFrom-Json
if ($MinecraftVersions.Count) { $targets = @($targets | Where-Object { $_.minecraft -in $MinecraftVersions }); if ($targets.Count -ne $MinecraftVersions.Count) { throw 'Every requested version must exist in versions.json.' } }
$bundledJavaDirectory = Join-Path $workspaceDirectory '.tools/java'
$buildJdk = if (Test-Path -LiteralPath $bundledJavaDirectory) { Get-ChildItem -LiteralPath $bundledJavaDirectory -Directory -Filter 'jdk-25*' | Select-Object -First 1 }
if ($buildJdk) { $env:JAVA_HOME = $buildJdk.FullName }
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25 before building.' }
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $workspaceDirectory '.gradle-cache' }
$logDirectory = Join-Path $portDirectory 'build/logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
foreach ($target in $targets) {
    $versionArgument = "-Pmc=$($target.minecraft)"
    Write-Output "Building Minecraft $($target.minecraft) (Java $($target.java))"
    $tasks = @('build', '--console=plain')
    if ($GameChecks) { $tasks = @('-PvisualCheck', 'build', 'runVisualCheck', '--console=plain') }
    $logName = if ($GameChecks) { "$($target.minecraft).log" } else { "$($target.minecraft)-build.log" }
    $logFile = Join-Path $logDirectory $logName
    & (Join-Path $portDirectory 'gradlew.bat') -p $portDirectory $versionArgument @tasks *> $logFile
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $logFile -Tail 70; throw "Minecraft $($target.minecraft) failed. See $logFile" }
    if ($GameChecks -and !(Select-String -LiteralPath $logFile -Pattern 'VISUAL CHECK PASSED' -Quiet)) { throw "Minecraft $($target.minecraft) did not complete the game checks." }
    Write-Output "Passed Minecraft $($target.minecraft)"
}
