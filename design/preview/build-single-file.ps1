# Build sumidoku-android-preview.html: one self-contained file that embeds
# every screen (NN-*.html) as an iframe srcdoc with mock.css inlined.
# Run after editing any screen or assets/mock.css:
#   pwsh design/preview/build-single-file.ps1
$dir = $PSScriptRoot
$css = Get-Content "$dir\assets\mock.css" -Raw
$index = Get-Content "$dir\index.html" -Raw
$index = $index -replace '<link rel="stylesheet" href="assets/mock\.css">', ("<style>" + $css + "</style>")

$map = @{}
Get-ChildItem $dir -Filter '*.html' | Where-Object { $_.Name -match '^\d\d-' } | Sort-Object Name | ForEach-Object {
  $h = Get-Content $_.FullName -Raw
  $h = $h -replace '<link rel="stylesheet" href="assets/mock\.css">', ("<style>" + $css + "</style>")
  # Escape for a double-quoted srcdoc attribute: & first, then ".
  $map[$_.Name] = ($h -replace '&','&amp;' -replace '"','&quot;')
}

$evaluator = {
  param($m)
  $doc = $map[$m.Groups[2].Value]
  if ($null -eq $doc) { return $m.Value }
  return ('<iframe' + $m.Groups[1].Value + ' srcdoc="' + $doc + '"' + $m.Groups[3].Value + '>')
}
$out = [regex]::Replace($index, '<iframe([^>]*?) src="(\d\d-[^"]+\.html)"([^>]*?)>', $evaluator)
$outPath = "$dir\sumidoku-android-preview.html"
Set-Content $outPath $out -Encoding UTF8 -NoNewline
$count = ([regex]::Matches($out, 'srcdoc=')).Count
"embedded $count screens -> $outPath ($((Get-Item $outPath).Length) bytes)"
