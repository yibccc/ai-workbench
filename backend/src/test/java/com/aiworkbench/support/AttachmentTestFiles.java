package com.aiworkbench.support;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Valid originals with padding inside real PDF whitespace / PNG ancillary chunks. */
public final class AttachmentTestFiles {
    private AttachmentTestFiles() {}
    public static byte[] pdfExact(int size) throws IOException {
        byte[] header="%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        List<String> objects=List.of("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>\nendobj\n");
        int padding=size-400;
        for(int attempt=0;attempt<5;attempt++) {
            var output=new ByteArrayOutputStream(size); output.write(header); byte[] spaces=new byte[padding]; Arrays.fill(spaces,(byte)' '); output.write(spaces);
            List<Integer> offsets=new ArrayList<>();
            for(String object:objects) { offsets.add(output.size()); output.write(object.getBytes(StandardCharsets.US_ASCII)); }
            int xref=output.size(); StringBuilder tail=new StringBuilder("xref\n0 4\n0000000000 65535 f \n");
            for(int offset:offsets) tail.append(String.format(Locale.ROOT,"%010d 00000 n \n",offset));
            tail.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
            output.write(tail.toString().getBytes(StandardCharsets.US_ASCII));
            if(output.size()==size) return output.toByteArray(); padding+=size-output.size();
        }
        throw new IOException("Cannot create exact PDF fixture");
    }
    public static byte[] pngExact(int size) throws IOException {
        var original=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",original);
        byte[] png=original.toByteArray(); int padding=size-png.length-12; if(padding<0) throw new IOException();
        var output=new ByteArrayOutputStream(size); output.write(png,0,png.length-12);
        var data=new DataOutputStream(output); data.writeInt(padding); byte[] type="wbSt".getBytes(StandardCharsets.US_ASCII); data.write(type);
        byte[] bytes=new byte[padding]; data.write(bytes); CRC32 crc=new CRC32(); crc.update(type); crc.update(bytes); data.writeInt((int)crc.getValue());
        output.write(png,png.length-12,12); return output.toByteArray();
    }
}
