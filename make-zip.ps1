$root = 'C:\Users\Rahul Kumar Mahto\ShopSphere'
$zip = Join-Path $root 'shopsphere.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
$t = Join-Path $root 'zipdir'
if (Test-Path $t) { Remove-Item $t -Recurse -Force }
New-Item -ItemType Directory -Path $t | Out-Null
Copy-Item (Join-Path $root 'backend') -Destination $t -Recurse
Copy-Item (Join-Path $root 'database') -Destination $t -Recurse
Copy-Item (Join-Path $root 'frontend') -Destination $t -Recurse
Remove-Item (Join-Path $t 'backend\shopsphere\target') -Recurse -Force
Compress-Archive -Path (Join-Path $t '*') -DestinationPath $zip -Force
Remove-Item $t -Recurse -Force
Get-Item $zip | Select-Object Length, LastWriteTime
