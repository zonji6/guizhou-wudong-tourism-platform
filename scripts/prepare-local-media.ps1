$media = @(
    @{ Source = '天光乍雾山.jpg'; Target = 'mountain' },
    @{ Source = '风雨桥.jpg'; Target = 'water' },
    @{ Source = '乌东苗寨.jpg'; Target = 'village' },
    @{ Source = '村寨全景航拍.png'; Target = 'village-aerial' },
    @{ Source = '采茶.png'; Target = 'tea' },
    @{ Source = '老幼相宜——人间幸福.jpg'; Target = 'people' },
    @{ Source = '溪涧穿石.jpg'; Target = 'creek' },
    @{ Source = '田野务农.jpg'; Target = 'labor' }
)

$sourceRoot = Join-Path $PSScriptRoot '..\resours\贵州乌东图片'
$webRoot = Join-Path $PSScriptRoot '..\web\public\images\wudong-local'
$miniRoot = Join-Path $PSScriptRoot '..\miniprogram\assets\wudong-local'

Add-Type -AssemblyName System.Drawing

function Get-JpegEncoder {
    return [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
        Where-Object { $_.MimeType -eq 'image/jpeg' } |
        Select-Object -First 1
}

function Export-Jpeg {
    param([string]$Source, [string]$Target, [int]$MaxSide, [long]$Quality)

    if (-not (Test-Path -LiteralPath $Source)) { throw "未找到素材：$Source" }

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
            } finally { $graphics.Dispose() }
            $encoderParameters = [Drawing.Imaging.EncoderParameters]::new(1)
            try {
                $encoderParameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new([Drawing.Imaging.Encoder]::Quality, $Quality)
                $bitmap.Save($Target, (Get-JpegEncoder), $encoderParameters)
            } finally { $encoderParameters.Dispose() }
        } finally { $bitmap.Dispose() }
    } finally { $sourceImage.Dispose() }
}

New-Item -ItemType Directory -Force -Path $webRoot, $miniRoot | Out-Null

foreach ($item in $media) {
    $source = Join-Path $sourceRoot $item.Source
    Export-Jpeg $source (Join-Path $webRoot ($item.Target + '-thumb.jpg')) 960 76
    Export-Jpeg $source (Join-Path $webRoot ($item.Target + '-large.jpg')) 1800 82
    Export-Jpeg $source (Join-Path $miniRoot ($item.Target + '.jpg')) 960 74
}
