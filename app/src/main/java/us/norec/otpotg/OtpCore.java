// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 OTPOTG contributors
package us.norec.otpotg;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Locale;

public final class OtpCore {
    private OtpCore() {}
    public static final String MAGIC = "999998";
    public static final int HEADER = 19;
    public static final String[] CHARSETS = {"UTF-8","US-ASCII","ISO-8859-1"};
    public static final String[] LABELS = {"UTF-8","ASCII","Latin-1"};

    public static final class Encoded {
        public final String digits;
        public final int bytes;
        Encoded(String digits, int bytes) { this.digits=digits; this.bytes=bytes; }
    }
    public static Charset charset(int mode) {
        if (mode<0 || mode>2) throw new IllegalArgumentException("Unknown charset");
        return Charset.forName(CHARSETS[mode]);
    }
    private static byte[] encodeStrict(String message,int mode) throws CharacterCodingException {
        ByteBuffer buf=charset(mode).newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(message));
        byte[] out=new byte[buf.remaining()];
        buf.get(out);
        return out;
    }
    public static Encoded encode(String message, int mode, int offset, int block) throws CharacterCodingException {
        if(offset<0||offset>255) throw new IllegalArgumentException("Offset must be 0..255");
        if(block<=0) throw new IllegalArgumentException("Invalid block");
        byte[] bytes=encodeStrict(message,mode);
        if(bytes.length>999_999_999) throw new IllegalArgumentException("Message too large");
        StringBuilder sb=new StringBuilder(HEADER+bytes.length*3+block);
        sb.append(MAGIC).append(mode).append(String.format(Locale.ROOT,"%03d",offset))
          .append(String.format(Locale.ROOT,"%09d",bytes.length));
        for(byte b:bytes) sb.append(String.format(Locale.ROOT,"%03d",((b&255)+offset)&255));
        while(sb.length()%block!=0) sb.append('0');
        return new Encoded(sb.toString(),bytes.length);
    }
    public static String decode(String digits) throws CharacterCodingException {
        if (!digits.matches("[0-9]+")) throw new IllegalArgumentException("Invalid decimal data");
        if(digits.startsWith(MAGIC)) {
            if(digits.length()<HEADER) throw new IllegalArgumentException("Truncated V2 header");
            int mode=digits.charAt(6)-'0';
            int offset=Integer.parseInt(digits.substring(7,10));
            int count=Integer.parseInt(digits.substring(10,19));
            if(mode<0||mode>2||offset>255) throw new IllegalArgumentException("Bad header");
            long end=(long)HEADER+(long)count*3;
            if(end>digits.length()) throw new IllegalArgumentException("Truncated data");
            for(int i=(int)end;i<digits.length();i++) if(digits.charAt(i)!='0')
                throw new IllegalArgumentException("Invalid padding");
            byte[] bytes=new byte[count];
            for(int i=0;i<count;i++){
                int value=Integer.parseInt(digits.substring(HEADER+i*3,HEADER+i*3+3));
                if(value>255) throw new IllegalArgumentException("Invalid encoded byte");
                bytes[i]=(byte)((value-offset+256)&255);
            }
            return charset(mode).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        if(digits.startsWith("999999")) {
            if(digits.length()<15) throw new IllegalArgumentException("Truncated V1 header");
            int count=Integer.parseInt(digits.substring(6,15));
            long end=15L+count*3L;
            if(end>digits.length()) throw new IllegalArgumentException("Truncated V1 data");
            for(int i=(int)end;i<digits.length();i++) if(digits.charAt(i)!='0')
                throw new IllegalArgumentException("Bad V1 padding");
            return decodeLegacy(digits.substring(15,(int)end));
        }
        return decodeLegacy(digits);
    }
    private static String decodeLegacy(String digits) throws CharacterCodingException {
        if(digits.length()%3!=0) throw new IllegalArgumentException("Legacy length must be multiple of 3");
        byte[] bytes=new byte[digits.length()/3];
        for(int i=0;i<bytes.length;i++){
            int n=Integer.parseInt(digits.substring(i*3,i*3+3));
            if(n>255) throw new IllegalArgumentException("Invalid legacy byte");
            bytes[i]=(byte)n;
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    public static String add(String a,String b,int direction) {
        if(a.length()!=b.length()||!a.matches("[0-9]*")||!b.matches("[0-9]*"))
            throw new IllegalArgumentException("Cipher/key mismatch");
        StringBuilder result=new StringBuilder(a.length());
        for(int i=0;i<a.length();i++)
            result.append((a.charAt(i)-'0'+direction*(b.charAt(i)-'0')+10)%10);
        return result.toString();
    }
    public static String group(String digits) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<digits.length();i+=5) {
            if(i>0) out.append(i%50==0?'\n':' ');
            out.append(digits, i, Math.min(i+5,digits.length()));
        }
        return out.toString();
    }
    public static String onlyDigits(String s) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c>='0'&&c<='9')out.append(c);
            else if(!Character.isWhitespace(c)) throw new IllegalArgumentException("Only ASCII digits and whitespace are allowed");
        }
        return out.toString();
    }
}
