<?php
declare(strict_types=1);
namespace Pam\Native\Gpu;
use InvalidArgumentException;use JsonException;
final readonly class GpuProgram
{
 /** @param array<string,float|int> $uniforms */public function __construct(public string$glsl,public string$metal,public array$uniforms=[]){foreach([$glsl,$metal]as$source)if($source===''||strlen($source)>65_536||str_contains($source,"\0"))throw new InvalidArgumentException('GPU shader sources must contain between 1 and 65,536 bytes.');if(count($uniforms)>64)throw new InvalidArgumentException('GPU programs support at most 64 scalar uniforms.');foreach($uniforms as$name=>$value)if(preg_match('/^[A-Za-z_][A-Za-z0-9_]{0,63}$/',$name)!==1||!is_finite((float)$value))throw new InvalidArgumentException('GPU uniforms require safe names and finite scalar values.');}
 /** @throws JsonException */public function uniformsJson():string{return json_encode($this->uniforms,JSON_THROW_ON_ERROR|JSON_PRESERVE_ZERO_FRACTION);}
}
