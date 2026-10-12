param([int]$ParentPid = 0, [switch]$Once)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Send-Message($Message) {
    [Console]::WriteLine(($Message | ConvertTo-Json -Compress -Depth 4))
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
    Add-Type -AssemblyName System.Drawing
    $script:AsTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
        $_.GetGenericArguments().Count -eq 1 -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    $script:AsStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1
    } | Select-Object -First 1

    function Await-WinRt($Operation, [Type]$ResultType) {
        $task = $script:AsTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
        if (-not $task.Wait(8000)) { throw 'Windows media request timed out.' }
        return $task.Result
    }

    function Read-Cover($Thumbnail) {
        if ($null -eq $Thumbnail) { return '' }
        $stream = $null; $input = $null; $image = $null; $small = $null; $graphics = $null; $output = $null
        try {
            $stream = Await-WinRt ($Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
            $input = $script:AsStream.Invoke($null, @($stream))
            if ($input.Length -eq 0 -or $input.Length -gt 8388608) { return '' }
            $image = [System.Drawing.Image]::FromStream($input)
            $small = [System.Drawing.Bitmap]::new(128, 128)
            $graphics = [System.Drawing.Graphics]::FromImage($small)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($image, 0, 0, 128, 128)
            $output = [System.IO.MemoryStream]::new()
            $small.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
            return [Convert]::ToBase64String($output.ToArray())
        } catch { return '' }
        finally {
            foreach ($resource in @($graphics, $small, $image, $input, $output, $stream)) {
                if ($null -ne $resource -and $resource -is [System.IDisposable]) {
                    try { [System.IDisposable].GetMethod('Dispose').Invoke($resource, @()) | Out-Null } catch {}
                }
            }
        }
    }

    $manager = Await-WinRt ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    $lastCoverKey = ''
    do {
        if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { break }
        try {
            $sessions = @($manager.GetSessions() | Where-Object { $_.SourceAppUserModelId -match '(?i)spotify' })
            $session = $sessions | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' } | Select-Object -First 1
            if (-not $session) { $session = $sessions | Select-Object -First 1 }
            if (-not $session) {
                $lastCoverKey = ''
                Send-Message @{ type = 'waiting'; message = 'Open Spotify desktop and play a song.' }
            } else {
                $media = Await-WinRt ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $timeline = $session.GetTimelineProperties()
                $playback = $session.GetPlaybackInfo()
                $playing = $playback.PlaybackStatus.ToString() -eq 'Playing'
                $rate = 1.0
                if ($null -ne $playback.PlaybackRate -and [double]$playback.PlaybackRate -gt 0) { $rate = [double]$playback.PlaybackRate }
                $now = [DateTimeOffset]::UtcNow
                $position = $timeline.Position.TotalMilliseconds
                if ($playing) {
                    $age = [Math]::Max(0, ($now - [DateTimeOffset]$timeline.LastUpdatedTime).TotalMilliseconds)
                    if ($age -lt 3600000) { $position += $age * $rate }
                }
                $duration = [Math]::Max(0, ($timeline.EndTime - $timeline.StartTime).TotalMilliseconds)
                $position = [Math]::Max(0, $position - $timeline.StartTime.TotalMilliseconds)
                if ($duration -gt 0) { $position = [Math]::Min($duration, $position) }
                if ([string]::IsNullOrWhiteSpace($media.Title)) {
                    Send-Message @{ type = 'waiting'; message = 'Waiting for Spotify track information.' }
                } else {
                    $sample = @{
                        type = 'playback'; title = $media.Title; artist = $media.Artist; album = $media.AlbumTitle
                        durationMs = [long]$duration; positionMs = [long]$position; playing = $playing
                        playbackRate = $rate; sampledAtMs = $now.ToUnixTimeMilliseconds()
                    }
                    $coverKey = $media.Title + "`n" + $media.Artist + "`n" + $media.AlbumTitle
                    if ($coverKey -ne $lastCoverKey) {
                        $sample.coverPng = Read-Cover $media.Thumbnail
                        $lastCoverKey = $coverKey
                    }
                    Send-Message $sample
                }
            }
        } catch {
            Send-Message @{ type = 'error'; message = 'Could not read Spotify media session. Try restarting Spotify.' }
        }
        if (-not $Once) { Start-Sleep -Milliseconds 700 }
    } while (-not $Once)
} catch {
    Send-Message @{ type = 'error'; message = 'Windows media bridge unavailable: ' + $_.Exception.Message }
    exit 1
}

