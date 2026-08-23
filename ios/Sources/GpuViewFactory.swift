import Foundation
import MetalKit
import PamNative
import UIKit
public final class GpuViewFactory:NativeViewFactory,@unchecked Sendable{public init(){};public func create(context:AnyObject?,emit:@escaping(Data)->Void)->UIView{PamGpuView(emit:emit)};public func update(view:UIView,properties:[String:WireValue]){(view as?PamGpuView)?.update(properties)};public func release(view:UIView){(view as?PamGpuView)?.releaseGpu()}}
private final class PamGpuView:MTKView,MTKViewDelegate,@unchecked Sendable{
 private let emitter:(Data)->Void;private var pipeline:MTLRenderPipelineState?;private var revision:Int64 = -1;private var started=ProcessInfo.processInfo.systemUptime;private var uniforms:[String:Float]=[:]
 init(emit:@escaping(Data)->Void){emitter=emit;super.init(frame:.zero,device:MTLCreateSystemDefaultDevice());delegate=self;colorPixelFormat = .bgra8Unorm;clearColor=MTLClearColorMake(0,0,0,0);framebufferOnly=true}
 required init(coder:NSCoder){fatalError("init(coder:) is unavailable")}
 func update(_ values:[String:WireValue]){if case let.integer(mode)?=values["renderMode"]{isPaused=mode==2;enableSetNeedsDisplay=mode==2};guard case let.integer(next)?=values["revision"],next != revision,case let.text(source)?=values["metal"]else{return};revision=next;if case let.text(json)?=values["uniforms"],let data=json.data(using:.utf8),let decoded=try?JSONSerialization.jsonObject(with:data)as?[String:NSNumber]{uniforms=decoded.mapValues{$0.floatValue}};compile(source);setNeedsDisplay()}
 private func compile(_ source:String){guard let device else{send(3,"Metal is unavailable");return};do{let library=try device.makeLibrary(source:Self.prelude+source,options:nil);guard let vertex=library.makeFunction(name:"pam_vertex"),let fragment=library.makeFunction(name:"pam_fragment")else{throw GpuError.entryPoint};let descriptor=MTLRenderPipelineDescriptor();descriptor.vertexFunction=vertex;descriptor.fragmentFunction=fragment;descriptor.colorAttachments[0].pixelFormat=colorPixelFormat;pipeline=try device.makeRenderPipelineState(descriptor:descriptor);started=ProcessInfo.processInfo.systemUptime;send(1)}catch{pipeline=nil;send(3,error.localizedDescription)}}
 func draw(in view:MTKView){guard let pipeline,let drawable=currentDrawable,let descriptor=currentRenderPassDescriptor,let queue=device?.makeCommandQueue(),let buffer=queue.makeCommandBuffer(),let encoder=buffer.makeRenderCommandEncoder(descriptor:descriptor)else{return};var builtins=Builtins(resolution:SIMD2(Float(drawableSize.width),Float(drawableSize.height)),time:Float(ProcessInfo.processInfo.systemUptime-started));encoder.setRenderPipelineState(pipeline);encoder.setFragmentBytes(&builtins,length:MemoryLayout<Builtins>.stride,index:0);let values=uniforms.sorted{$0.key<$1.key}.map(\.value);values.withUnsafeBytes{bytes in if let base=bytes.baseAddress,!bytes.isEmpty{encoder.setFragmentBytes(base,length:bytes.count,index:1)}};encoder.drawPrimitives(type:.triangleStrip,vertexStart:0,vertexCount:4);encoder.endEncoding();buffer.present(drawable);buffer.commit()}
 func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize){};func releaseGpu(){delegate=nil;pipeline=nil;pause()};private func pause(){isPaused=true}
 private func send(_ event:Int64,_ message:String=""){if let data=try?WireMap.encode(["event":.integer(event),"message":.text(message)]){emitter(data)}}
 private static let prelude="""
 #include <metal_stdlib>
 using namespace metal;
 struct PamVertexOut { float4 position [[position]]; float2 uv; };
 struct PamBuiltins { float2 resolution; float time; };
 vertex PamVertexOut pam_vertex(uint id [[vertex_id]]) { float2 p[4]={float2(-1,-1),float2(1,-1),float2(-1,1),float2(1,1)}; PamVertexOut o; o.position=float4(p[id],0,1); o.uv=p[id]*.5+.5; return o; }
 """
}
private struct Builtins{var resolution:SIMD2<Float>;var time:Float}
private enum GpuError:LocalizedError{case entryPoint;var errorDescription:String?{"Metal source must define pam_fragment."}}
