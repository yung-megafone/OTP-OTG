// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 OTPOTG contributors
package us.norec.otpotg;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class PadStore {
    private final AtomicFile file;
    public String pad="";
    public long retired=0;
    public boolean testData=false;
    public int block=5, charset=0, byteOffset=0;
    public JSONArray history=new JSONArray();

    public PadStore(Context context) throws Exception {
        file=new AtomicFile(new File(context.getFilesDir(),"pad.json"));
        if(file.getBaseFile().exists()) load();
    }
    private void load() throws Exception {
        try (FileInputStream stream=file.openRead()) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n;
            while((n=stream.read(buf))!=-1)out.write(buf,0,n);
            JSONObject o=new JSONObject(out.toString("UTF-8"));
            pad=o.optString("pad","");
            if(!pad.matches("[0-9]*")) throw new IllegalStateException("Invalid pad store");
            retired=o.optLong("retired",0);
            testData=o.optBoolean("testData",false);
            block=o.optInt("block",5); charset=o.optInt("charset",0);
            byteOffset=o.optInt("byteOffset",0);
            history=o.optJSONArray("history");
            if(history==null) history=new JSONArray();
            if(block!=5&&block!=30&&block!=300&&block!=600) throw new IllegalStateException("Bad block setting");
            if(charset<0||charset>2||byteOffset<0||byteOffset>255) throw new IllegalStateException("Bad encoding settings");
        }
    }
    public synchronized void save() throws Exception {
        JSONObject o=new JSONObject();
        o.put("format",1);o.put("pad",pad);o.put("retired",retired);
        o.put("testData",testData);o.put("block",block);
        o.put("charset",charset);o.put("byteOffset",byteOffset);
        o.put("history",history);
        byte[] bytes=o.toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream stream=file.startWrite();
        try { stream.write(bytes);file.finishWrite(stream); }
        catch(Exception e){file.failWrite(stream);throw e;}
    }
    public synchronized void append(String digits,boolean synthetic) throws Exception {
        String old=pad;boolean oldTest=testData;
        pad+=digits;testData|=synthetic;
        try {save();} catch(Exception e){pad=old;testData=oldTest;throw e;}
    }
    public synchronized void consume(String id,int amount) throws Exception {
        if(amount<0||amount>pad.length())throw new IllegalArgumentException("Insufficient pad");
        String old=pad;long oldRetired=retired;JSONArray oldHistory=history;
        JSONObject record=new JSONObject();
        record.put("id",id);record.put("offset",retired);record.put("length",amount-12);
        JSONArray next=new JSONArray();
        for(int i=0;i<history.length();i++)next.put(history.get(i));
        next.put(record);
        pad=pad.substring(amount);retired+=amount;history=next;
        try{save();}catch(Exception e){pad=old;retired=oldRetired;history=oldHistory;throw e;}
    }
    public synchronized void purge() throws Exception {
        String old=pad;long oldRetired=retired;JSONArray oldHistory=history;boolean oldTest=testData;
        pad="";retired=0;history=new JSONArray();testData=false;
        try{save();}catch(Exception e){pad=old;retired=oldRetired;history=oldHistory;testData=oldTest;throw e;}
    }
    public synchronized void retire() throws Exception {
        String old=pad;long oldRetired=retired;boolean oldTest=testData;
        retired+=pad.length();pad="";testData=false;
        try{save();}catch(Exception e){pad=old;retired=oldRetired;testData=oldTest;throw e;}
    }
}
