# Builds the images for the Reddit post from the demo-mode screenshots (no real addresses):
#   play-store/reddit/00-collage.png   - 4 phones side by side with the app name (lead image)
#   play-store/reddit/01..08-*.png     - the individual screenshots (gallery)
# Run from the project folder:
#     powershell -ExecutionPolicy Bypass -File .\play-store\tools\make-reddit-images.ps1

Add-Type -AssemblyName System.Drawing
$root = Split-Path (Split-Path $MyInvocation.MyCommand.Path)
$shots = Join-Path $root 'graphics\phone-screenshots'
$out = Join-Path $root 'reddit'
New-Item -ItemType Directory -Force $out | Out-Null

function New-RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

$W = 2400; $H = 1300
$bmp = New-Object Drawing.Bitmap $W, $H, ([Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'; $g.TextRenderingHint = 'AntiAliasGridFit'

$red1 = [Drawing.Color]::FromArgb(255, 0xEF, 0x53, 0x50)
$red2 = [Drawing.Color]::FromArgb(255, 0xB7, 0x1C, 0x1C)
$g.FillRectangle((New-Object Drawing.Drawing2D.LinearGradientBrush ((New-Object Drawing.Point 0, 0), (New-Object Drawing.Point $W, $H), $red1, $red2)), 0, 0, $W, $H)
$soft = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(24, 255, 255, 255))
$g.FillEllipse($soft, 1500, -500, 1300, 1300)
$g.FillEllipse($soft, -300, 1000, 800, 800)

$white = New-Object Drawing.SolidBrush ([Drawing.Color]::White)
$white2 = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(230, 255, 255, 255))
$center = New-Object Drawing.StringFormat; $center.Alignment = 'Center'
$g.DrawString('Troks Tomato Router Remote', (New-Object Drawing.Font 'Segoe UI', 78, ([Drawing.FontStyle]::Bold), ([Drawing.GraphicsUnit]::Pixel)), $white, ($W / 2), 50, $center)
$g.DrawString('An Android app for FreshTomato routers  ·  Material 3  ·  English / Hebrew / Russian / Arabic  ·  Demo mode built in',
    (New-Object Drawing.Font 'Segoe UI', 34, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)), $white2, ($W / 2), 160, $center)

# 4 phones
$pick = '01-overview.png', '02-devices.png', '05-ip-traffic.png', '06-manage.png'
$labels = 'Overview', 'Devices', 'Live traffic per device', 'Manage'
$pw = 470; $ph = [int]($pw * 1920 / 1080) + 28; $gap = 80
$x0 = [int](($W - (4 * $pw + 3 * $gap)) / 2); $y = 270
$labelFont = New-Object Drawing.Font 'Segoe UI', 32, ([Drawing.FontStyle]::Bold), ([Drawing.GraphicsUnit]::Pixel)
for ($i = 0; $i -lt 4; $i++) {
    $x = $x0 + $i * ($pw + $gap)
    $g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(70, 0, 0, 0))), (New-RoundRect ($x + 10) ($y + 16) $pw $ph 54))
    $g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 28, 20, 20))), (New-RoundRect $x $y $pw $ph 54))
    $inner = New-RoundRect ($x + 14) ($y + 14) ($pw - 28) ($ph - 28) 42
    $img = [Drawing.Image]::FromFile((Join-Path $shots $pick[$i]))
    $g.SetClip($inner); $g.DrawImage($img, ($x + 14), ($y + 14), ($pw - 28), ($ph - 28)); $g.ResetClip()
    $img.Dispose()
    $g.DrawString($labels[$i], $labelFont, $white, ($x + $pw / 2), ($y + $ph + 22), $center)
}

$bmp.Save((Join-Path $out '00-collage.png'), [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# gallery copies
Get-ChildItem $shots -Filter *.png | ForEach-Object { Copy-Item $_.FullName (Join-Path $out $_.Name) -Force }
Copy-Item (Join-Path $root 'graphics\feature-graphic.png') (Join-Path $out '09-feature-graphic.png') -Force
Write-Host "Saved Reddit images to $out"
