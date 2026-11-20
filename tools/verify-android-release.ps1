# Static APK audit only; does not establish secure-store behavior or absence of secrets.
param(
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$BuildTools,
    [Parameter(Mandatory)][string]$Candidate,
    [string]$OutputJson
)
$ErrorActionPreference = 'Stop'
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$aapt = Join-Path $BuildTools 'aapt.exe'
$apksigner = Join-Path $BuildTools 'apksigner.bat'
foreach ($tool in @($aapt, $apksigner)) {
    if (!(Test-Path -LiteralPath $tool)) { throw "Required SDK tool missing: $tool" }
}
$badging = (& $aapt dump badging $apkPath 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'aapt badging failed' }
$manifest = (& $aapt dump xmltree $apkPath AndroidManifest.xml 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'aapt manifest inspection failed' }
# Nonzero is expected for unsigned artifacts. Keep only a classification, not certificate data.
$signature = (& $apksigner verify --verbose $apkPath 2>&1 | ForEach-Object { "$_" }) -join "`n"
$signatureExit = $LASTEXITCODE
$signatureStatus = if ($signatureExit -eq 0) { 'VERIFIED' }
    elseif ($signature -match 'Missing META-INF/MANIFEST.MF|No signatures|No JAR signatures') { 'UNSIGNED' }
    else { 'UNVERIFIED_OR_TOOL_ERROR' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
$markers = [ordered]@{
    DebugPushFixtureReceiver = $false
    ComposePreviewActivity = $false
    ComposeViewAdapter = $false
    LocalDebugOrigin = $false
}
$markerText = @{
    DebugPushFixtureReceiver = 'Lcom/example/roy/DebugPushFixtureReceiver;'
    ComposePreviewActivity = 'Landroidx/compose/ui/tooling/PreviewActivity;'
    ComposeViewAdapter = 'Landroidx/compose/ui/tooling/ComposeViewAdapter;'
    LocalDebugOrigin = 'http://127.0.0.1:18080'
}
try {
    foreach ($entry in $archive.Entries | Where-Object FullName -Match '^classes\d*\.dex$') {
        $stream = $entry.Open()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::GetEncoding(28591))
        try { $dex = $reader.ReadToEnd() } finally { $reader.Dispose() }
        foreach ($name in @($markers.Keys)) {
            $markers[$name] = $markers[$name] -or $dex.Contains($markerText[$name])
        }
    }
    $debugNetworkResource = @($archive.Entries | Where-Object FullName -Match 'roy_debug_network').Count -gt 0
} finally { $archive.Dispose() }

$size = (Get-Item -LiteralPath $apkPath).Length
$result = [ordered]@{
    candidate = $Candidate
    apk = $apkPath
    sha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    bytes = $size
    decimalMB = $size / 1000000
    sizeAtMost60MB = $size -le 60000000
    package = (($badging -split "`n") | Where-Object { $_ -match '^package:' })
    sdk = @(($badging -split "`n") | Where-Object { $_ -match '^(sdkVersion|targetSdkVersion):' })
    signature = $signatureStatus
    signatureVerifierExit = $signatureExit
    debuggable = [bool]($manifest -match 'android:debuggable[^\r\n]*0xffffffff')
    testOnly = [bool]($manifest -match 'android:testOnly[^\r\n]*0xffffffff')
    explicitlyAllowsCleartext = [bool]($manifest -match 'android:usesCleartextTraffic[^\r\n]*0xffffffff')
    networkSecurityConfigPresent = [bool]($manifest -match 'android:networkSecurityConfig')
    debugNetworkResourcePresent = $debugNetworkResource
    debugFixtureDeclared = [bool]($manifest -match 'DebugPushFixtureReceiver')
    previewActivityDeclared = [bool]($manifest -match 'androidx.compose.ui.tooling.PreviewActivity')
    dexMarkers = $markers
    limitations = @(
        'DEX markers establish string presence, not reachability or runtime behavior.',
        'Default cleartext policy depends on target SDK when no explicit setting exists.',
        'No general secret absence claim; secure storage and session revocation require device checks.',
        'UNSIGNED does not verify install/update compatibility; no key is generated or used.'
    )
}
$json = $result | ConvertTo-Json -Depth 5
if ($OutputJson) { Set-Content -LiteralPath $OutputJson -Value $json -Encoding utf8 }
$json
