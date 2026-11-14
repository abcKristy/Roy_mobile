# Run on a disposable Android emulator with the local backend on port 18080.
# The emulator must be unlocked and Roy must be signed out.
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Serial = 'emulator-5554',
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$package = 'com.example.roy'
$login = 'С возвращением'
$register = 'Создайте профиль'
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

function Test-ImeVisible {
    [bool](Invoke-Device shell dumpsys input_method | Select-String '^\s*m(InputShown|IsInputViewShown)=true\b')
}

$apkPath = (Resolve-Path -LiteralPath $Apk).Path
Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
try {
    $null = Invoke-Device reverse tcp:18080 tcp:18080
    $null = Invoke-Device install -r $apkPath
    $null = Invoke-Device shell am force-stop $package
    $null = Invoke-Device shell am start -W -n "$package/.MainActivity"
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
            if (Test-ImeVisible) { throw 'IME remained visible after dismissal' }
        }
        $null = Invoke-Device shell input keyevent KEYCODE_BACK
        $null = Wait-Label $login
        Start-Sleep -Milliseconds 400
        $null = Wait-Label $login
        "PASS: $tapCount registration tap(s), one system Back, login remains visible"
    }
} finally {
    $null = Invoke-Device shell rm -f $dump
}
