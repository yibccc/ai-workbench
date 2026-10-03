package com.aiworkbench.storage;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.*;
import javax.imageio.stream.FileImageInputStream;

/** No Spring context, network, conversion, or document execution in this bounded child process. */
public final class AttachmentValidationWorker {
    private AttachmentValidationWorker() {}
    public static void main(String[] args) {
        try {
            if(args.length!=2) throw new IOException();
            validate(Path.of(args[0]),args[1]); System.out.print("VALID");
        } catch(IOException | IllegalArgumentException invalid) { System.out.print("INVALID"); System.exit(2); }
        catch(Throwable resourceFailure) { System.out.print("UNAVAILABLE"); System.exit(3); }
    }
    static void validate(Path path,String extension) throws IOException {
        switch(extension) {
            case "md"->MarkdownText.decode(path);
            case "pdf"->PdfStructureValidator.validate(path);
            case "webp"->{ WebPStructureValidator.validate(path); image(path,"WebP"); }
            case "png"->{ png(path); image(path,"png"); }
            case "jpg","jpeg"->image(path,"JPEG");
            default->throw new IOException();
        }
    }
    private static void image(Path path,String expected) throws IOException {
        try(var input=new FileImageInputStream(path.toFile())) {
            Iterator<ImageReader> available=ImageIO.getImageReaders(input);
            if(!available.hasNext()) throw new IOException(); ImageReader reader=available.next();
            try {
                if(!expected.equalsIgnoreCase(reader.getFormatName())) throw new IOException();
                reader.setInput(input,false,true); int frames=reader.getNumImages(true);
                if(frames<1) throw new IOException();
                boolean[] warning={false}; reader.addIIOReadWarningListener((ignored,message)->{
                    String text=message.toLowerCase(Locale.ROOT); if(text.contains("truncat") || text.contains("premature") || text.contains("corrupt")) warning[0]=true;
                });
                for(int i=0;i<frames;i++) {
                    int width=reader.getWidth(i),height=reader.getHeight(i); if(width<=0 || height<=0) throw new IOException();
                    long pixels=(long)width*height;
                    // Full RIFF/frame/encoding-header validation already ran. Fixed WebP decoder
                    // allocates a full VP8L/ALPH frame and rejects ratio>2048 before sampling.
                    // Retain legal high-compression files via that explicitly limited structure path.
                    if(expected.equalsIgnoreCase("WebP") && (pixels>4_194_304 || pixels*4>Files.size(path)*2048)) continue;
                    var param=reader.getDefaultReadParam();
                    param.setSourceSubsampling((int)Math.max(1L,((long)width+255)/256),(int)Math.max(1L,((long)height+255)/256),0,0);
                    BufferedImage sample=reader.read(i,param);
                    if(sample==null || warning[0]) throw new IOException(); sample.flush();
                }
            } finally { reader.dispose(); }
        }
    }
    private static void png(Path path) throws IOException {
        try(var file=new RandomAccessFile(path.toFile(),"r")) {
            if(file.readLong()!=0x89504e470d0a1a0aL) throw new IOException(); boolean ihdr=false,idat=false,end=false;
            byte[] buffer=new byte[8192];
            while(file.getFilePointer()<file.length()) {
                long length=Integer.toUnsignedLong(file.readInt()); byte[] type=new byte[4]; file.readFully(type);
                String name=new String(type,StandardCharsets.US_ASCII);
                if(length>file.length()-file.getFilePointer()-4) throw new IOException();
                if(!ihdr && (!name.equals("IHDR") || length!=13)) throw new IOException();
                if(name.equals("IHDR")) { if(ihdr) throw new IOException(); ihdr=true; }
                if(name.equals("IDAT")) idat=true;
                CRC32 crc=new CRC32(); crc.update(type); long remaining=length;
                while(remaining>0) { int count=(int)Math.min(remaining,buffer.length); file.readFully(buffer,0,count); crc.update(buffer,0,count); remaining-=count; }
                if(crc.getValue()!=Integer.toUnsignedLong(file.readInt())) throw new IOException();
                if(name.equals("IEND")) { if(length!=0 || !idat || file.getFilePointer()!=file.length()) throw new IOException(); end=true; break; }
            }
            if(!end) throw new IOException();
        }
    }
}
