param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
foreach($name in @('python','java','git','winget','adb')){
 $c=Get-Command $name -ErrorAction SilentlyContinue
 Write-Status "$name : $(if($c){$c.Source}else{'MISSING'})"
}
$sdk = Resolve-AndroidSdk $Root
Write-Status "Android SDK: $(if($sdk){$sdk}else{'NOT CONFIGURED'})"
Write-Status 'Inventory only; missing tools are handled by install-dependencies.'
