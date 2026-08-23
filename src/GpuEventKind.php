<?php
declare(strict_types=1);
namespace Pam\Native\Gpu;
enum GpuEventKind:int{case Ready=1;case Frame=2;case Error=3;}
