param(
    [string]$SourceDocument = '',
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$documentName = ConvertFrom-Json '"\u4e4c\u4e1c\u8863\u98df\u4f4f\u884c.docx"'
if ([string]::IsNullOrWhiteSpace($SourceDocument)) {
    foreach ($folder in @('Desktop', 'Downloads')) {
        $candidate = Join-Path (Join-Path $env:USERPROFILE $folder) $documentName
        if (Test-Path -LiteralPath $candidate) { $SourceDocument = $candidate; break }
    }
}
if (-not $SourceDocument -or -not (Test-Path -LiteralPath $SourceDocument)) {
    throw 'Content Word document not found. Supply -SourceDocument with its local path.'
}

$destination = Join-Path $ProjectRoot 'web\public\images\wudong-content'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$jpeg = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object { $_.MimeType -eq 'image/jpeg' } | Select-Object -First 1

function Export-ContentImage {
    param([Drawing.Image]$Image, [string]$OutputPath, [int]$MaxSide, [long]$Quality)
    $scale = [Math]::Min(1.0, $MaxSide / [Math]::Max($Image.Width, $Image.Height))
    $width = [Math]::Max(1, [int][Math]::Round($Image.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Round($Image.Height * $scale))
    $bitmap = [Drawing.Bitmap]::new($width, $height)
    try {
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([Drawing.Color]::White)
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($Image, 0, 0, $width, $height)
        }
        finally { $graphics.Dispose() }
        $parameters = [Drawing.Imaging.EncoderParameters]::new(1)
        try {
            $parameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new([Drawing.Imaging.Encoder]::Quality, $Quality)
            $bitmap.Save($OutputPath, $jpeg, $parameters)
        }
        finally { $parameters.Dispose() }
    }
    finally { $bitmap.Dispose() }
}

$archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($SourceDocument))
$count = 0
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notmatch '^word/media/(image\d+)\.(png|jpe?g)$') { continue }
        $name = $Matches[1]
        $stream = $entry.Open()
        $buffer = [IO.MemoryStream]::new()
        try {
            $stream.CopyTo($buffer)
            $buffer.Position = 0
            $image = [Drawing.Image]::FromStream($buffer)
            try {
                Export-ContentImage $image (Join-Path $destination ($name + '.jpg')) 1400 82
                Export-ContentImage $image (Join-Path $destination ($name + '-thumb.jpg')) 600 76
                $count++
            }
            finally { $image.Dispose() }
        }
        finally { $buffer.Dispose(); $stream.Dispose() }
    }
}
finally { $archive.Dispose() }
Write-Host "Prepared $count content images with thumbnails at $destination" -ForegroundColor Green
