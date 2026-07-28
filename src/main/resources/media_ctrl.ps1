param(
    [string]$Action = 'TryTogglePlayPauseAsync',
    [string]$TargetTitle = '',
    [string]$TargetSource = ''
)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$am=[System.WindowsRuntimeSystemExtensions].GetMethods()|
?{$_.Name-eq'AsTask'-and$_.IsGenericMethodDefinition-and$_.GetParameters().Length-eq1}|select -First 1
function Await($o,$t){$m=$am.MakeGenericMethod($t);$m.Invoke($null,@($o)).GetAwaiter().GetResult()}
try{
  $c=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
  $m=Await($c::RequestAsync())([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
  $target=$null
  foreach($s in $m.GetSessions()){
    try{
      $p=Await($s.TryGetMediaPropertiesAsync())([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
      if(($TargetSource -ne '' -and $s.SourceAppUserModelId -like ('*'+$TargetSource+'*')) -or ($TargetTitle -ne '' -and $p.Title -eq $TargetTitle)){
        $target=$s; break
      }
    }catch{}
  }
  if(-not $target){$target=$m.GetCurrentSession()}
  if(-not $target -and $m.GetSessions().Count-gt0){$target=$m.GetSessions()[0]}
  if($target){Await($target.$Action())([bool])|Out-Null}
}catch{}
