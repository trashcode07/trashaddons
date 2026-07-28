Add-Type -AssemblyName System.Runtime.WindowsRuntime
$am=[System.WindowsRuntimeSystemExtensions].GetMethods()|
?{$_.Name-eq'AsTask'-and$_.IsGenericMethodDefinition-and$_.GetParameters().Length-eq1}|select -First 1
function Await($o,$t){$m=$am.MakeGenericMethod($t);$m.Invoke($null,@($o)).GetAwaiter().GetResult()}
$asStream=[System.IO.WindowsRuntimeStreamExtensions].GetMethods()|?{$_.Name-eq'AsStream'-and$_.GetParameters().Length-eq1}|select -First 1
try{
$c=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
$m=Await($c::RequestAsync())([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$results=@()
foreach($s in $m.GetSessions()){
  try{$p=Await($s.TryGetMediaPropertiesAsync())([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $tl=$s.GetTimelineProperties();$pb=$s.GetPlaybackInfo()
    $playing=($pb.PlaybackStatus-eq[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing)
    $tB64=''
    if($p.Thumbnail){
      try{
        $st=Await($p.Thumbnail.OpenReadAsync())([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $ns=$asStream.Invoke($null,@($st))
        $ms=New-Object System.IO.MemoryStream
        $ns.CopyTo($ms)
        $tB64=[Convert]::ToBase64String($ms.ToArray())
      }catch{}
    }
    $line=($p.Title+':::'+$p.Artist+':::'+[long]$tl.Position.TotalMilliseconds+':::'+
      [long]$tl.EndTime.TotalMilliseconds+':::'+$playing+':::'+$s.SourceAppUserModelId+':::'+$tB64)
    if($playing){$results=@($line)+$results}else{$results+=$line}
  }catch{}
}
if($results.Count-gt0){$results|%{Write-Output $_}}else{Write-Output 'NONE'}
}catch{Write-Output('ERROR:'+$_)}
