# Run on a disposable Android emulator with a local backend.
# The emulator must be unlocked and Roy must be signed out.
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Serial = 'emulator-5554',
    [string]$Adb = 'adb',
    [ValidateRange(1, 65535)][int]$BackendPort = 18080
)
$ErrorActionPreference = 'Stop'
$package = 'com.example.roy'
# Submit labels identify the mode even when the IME scrolls the heading out of view.
$login = 'Войти'
$register = 'Создать профиль'
$profile = 'Профиль'
$switch = 'Нет аккаунта? Зарегистрироваться'
$dump = '/data/local/tmp/roy-mode-back.xml'

function Invoke-Device {
    & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed with exit $LASTEXITCODE" }
}

function Wait-Label([string]$Label) {
    for ($attempt = 0; $attempt -lt 6; $attempt++) {
        try {
            $null = Invoke-Device shell rm -f $dump
            $null = Invoke-Device shell uiautomator dump $dump
            $xml = [xml]((Invoke-Device shell cat $dump) -join [Environment]::NewLine)
            $node = $xml.SelectNodes("//node[@package='$package']") |
                Where-Object { $_.text -eq $Label -or $_.'content-desc' -eq $Label } |
                Select-Object -First 1
            if ($node) { return $node }
        } catch {
            if ($attempt -eq 5) { throw }
        }
        Start-Sleep -Milliseconds 300
    }
    throw "App label absent: $Label"
}

function Invoke-NodeTap($Node) {
    if ($Node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Invalid UI node bounds' }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    $null = Invoke-Device shell input tap $x $y
}

function ConvertFrom-ImeVisibilityDump([string]$Dump) {
    # InputMethodService may retain mIsInputViewShown after its window is hidden.
    # Use the manager's current input state; fail explicitly on an unknown dump format.
    $states = @([regex]::Matches($Dump, '(?m)^\s*mInputShown=(true|false)\b') |
        ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    if ($states.Count -ne 1) { throw 'Missing or ambiguous manager mInputShown state' }
    return $states[0] -eq 'true'
}

function Test-ImeVisible {
    ConvertFrom-ImeVisibilityDump ((Invoke-Device shell dumpsys input_method) -join "`n")
}

function Wait-ImeHidden {
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        if (!(Test-ImeVisible)) { return }
        Start-Sleep -Milliseconds 200
    }
    throw 'IME remained visible after dismissal'
}

$apkPath = (Resolve-Path -LiteralPath $Apk).Path
Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
try {
    $null = Invoke-Device reverse tcp:18080 "tcp:$BackendPort"
    $null = Invoke-Device install -r $apkPath
    $null = Invoke-Device shell am force-stop $package
    $null = Invoke-Device shell am start -W -n "$package/.MainActivity"
    # A signed-out cold launch opens the guest map. Profile owns the login entry.
    Invoke-NodeTap (Wait-Label $profile)
    $null = Wait-Label $login

    foreach ($tapCount in 1,2,2,2) {
        $button = Wait-Label $switch
        if ($button.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Invalid registration button bounds' }
        $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        $taps = (@("input tap $x $y") * $tapCount) -join '; '
        $null = Invoke-Device shell $taps
        $null = Wait-Label $register
        if (Test-ImeVisible) {
            # If the second tap hit an editor, the IME consumes its own Back first.
            $null = Invoke-Device shell input keyevent KEYCODE_BACK
            $null = Wait-Label $register
            Wait-ImeHidden
        }
        $null = Invoke-Device shell input keyevent KEYCODE_BACK
        $null = Wait-Label $login
        Start-Sleep -Milliseconds 400
        $null = Wait-Label $login
        "PASS: $tapCount registration tap(s), one system Back, login remains visible"
    }
} finally {
    try { $null = Invoke-Device shell rm -f $dump }
    catch { Write-Warning "Could not remove temporary UI dump: $_" }
}
