package com.aiworkbench.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Validates all RIFF chunks and animation frame boundaries without allocating a canvas. */
final class WebPStructureValidator {
    private WebPStructureValidator() {}
    static void validate(Path path) throws IOException {
        try(var f=new RandomAccessFile(path.toFile(),"r")) {
            if(!four(f).equals("RIFF")) throw new IOException(); long length=u32(f);
            if(length+8!=f.length() || !four(f).equals("WEBP")) throw new IOException();
            long width=0,height=0; boolean extended=false,animation=false,anim=false,image=false,frames=false;
            while(f.getFilePointer()<f.length()) {
                String type=four(f); long size=u32(f),start=f.getFilePointer(),end=start+size;
                boundary(f,end,size,f.length());
                switch(type) {
                    case "VP8X"->{
                        if(extended || image || frames || start!=20 || size!=10) throw new IOException();
                        int flags=f.readUnsignedByte(); if((flags&0xc1)!=0 || u24(f)!=0) throw new IOException();
                        width=u24(f)+1; height=u24(f)+1; extended=true; animation=(flags&2)!=0;
                    }
                    case "ANIM"->{ if(!extended || !animation || anim || frames || size!=6) throw new IOException(); anim=true; }
                    case "ANMF"->{
                        if(!extended || !animation || !anim || image || size<16) throw new IOException(); frames=true;
                        long x=u24(f)*2,y=u24(f)*2,w=u24(f)+1,h=u24(f)+1; u24(f);
                        if((f.readUnsignedByte()&0xfc)!=0 || x+w>width || y+h>height) throw new IOException();
                        boolean encoded=false;
                        while(f.getFilePointer()<end) {
                            String child=four(f); long n=u32(f),data=f.getFilePointer(),childEnd=data+n; boundary(f,childEnd,n,end);
                            if(child.equals("VP8 ") || child.equals("VP8L")) {
                                if(encoded) throw new IOException(); encoded=true; long[] dims=encoding(f,child,n);
                                if(dims[0]!=w || dims[1]!=h) throw new IOException();
                            } else if(child.equals("ALPH") && (encoded || n<1)) throw new IOException();
                            f.seek(childEnd+(n&1));
                        }
                        if(!encoded || f.getFilePointer()!=end) throw new IOException();
                    }
                    case "VP8 ","VP8L"->{
                        if(image || frames || animation) throw new IOException(); image=true; long[] dims=encoding(f,type,size);
                        if(extended && (width!=dims[0] || height!=dims[1])) throw new IOException();
                    }
                    case "ALPH"->{ if(!extended || image || frames || size<1) throw new IOException(); }
                    default->{ /* Metadata and unknown chunks are ignorable under the WebP container contract. */ }
                }
                f.seek(end+(size&1));
            }
            if(f.getFilePointer()!=f.length() || (!image && !frames) || (animation && !frames)) throw new IOException();
        }
    }
    private static long[] encoding(RandomAccessFile f,String type,long n) throws IOException {
        if(type.equals("VP8L")) {
            if(n<6 || f.readUnsignedByte()!=0x2f) throw new IOException(); long bits=u32(f);
            if((bits>>>29)!=0) throw new IOException(); return new long[]{(bits&0x3fff)+1,((bits>>>14)&0x3fff)+1};
        }
        if(n<11) throw new IOException(); long tag=u24(f);
        if((tag&1)!=0 || (tag>>>5)>n-3 || f.readUnsignedByte()!=0x9d || f.readUnsignedByte()!=0x01 || f.readUnsignedByte()!=0x2a) throw new IOException();
        long w=u16(f)&0x3fff,h=u16(f)&0x3fff; if(w==0 || h==0) throw new IOException(); return new long[]{w,h};
    }
    private static void boundary(RandomAccessFile f,long end,long size,long limit) throws IOException {
        if(end<f.getFilePointer() || end+(size&1)>limit) throw new IOException();
    }
    private static String four(RandomAccessFile f) throws IOException { byte[] b=new byte[4]; f.readFully(b); return new String(b,StandardCharsets.US_ASCII); }
    private static long u16(RandomAccessFile f) throws IOException { return f.readUnsignedByte()|((long)f.readUnsignedByte()<<8); }
    private static long u24(RandomAccessFile f) throws IOException { return u16(f)|((long)f.readUnsignedByte()<<16); }
    private static long u32(RandomAccessFile f) throws IOException { return u24(f)|((long)f.readUnsignedByte()<<24); }
}
