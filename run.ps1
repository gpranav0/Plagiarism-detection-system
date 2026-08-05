param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ApplicationArguments
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ExecutionMutex = [System.Threading.Mutex]::new(
    $false, 'Local\AdvancedPlagiarismDetectionSystemExecution')
$ExecutionLockAcquired = $false
try {
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
        throw 'Timed out waiting for another project build or run to finish.'
    }

    & (Join-Path $ProjectRoot 'build.ps1') -ExecutionLockHeld
    Push-Location $ProjectRoot
    try {
        & java -Xms32m -Xmx256m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 `
            -cp (Join-Path $ProjectRoot 'out') `
            edu.academic.integrity.app.Main @ApplicationArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Application exited with code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($ExecutionLockAcquired) {
        $ExecutionMutex.ReleaseMutex()
    }
    $ExecutionMutex.Dispose()
}
