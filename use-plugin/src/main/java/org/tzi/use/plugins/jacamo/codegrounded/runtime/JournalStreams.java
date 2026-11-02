package org.tzi.use.plugins.jacamo.codegrounded.runtime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/** Lossless storage codec only; canonical records and their hash chain are unchanged. */
final class JournalStreams {
 private JournalStreams(){}
 static byte[] compress(byte[] bytes)throws IOException{
  var output=new ByteArrayOutputStream();
  try(var gzip=new GZIPOutputStream(output,8192){{def.setLevel(Deflater.BEST_SPEED);}}){gzip.write(bytes);}
  return output.toByteArray();
 }
 static InputStream input(Path path,long physicalBytes,boolean compressed)throws IOException{
  var bounded=new FilterInputStream(Files.newInputStream(path)){
   long remaining=physicalBytes;
   @Override public int read()throws IOException{if(remaining==0)return -1;int value=super.read();if(value>=0)remaining--;return value;}
   @Override public int read(byte[] target,int offset,int length)throws IOException{
    if(remaining==0)return -1;int count=in.read(target,offset,(int)Math.min(remaining,length));if(count>0)remaining-=count;return count;
   }
   @Override public int available()throws IOException{return (int)Math.min(remaining,super.available());}
  };
  try{return compressed?new GZIPInputStream(bounded,65536):bounded;}catch(IOException error){bounded.close();throw error;}
 }
 static BufferedReader reader(Path path,long physicalBytes,boolean compressed)throws IOException{
  return new BufferedReader(new InputStreamReader(input(path,physicalBytes,compressed),StandardCharsets.UTF_8),8192){
   final char[] chunk=new char[8192];int position,count;boolean ended;
   @Override public String readLine()throws IOException{
    if(ended)return null;var line=new StringBuilder();
    while(true){
     if(position==count){count=super.read(chunk,0,chunk.length);position=0;if(count<0){ended=true;return line.isEmpty()?null:line.toString();}}
     int start=position;while(position<count&&chunk[position]!='\n')position++;
     if(line.length()+position-start>org.jacamo.bridge.contract.CanonicalJson.DEFAULT_MAX_BYTES)throw new IOException("JOURNAL_RECORD_SIZE_LIMIT");
     line.append(chunk,start,position-start);
     if(position<count){position++;if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return line.toString();}
    }
   }
  };
 }
}
