# Generates the Google Play graphics from code (no design tool needed):
#   play-store/graphics/icon-512.png             - app icon, 512x512 (same geometry as the launcher icon)
#   play-store/graphics/feature-graphic.png      - feature graphic, 1024x500
# Run from the project folder:
#     powershell -ExecutionPolicy Bypass -File .\play-store\tools\make-graphics.ps1

Add-Type -AssemblyName System.Drawing
$root = Split-Path (Split-Path $MyInvocation.MyCommand.Path)
$out = Join-Path $root 'graphics'
New-Item -ItemType Directory -Force $out | Out-Null

$red1 = [Drawing.Color]::FromArgb(255, 0xEF, 0x53, 0x50)
$red2 = [Drawing.Color]::FromArgb(255, 0xC6, 0x28, 0x28)
$leaf = [Drawing.Color]::FromArgb(255, 0x9C, 0xCC, 0x65)

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

# Draws the router glyph. Geometry is in the 108x108 adaptive-icon space (see ic_launcher_foreground.xml);
# (ox, oy) is where icon point (18,18) lands and s is pixels per icon unit, so the visible 72x72 area is drawn.
function Draw-Glyph($g, [float]$ox, [float]$oy, [float]$s) {
    function P([float]$v) { return $v }
    $X = { param($v) $ox + ($v - 18) * $s }
    $Y = { param($v) $oy + ($v - 18) * $s }
    $white = New-Object Drawing.SolidBrush ([Drawing.Color]::White)

    # body with LED holes (even-odd)
    $body = New-RoundRect (& $X 30) (& $Y 58) (48 * $s) (18 * $s) (7 * $s)
    foreach ($cx in 40, 48, 56) { $body.AddEllipse((& $X ($cx - 2.6)), (& $Y (67 - 2.6)), 5.2 * $s, 5.2 * $s) }
    $body.FillMode = [Drawing.Drawing2D.FillMode]::Alternate
    $g.FillPath($white, $body)

    # antenna
    $ant = New-RoundRect (& $X 62) (& $Y 42) (4 * $s) (17 * $s) (2 * $s)
    $g.FillPath($white, $ant)

    # wifi waves
    $pen = New-Object Drawing.Pen ([Drawing.Color]::White), (3.5 * $s)
    $pen.StartCap = [Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    foreach ($r in 7, 13) { $g.DrawArc($pen, (& $X (64 - $r)), (& $Y (40 - $r)), 2 * $r * $s, 2 * $r * $s, 225, 90) }

    # leaf
    $lp = New-Object Drawing.Drawing2D.GraphicsPath
    $lp.AddBezier((& $X 33), (& $Y 57), (& $X 32), (& $Y 50), (& $X 37), (& $Y 46), (& $X 44), (& $Y 47))
    $lp.AddBezier((& $X 44), (& $Y 47), (& $X 44), (& $Y 53), (& $X 39), (& $Y 57), (& $X 33), (& $Y 57))
    $g.FillPath((New-Object Drawing.SolidBrush $leaf), $lp)
}

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object Drawing.Bitmap $w, $h
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    return @($bmp, $g)
}

# ---------- 512x512 icon (full square; Play rounds the corners itself) ----------
$bmp, $g = New-Canvas 512 512
$bg = New-Object Drawing.Drawing2D.LinearGradientBrush ((New-Object Drawing.Point 0, 0), (New-Object Drawing.Point 512, 512), $red1, $red2)
$g.FillRectangle($bg, 0, 0, 512, 512)
Draw-Glyph $g 0 0 (512 / 72)
$bmp.Save((Join-Path $out 'icon-512.png'), [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# ---------- 1024x500 feature graphic ----------
$bmp, $g = New-Canvas 1024 500
$bg = New-Object Drawing.Drawing2D.LinearGradientBrush ((New-Object Drawing.Point 0, 0), (New-Object Drawing.Point 1024, 500), $red1, $red2)
$g.FillRectangle($bg, 0, 0, 1024, 500)
# soft decorative circles
$soft = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(28, 255, 255, 255))
$g.FillEllipse($soft, 560, -160, 520, 520)
$g.FillEllipse($soft, -120, 330, 300, 300)

# icon tile
$tile = New-RoundRect 64 70 132 132 34
$g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(60, 0, 0, 0))), (New-RoundRect 68 76 132 132 34))
$g.FillPath((New-Object Drawing.Drawing2D.LinearGradientBrush ((New-Object Drawing.Point 64, 70), (New-Object Drawing.Point 196, 202), $red1, $red2)), $tile)
$g.DrawPath((New-Object Drawing.Pen ([Drawing.Color]::FromArgb(90, 255, 255, 255)), 2), $tile)
Draw-Glyph $g 64 70 (132 / 72)

$white = New-Object Drawing.SolidBrush ([Drawing.Color]::White)
$white2 = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(225, 255, 255, 255))
$g.DrawString('Troks Tomato', (New-Object Drawing.Font 'Segoe UI', 40, ([Drawing.FontStyle]::Bold), ([Drawing.GraphicsUnit]::Pixel)), $white, 60, 236)
$g.DrawString('Router Remote', (New-Object Drawing.Font 'Segoe UI', 40, ([Drawing.FontStyle]::Bold), ([Drawing.GraphicsUnit]::Pixel)), $white, 60, 284)
$sub = New-Object Drawing.Font 'Segoe UI', 21, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$g.DrawString('Manage your FreshTomato router', $sub, $white2, 62, 350)
$g.DrawString('Devices  -  WiFi  -  Live traffic  -  Ports', $sub, $white2, 62, 380)

# phone with a screenshot
$shot = [Drawing.Image]::FromFile((Join-Path $root 'screenshots\en\05-ip-traffic.png'))
$pw = 250; $ph = 444; $px = 690; $py = 28
$g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(70, 0, 0, 0))), (New-RoundRect ($px + 8) ($py + 12) $pw $ph 34))
$frame = New-RoundRect $px $py $pw $ph 34
$g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 30, 22, 22))), $frame)
$inner = New-RoundRect ($px + 9) ($py + 9) ($pw - 18) ($ph - 18) 26
$g.SetClip($inner)
$g.DrawImage($shot, ($px + 9), ($py + 9), ($pw - 18), ($ph - 18))
$g.ResetClip()
$shot.Dispose()

# Play wants the feature graphic and screenshots as 24-bit PNG (no alpha channel)
function Save-Rgb24($img, [string]$path) {
    $rgb = New-Object Drawing.Bitmap $img.Width, $img.Height, ([Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $gg = [Drawing.Graphics]::FromImage($rgb)
    $gg.DrawImage($img, 0, 0, $img.Width, $img.Height)
    $gg.Dispose()
    $rgb.Save($path, [Drawing.Imaging.ImageFormat]::Png)
    $rgb.Dispose()
}

Save-Rgb24 $bmp (Join-Path $out 'feature-graphic.png')
$g.Dispose(); $bmp.Dispose()

# phone screenshots -> graphics/phone-screenshots (24-bit copies of screenshots/en)
$shotsOut = Join-Path $out 'phone-screenshots'
New-Item -ItemType Directory -Force $shotsOut | Out-Null
Get-ChildItem (Join-Path $root 'screenshots\en') -Filter *.png | ForEach-Object {
    $img = [Drawing.Image]::FromFile($_.FullName)
    Save-Rgb24 $img (Join-Path $shotsOut $_.Name)
    $img.Dispose()
}
Write-Host "Saved graphics to $out"
