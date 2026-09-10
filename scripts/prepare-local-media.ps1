param(
    [string]$SourceRoot = ''
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$media = ConvertFrom-Json @'
[
  {"Source":"\u5929\u5149\u4e4d\u96fe\u5c71.jpg","Target":"mountain"},
  {"Source":"\u98ce\u96e8\u6865.jpg","Target":"water"},
  {"Source":"\u4e4c\u4e1c\u82d7\u5be8.jpg","Target":"village"},
  {"Source":"\u6751\u5be8\u5168\u666f\u822a\u62cd.png","Target":"village-aerial"},
  {"Source":"\u91c7\u8336.png","Target":"tea"},
  {"Source":"\u8001\u5e7c\u76f8\u5b9c\u2014\u2014\u4eba\u95f4\u5e78\u798f.jpg","Target":"people"},
  {"Source":"\u6eaa\u6da7\u7a7f\u77f3.jpg","Target":"creek"},
  {"Source":"\u7530\u91ce\u52a1\u519c.jpg","Target":"labor"}
]
'@
$sourceFolder = ConvertFrom-Json '"\u8d35\u5dde\u4e4c\u4e1c\u56fe\u7247"'

if ([string]::IsNullOrWhiteSpace($SourceRoot)) {
    $localSource = Join-Path $projectRoot (Join-Path 'resours' $sourceFolder)
    $mainWorkspaceSource = Join-Path $projectRoot (Join-Path '..\..\resours' $sourceFolder)
    if (Test-Path -LiteralPath $localSource) {
        $SourceRoot = $localSource
    }
    elseif (Test-Path -LiteralPath $mainWorkspaceSource) {
        $SourceRoot = $mainWorkspaceSource
    }
    else {
        throw 'Local photo source was not found. Pass it with -SourceRoot.'
    }
}

$SourceRoot = [IO.Path]::GetFullPath($SourceRoot)
$webRoot = Join-Path $projectRoot 'web\public\images\wudong-local'
$miniRoot = Join-Path $projectRoot 'miniprogram\assets\wudong-local'

Add-Type -AssemblyName System.Drawing

function Get-JpegEncoder {
    return [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
        Where-Object { $_.MimeType -eq 'image/jpeg' } |
        Select-Object -First 1
}

function Export-Jpeg {
    param([string]$Source, [string]$Target, [int]$MaxSide, [long]$Quality)

    if (-not (Test-Path -LiteralPath $Source)) {
        throw "Missing source image: $Source"
    }

    $sourceImage = [Drawing.Image]::FromFile($Source)
    try {
        $scale = [Math]::Min(1.0, $MaxSide / [Math]::Max($sourceImage.Width, $sourceImage.Height))
        $width = [Math]::Max(1, [int][Math]::Round($sourceImage.Width * $scale))
        $height = [Math]::Max(1, [int][Math]::Round($sourceImage.Height * $scale))
        $bitmap = [Drawing.Bitmap]::new($width, $height)
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.DrawImage($sourceImage, 0, 0, $width, $height)
            }
            finally {
                $graphics.Dispose()
            }

            $encoderParameters = [Drawing.Imaging.EncoderParameters]::new(1)
            try {
                $encoderParameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new(
                    [Drawing.Imaging.Encoder]::Quality,
                    $Quality
                )
                $bitmap.Save($Target, (Get-JpegEncoder), $encoderParameters)
            }
            finally {
                $encoderParameters.Dispose()
            }
        }
        finally {
            $bitmap.Dispose()
        }
    }
    finally {
        $sourceImage.Dispose()
    }
}

New-Item -ItemType Directory -Force -Path $webRoot, $miniRoot | Out-Null

foreach ($item in $media) {
    $source = Join-Path $SourceRoot $item.Source
    Export-Jpeg $source (Join-Path $webRoot ($item.Target + '-thumb.jpg')) 960 76
    Export-Jpeg $source (Join-Path $webRoot ($item.Target + '-large.jpg')) 1800 82
    Export-Jpeg $source (Join-Path $miniRoot ($item.Target + '.jpg')) 960 74
}

Write-Host "Generated 16 Web images and 8 Mini Program images from: $SourceRoot" -ForegroundColor Green
