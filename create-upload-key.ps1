# Creates the Google Play upload key for Troks Tomato Router Remote (com.troks.tomatorouter.remote)
# and writes keystore.properties so release builds are signed with it.
#
# Run it yourself in a terminal from the project folder:
#     powershell -ExecutionPolicy Bypass -File .\create-upload-key.ps1
#
# - Key: %USERPROFILE%\.android-keys\troks-tomato-router-upload.jks (alias "upload")
# - keystore.properties is in .gitignore - never commit it.
# - BACK UP the .jks file and its password (e.g. in a password manager).
#   With Play App Signing a lost upload key can be reset through Play support, but it takes days.

#     add -Gui to type the password in a Windows dialog instead of the terminal
param([switch]$Gui)

$ErrorActionPreference = 'Stop'
$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
if (-not (Test-Path $keytool)) { throw "keytool not found at $keytool" }

$keyDir  = Join-Path $env:USERPROFILE '.android-keys'
$keyFile = Join-Path $keyDir 'troks-tomato-router-upload.jks'
$alias   = 'upload'
$props   = Join-Path $PSScriptRoot 'keystore.properties'

if (Test-Path $keyFile) { throw "A key already exists at $keyFile - not overwriting it." }
New-Item -ItemType Directory -Force $keyDir | Out-Null

if ($Gui) {
    # A small Windows dialog with two hidden password fields (for terminals that can't take hidden input)
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $f = New-Object Windows.Forms.Form
    $f.Text = 'Troks Tomato Router Remote - upload key password'
    $f.Size = New-Object Drawing.Size 440, 250
    $f.StartPosition = 'CenterScreen'
    $f.TopMost = $true
    $f.FormBorderStyle = 'FixedDialog'
    $f.MaximizeBox = $false
    $l1 = New-Object Windows.Forms.Label; $l1.Text = 'Choose a password for the upload key (min 6 chars):'; $l1.SetBounds(16, 16, 400, 20)
    $t1 = New-Object Windows.Forms.TextBox; $t1.UseSystemPasswordChar = $true; $t1.SetBounds(16, 40, 390, 24)
    $l2 = New-Object Windows.Forms.Label; $l2.Text = 'Repeat the password:'; $l2.SetBounds(16, 76, 400, 20)
    $t2 = New-Object Windows.Forms.TextBox; $t2.UseSystemPasswordChar = $true; $t2.SetBounds(16, 100, 390, 24)
    $ok = New-Object Windows.Forms.Button; $ok.Text = 'Create key'; $ok.SetBounds(220, 150, 90, 30); $ok.DialogResult = 'OK'
    $cancel = New-Object Windows.Forms.Button; $cancel.Text = 'Cancel'; $cancel.SetBounds(316, 150, 90, 30); $cancel.DialogResult = 'Cancel'
    $f.Controls.AddRange(@($l1, $t1, $l2, $t2, $ok, $cancel))
    $f.AcceptButton = $ok; $f.CancelButton = $cancel
    $f.Add_Shown({ $f.Activate(); $t1.Focus() })
    if ($f.ShowDialog() -ne 'OK') { throw 'Cancelled.' }
    $plain1 = $t1.Text; $plain2 = $t2.Text
    $f.Dispose()
} else {
    $p1 = Read-Host 'Choose a password for the upload key (min 6 chars)' -AsSecureString
    $p2 = Read-Host 'Repeat the password' -AsSecureString
    $plain1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p1))
    $plain2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p2))
}
if ($plain1 -ne $plain2) { throw 'Passwords do not match.' }
if ($plain1.Length -lt 6) { throw 'Password must be at least 6 characters.' }

$env:TROKS_KEY_PASS = $plain1
try {
    & $keytool -genkeypair -keystore $keyFile -storetype JKS -alias $alias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Lior Trok, OU=Trok's, O=Troks, L=Yavne, ST=IL, C=IL" `
        -storepass:env TROKS_KEY_PASS -keypass:env TROKS_KEY_PASS
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE)" }
} finally {
    Remove-Item Env:\TROKS_KEY_PASS -ErrorAction SilentlyContinue
}

$storePath = $keyFile.Replace('\', '/')
@"
storeFile=$storePath
storePassword=$plain1
keyAlias=$alias
keyPassword=$plain1
"@ | Set-Content -Path $props -Encoding ascii

Write-Host ''
Write-Host "Done. Upload key: $keyFile"
Write-Host "Signing config:  $props"
Write-Host 'Now back up the .jks file and the password.'
