package com.aiworkbench.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.*;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Strict xref/envelope parsing; never render pages, execute scripts, or demand a document password. */
final class PdfStructureValidator {
    private PdfStructureValidator() {}
    static void validate(Path path) throws IOException {
        boolean encrypted;
        try(var input=new RandomAccessReadBufferedFile(path); var parser=new Envelope(input,path)) {
            encrypted=parser.validate();
        }
        if(encrypted) return; // Only observable outer structure is provable without decryption materials.
        try(var input=new RandomAccessReadBufferedFile(path)) {
            PDFParser parser=new PDFParser(input,"",null,null,IOUtils.createTempFileOnlyStreamCache());
            try(PDDocument document=parser.parse(false)) {
                COSDictionary catalog=document.getDocumentCatalog().getCOSObject();
                if(!COSName.CATALOG.equals(catalog.getCOSName(COSName.TYPE))) throw new IOException();
                COSBase pages=catalog.getDictionaryObject(COSName.PAGES);
                if(!(pages instanceof COSDictionary root)) throw new IOException();
                visitPages(root,Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
    }
    private static void visitPages(COSDictionary node,Set<COSDictionary> visited) throws IOException {
        if(!visited.add(node)) throw new IOException(); COSName type=node.getCOSName(COSName.TYPE);
        if(COSName.PAGE.equals(type)) return;
        if(!COSName.PAGES.equals(type) || !(node.getDictionaryObject(COSName.KIDS) instanceof COSArray kids)
                || !(node.getDictionaryObject(COSName.COUNT) instanceof COSInteger count) || count.longValue()<0) throw new IOException();
        for(int i=0;i<kids.size();i++) {
            if(!(kids.getObject(i) instanceof COSDictionary child)) throw new IOException(); visitPages(child,visited);
        }
    }
    private static final class Envelope extends PDFParser implements AutoCloseable {
        private final Path path;
        Envelope(RandomAccessRead input,Path path) throws IOException { super(input,"",null,null,IOUtils.createTempFileOnlyStreamCache()); this.path=path; }
        @Override protected void prepareDecryption() { /* This parser checks clear structure and never invokes a security handler. */ }
        boolean validate() throws IOException {
            setLenient(false); if(!parsePDFHeader()) throw new IOException(); retrieveTrailer();
            COSDictionary trailer=document.getTrailer(); if(trailer==null || !(trailer.getItem(COSName.ROOT) instanceof COSObject root)) throw new IOException();
            var xrefs=document.getXrefTable(); if(xrefs.isEmpty() || !xrefs.containsKey(new COSObjectKey(root.getObjectNumber(),root.getGenerationNumber()))) throw new IOException();
            // Positive xref offsets must name the declared object, not just fall within the file.
            try(var file=new RandomAccessFile(path.toFile(),"r")) {
                for(var item:xrefs.entrySet()) if(item.getValue()>0) {
                    long offset=item.getValue(); if(offset>=file.length()) throw new IOException(); file.seek(offset);
                    byte[] bytes=new byte[(int)Math.min(96,file.length()-offset)]; file.readFully(bytes);
                    String header=new String(bytes,StandardCharsets.ISO_8859_1);
                    if(!header.matches("(?s)^"+item.getKey().getNumber()+"\\s+"+item.getKey().getGeneration()+"\\s+obj(?:\\s|[<\\[/]).*")) throw new IOException();
                }
                // Strict parser locates startxref. Also require the final EOF marker without invented repairs.
                file.seek(Math.max(0,file.length()-1024)); byte[] tail=new byte[(int)(file.length()-file.getFilePointer())]; file.readFully(tail);
                if(!new String(tail,StandardCharsets.ISO_8859_1).matches("(?s).*%%EOF\\s*")) throw new IOException();
            }
            if(trailer.containsKey(COSName.ENCRYPT)) {
                COSDictionary encryption=document.getEncryptionDictionary();
                if(encryption==null || !(encryption.getDictionaryObject(COSName.FILTER) instanceof COSName)) throw new IOException();
                return true;
            }
            return false;
        }
        @Override public void close() throws IOException { if(document!=null) document.close(); }
    }
}
