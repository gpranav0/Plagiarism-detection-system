param(
    [switch]$Tests,
    [switch]$ExecutionLockHeld
)

$ErrorActionPreference = 'Stop'
$ExecutionMutex = $null
$ExecutionLockAcquired = $false
if (-not $ExecutionLockHeld) {
    $ExecutionMutex = [System.Threading.Mutex]::new(
        $false, 'Local\AdvancedPlagiarismDetectionSystemExecution')
    try {
        $ExecutionLockAcquired = $ExecutionMutex.WaitOne(0)
    } catch [System.Threading.AbandonedMutexException] {
        $ExecutionLockAcquired = $true
    }
    if (-not $ExecutionLockAcquired) {
        Write-Host 'Another project build or run is active; waiting for it to finish...'
        try {
            $ExecutionLockAcquired = $ExecutionMutex.WaitOne(90000)
        } catch [System.Threading.AbandonedMutexException] {
            $ExecutionLockAcquired = $true
        }
    }
    if (-not $ExecutionLockAcquired) {
        $ExecutionMutex.Dispose()
        throw 'Timed out waiting for another project build or run to finish.'
    }
}

try {
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutputDirectory = Join-Path $ProjectRoot 'out'

if (Test-Path -LiteralPath $OutputDirectory) {
    $ResolvedProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
    $ResolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
    if (-not $ResolvedOutput.StartsWith($ResolvedProjectRoot + [System.IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to remove output outside the project root: $ResolvedOutput"
    }
    Remove-Item -LiteralPath $OutputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null

$SourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
if ($Tests) {
    $SourceFiles += @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
}

if ($SourceFiles.Count -eq 0) {
    throw 'No Java source files were found.'
}

# The desktop layer is deliberately isolated so the algorithmic engine stays
# headless and independently testable.
$ForbiddenUiImports = @(
    Select-String -Path $SourceFiles -Pattern '^import\s+(javax\.swing|java\.awt)(\.|;)' | Where-Object {
        $_.Path -notmatch '[\\/]ui[\\/]'
    }
)
if ($ForbiddenUiImports.Count -gt 0) {
    $Locations = ($ForbiddenUiImports | ForEach-Object { "$($_.Path):$($_.LineNumber)" }) -join ', '
    throw "Swing/AWT imports are allowed only in the ui package: $Locations"
}

# Bound compiler memory/worker use so builds remain stable on student laptops and lab VMs.
& javac -J-Xms32m -J-Xmx256m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=2 --release 17 -encoding UTF-8 -d $OutputDirectory $SourceFiles
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Write-Host "Compiled $($SourceFiles.Count) source files into $OutputDirectory"
} finally {
    if ($ExecutionLockAcquired) {
        $ExecutionMutex.ReleaseMutex()
    }
    if ($null -ne $ExecutionMutex) {
        $ExecutionMutex.Dispose()
    }
}
