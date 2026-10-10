# SPDX-License-Identifier: GPL-3.0-or-later
# LAD — Lazy Ass Developer for OTPOTG (Windows / PowerShell 5.1+)
param(
    [switch]$Cli,
    [ValidateSet('Status','Tests','Audit','BuildDebug','BuildRelease','Install','Logcat','Wrapper')]
    [string]$Action = 'Status'
)
$ErrorActionPreference = 'Stop'
$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Project
$LogDir = Join-Path $Project 'lad-logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$LogPath = Join-Path $LogDir ("lad-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
$script:LogBox = $null
function Write-Lad([string]$Message) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message
    Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8
    Write-Host $line
    if ($script:LogBox -and -not $script:LogBox.IsDisposed) {
        $script:LogBox.AppendText($line + [Environment]::NewLine)
        $script:LogBox.SelectionStart = $script:LogBox.TextLength
        $script:LogBox.ScrollToCaret()
        [System.Windows.Forms.Application]::DoEvents()
    }
}
function Find-Java {
    # Gradle 8.9 / AGP 8.7.x: prefer JDK 17, not Android Studio JBR 25.
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in @('C:\Program Files\Eclipse Adoptium','C:\Program Files\Java','C:\Program Files\Microsoft')) {
        if (Test-Path $base) {
            $candidates += @(Get-ChildItem -Path $base -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^(jdk-17|microsoft-jdk-17)' } | Select-Object -ExpandProperty FullName)
        }
    }
    $candidates += @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr")
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $java = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path $java)) { continue }
        # java -version writes to stderr by design; do not let PowerShell 5.1 treat it as a terminating error.
        $version = [string]::Join(' ', [string[]]@(& cmd.exe /d /c ('"' + $java + '" -version 2>&1')))
        if ($LASTEXITCODE -eq 0 -and $version -match 'version "17[."]') { return $candidate }
    }
    throw 'Java 17 JDK not found. Install Eclipse Temurin 17 (x64). Gradle 8.9 cannot run on Java 25.'
}
function Find-Sdk {
    foreach ($path in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($path -and (Test-Path $path)) { return $path }
    }
    return $null
}
function Find-Tool([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}
function Get-Gradle {
    $wrapper = Join-Path $Project 'gradlew.bat'
    if ((Test-Path $wrapper) -and (Test-Path (Join-Path $Project 'gradle\wrapper\gradle-wrapper.jar'))) { return $wrapper }
    return (Find-Tool 'gradle.bat')
}
function Invoke-External([string]$Exe,[string[]]$Arguments) {
    if (-not $Exe) { throw 'Required executable not found. See environment status.' }
    Write-Lad ("RUN: {0} {1}" -f $Exe, ($Arguments -join ' '))
    # Native tools may write routine output to stderr; PowerShell 5.1 turns that into ErrorRecord objects.
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Exe @Arguments 2>&1 | ForEach-Object { Write-Lad ([string]$_) }
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    if ($code -ne 0) { throw "Command exited with code $code; see LAD log above" }
}
function Get-Adb {
    $sdk = Find-Sdk
    if ($sdk) {
        $exe = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path $exe) { return $exe }
    }
    return (Find-Tool 'adb.exe')
}
function Invoke-Status {
    Write-Lad 'OTPOTG — LAD environment status'
    Write-Lad "Project: $Project"
    $jdk = Find-Java
    Write-Lad "Java 17: $jdk"
    Write-Lad "Android SDK: $(if(Find-Sdk){Find-Sdk}else{'NOT FOUND'})"
    Write-Lad "Gradle: $(if(Get-Gradle){Get-Gradle}else{'NOT FOUND — use Android Studio to create wrapper or install Gradle 8.9'})"
    Write-Lad "ADB: $(if(Get-Adb){Get-Adb}else{'NOT FOUND'})"
    Write-Lad "Git: $(if(Find-Tool 'git.exe'){Find-Tool 'git.exe'}else{'NOT FOUND'})"
    Write-Lad "App source: $(Test-Path (Join-Path $Project 'app\src\main\AndroidManifest.xml'))"
    $adb = Get-Adb
    if ($adb) { Invoke-External $adb @('devices','-l') }
}
function Set-BuildEnvironment {
    $java = Find-Java
    if ($java) { $env:JAVA_HOME = $java; $env:PATH = (Join-Path $java 'bin') + ';' + $env:PATH }
    $sdk = Find-Sdk
    if ($sdk) { $env:ANDROID_HOME = $sdk; $env:ANDROID_SDK_ROOT = $sdk }
}
function Invoke-GradleTask([string]$Task) {
    Set-BuildEnvironment
    $gradle = Get-Gradle
    if (-not $gradle) { throw 'No working Gradle wrapper or gradle.bat. Open project in Android Studio and run Gradle wrapper task with a locally installed Gradle 8.9, or install Gradle 8.9.' }
    Invoke-External $gradle @('--no-daemon', $Task)
}
function Invoke-Tests {
    Set-BuildEnvironment
    $javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { Find-Tool 'javac.exe' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { Find-Tool 'java.exe' }
    if (-not $javac -or -not $java) { throw 'Java JDK 17 is required for core tests.' }
    $dest = Join-Path $Project 'core-tests\classes'
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Invoke-External $javac @('-d',$dest,(Join-Path $Project 'app\src\main\java\us\norec\otpotg\OtpCore.java'),(Join-Path $Project 'core-tests\CoreSmokeTest.java'))
    Invoke-External $java @('-cp',$dest,'CoreSmokeTest')
}
function Invoke-Audit {
    Write-Lad 'F-Droid readiness preflight (source checks only; NOT an F-Droid acceptance guarantee)'
    $issues = 0
    foreach ($f in @('LICENSE','app\build.gradle','app\src\main\AndroidManifest.xml','fastlane\metadata\android\en-US\title.txt','gradle\wrapper\gradle-wrapper.properties')) {
        if (Test-Path (Join-Path $Project $f)) { Write-Lad "PASS: $f" }
        else { Write-Lad "FAIL: missing $f"; $issues++ }
    }
    $manifest = Get-Content (Join-Path $Project 'app\src\main\AndroidManifest.xml') -Raw
    if ($manifest -match '<uses-permission\b') { Write-Lad 'REVIEW: manifest declares permissions; inspect merged manifest too'; $issues++ }
    else { Write-Lad 'PASS: no declared permissions in source manifest' }
    if ($manifest -match 'android:allowBackup="false"') { Write-Lad 'PASS: app backup disabled in source manifest' }
    else { Write-Lad 'REVIEW: backup not explicitly disabled'; $issues++ }
    if (Test-Path (Join-Path $Project 'gradle\wrapper\gradle-wrapper.jar')) { Write-Lad 'PASS: wrapper JAR present' }
    else { Write-Lad 'TODO: wrapper JAR missing (current starter project limitation)'; $issues++ }
    if (Test-Path (Join-Path $Project 'gradlew.bat')) { Write-Lad 'PASS: gradlew.bat present' }
    else { Write-Lad 'TODO: gradlew.bat missing'; $issues++ }
    $sources = Get-ChildItem (Join-Path $Project 'app\src') -Recurse -Include *.java,*.kt -File
    foreach ($file in $sources) {
        $body = Get-Content $file.FullName -Raw
        if ($body -notmatch 'SPDX-License-Identifier: GPL-3.0-or-later') { Write-Lad "REVIEW: missing SPDX in $($file.Name)"; $issues++ }
        if ($body -match 'https?://|HttpURLConnection|OkHttp|Retrofit|Socket\(') { Write-Lad "REVIEW: potential network/string reference in $($file.Name) (manual inspection required)" }
    }
    if (Test-Path (Join-Path $Project '.git')) { Write-Lad 'PASS: Git repository initialized' }
    else { Write-Lad 'TODO: initialize Git repository and publish source'; $issues++ }
    Write-Lad "Preflight complete: $issues issue(s)/TODO(s). Inspect merged manifest and dependencies before release."
}
function Invoke-Install {
    $adb = Get-Adb
    if (-not $adb) { throw 'ADB not found; install Android SDK Platform Tools.' }
    $apk = Join-Path $Project 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw 'Debug APK not found. Build Debug first.' }
    Invoke-External $adb @('devices','-l')
    Invoke-External $adb @('install','-r',$apk)
    Write-Lad 'Install complete. If you get INSTALL_FAILED_USER_RESTRICTED, authorize installation on the phone.'
}
function Invoke-Logcat {
    $adb = Get-Adb
    if (-not $adb) { throw 'ADB not found.' }
    Write-Lad 'Collecting recent OTPOTG-related Android logs (not a continuous stream).'
    $output = & $adb logcat -d -t 2000 2>&1
    $output | Select-String -Pattern 'otpotg|AndroidRuntime|FATAL EXCEPTION' | ForEach-Object { Write-Lad ([string]$_) }
    Write-Lad 'Log collection complete. Logs may contain sensitive data: inspect before sharing.'
}
function Invoke-Wrapper {
    Set-BuildEnvironment
    $gradle = Find-Tool 'gradle.bat'
    if (-not $gradle) { throw 'Creating a wrapper requires locally installed Gradle 8.9. Android Studio does not necessarily install the gradle command.' }
    Invoke-External $gradle @('wrapper','--gradle-version','8.9','--distribution-type','bin')
    Write-Lad 'Wrapper created. Inspect and commit gradlew, gradlew.bat, wrapper JAR, and properties.'
}
function Invoke-Action([string]$Name) {
    try {
        switch ($Name) {
            Status { Invoke-Status }
            Tests { Invoke-Tests }
            Audit { Invoke-Audit }
            BuildDebug { Invoke-GradleTask ':app:assembleDebug' }
            BuildRelease { Invoke-GradleTask ':app:assembleRelease'; Write-Lad 'Release build is normally UNSIGNED. Do not commit signing keys.' }
            Install { Invoke-Install }
            Logcat { Invoke-Logcat }
            Wrapper { Invoke-Wrapper }
        }
        Write-Lad "DONE: $Name"
    } catch {
        Write-Lad "FAILED: $Name — $($_.Exception.Message)"
        if (-not $Cli) { [System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'LAD error') | Out-Null }
    }
}
if ($Cli) { Invoke-Action $Action; exit }
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
$form = New-Object System.Windows.Forms.Form
$form.Text = 'LAD — Lazy Ass Developer | OTPOTG'
$form.Size = New-Object System.Drawing.Size(940,660)
$form.StartPosition = 'CenterScreen'
$form.BackColor = [System.Drawing.Color]::FromArgb(28,28,28)
$form.ForeColor = [System.Drawing.Color]::White
$form.MinimumSize = New-Object System.Drawing.Size(760,520)
$header = New-Object System.Windows.Forms.Label
$header.Text = 'OTPOTG  /  LAZY ASS DEVELOPER'
$header.Font = New-Object System.Drawing.Font('Segoe UI',16,[System.Drawing.FontStyle]::Bold)
$header.ForeColor = [System.Drawing.Color]::White
$header.Location = New-Object System.Drawing.Point(18,15)
$header.Size = New-Object System.Drawing.Size(860,36)
$form.Controls.Add($header)
$subtitle = New-Object System.Windows.Forms.Label
$subtitle.Text = 'Build locally. Test locally. Ship FOSS. No phone networking required.'
$subtitle.ForeColor = [System.Drawing.Color]::Silver
$subtitle.Location = New-Object System.Drawing.Point(20,55)
$subtitle.Size = New-Object System.Drawing.Size(850,24)
$form.Controls.Add($subtitle)
$buttons = @(
    @('Environment','Status'), @('Core Tests','Tests'), @('F-Droid Audit','Audit'),
    @('Build Debug','BuildDebug'), @('Build Release','BuildRelease'), @('Install Debug','Install'),
    @('Read Logs','Logcat'), @('Setup Wrapper','Wrapper')
)
for ($i=0;$i -lt $buttons.Count;$i++) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $buttons[$i][0]
    $b.Tag = $buttons[$i][1]
    $b.Location = New-Object System.Drawing.Point((20 + ($i % 4)*225),(92 + [math]::Floor($i / 4)*52))
    $b.Size = New-Object System.Drawing.Size(210,42)
    $b.BackColor = [System.Drawing.Color]::FromArgb(67,67,67)
    $b.ForeColor = [System.Drawing.Color]::White
    $b.FlatStyle = 'Flat'
    $b.Add_Click({ Invoke-Action ([string]$this.Tag) })
    $form.Controls.Add($b)
}
$script:LogBox = New-Object System.Windows.Forms.TextBox
$script:LogBox.Multiline = $true
$script:LogBox.ReadOnly = $true
$script:LogBox.ScrollBars = 'Both'
$script:LogBox.WordWrap = $false
$script:LogBox.BackColor = [System.Drawing.Color]::FromArgb(14,14,14)
$script:LogBox.ForeColor = [System.Drawing.Color]::FromArgb(190,225,190)
$script:LogBox.Font = New-Object System.Drawing.Font('Consolas',9)
$script:LogBox.Location = New-Object System.Drawing.Point(20,210)
$script:LogBox.Size = New-Object System.Drawing.Size(885,390)
$script:LogBox.Anchor = 'Top,Bottom,Left,Right'
$form.Controls.Add($script:LogBox)
$form.Add_Shown({ Invoke-Action 'Status' })
[void]$form.ShowDialog()
