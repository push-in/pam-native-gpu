<?php
declare(strict_types=1);
namespace Pam\Native\Gpu;
use Closure;use Pam\Native\Element;use Pam\Native\Internal\Wire;use Pam\Native\Renderable;use Pam\Native\UI\CustomView;
final class GpuView implements Renderable
{
 private ?Closure$handler=null;private function __construct(private readonly GpuProgram$program,private readonly GpuRenderMode$mode,private readonly int$revision){}public static function make(GpuProgram$program,GpuRenderMode$mode=GpuRenderMode::Continuous,int$revision=1):self{return new self($program,$mode,max(0,$revision));}
 /** @param Closure(GpuEventKind,string):void $handler */public function onEvent(Closure$handler):self{$copy=clone$this;$copy->handler=$handler;return$copy;}
 public function toElement():Element{$view=CustomView::make('gpu.surface',['glsl'=>$this->program->glsl,'metal'=>$this->program->metal,'uniforms'=>$this->program->uniformsJson(),'renderMode'=>$this->mode->value,'revision'=>$this->revision]);$handler=$this->handler;return$handler===null?$view:$view->onNativeEvent(static function(string$payload)use($handler):void{$v=Wire::decodeMap($payload);$kind=GpuEventKind::tryFrom((int)($v['event']??3))??GpuEventKind::Error;$handler($kind,(string)($v['message']??''));});}
}
