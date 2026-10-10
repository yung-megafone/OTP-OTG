// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 OTPOTG contributors
package us.norec.otpotg;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG=0xff141414, PANEL=0xff242424, FG=0xffeeeeee, MUTED=0xffaaaaaa;
    private final SecureRandom random=new SecureRandom();
    private PadStore store;
    private LinearLayout root, content, tabBar;
    private EditText message,rollInput,cipherInput,keyInput;
    private TextView gaugeLabel,stats,warning, output,slipOutput;
    private ProgressBar gauge;
    private Button encryptButton;
    private int currentTab=1;
    private String pendingExport=null;
    private String lastSlip="",lastCipher="";
    private static final int EXPORT_SLIP=42, EXPORT_CIPHER=43;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        try {store=new PadStore(this);} catch(Exception e) {
            new AlertDialog.Builder(this).setTitle("Database error")
                .setMessage("Unable to load pad database. Refusing to overwrite it.\n"+e.getMessage())
                .setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();
            return;
        }
        showTab(1);
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }
    private void space(LinearLayout box,int h){
        View v=new View(this);box.addView(v,new LinearLayout.LayoutParams(1,dp(h)));
    }
    private void add(LinearLayout box,View v){box.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private TextView label(LinearLayout box,String value){
        TextView t=text(value,13,MUTED);add(box,t);space(box,6);return t;
    }
    private EditText input(LinearLayout box,String hint,int lines){
        EditText e=new EditText(this);
        e.setTextColor(FG);e.setHintTextColor(0xff888888);e.setHint(hint);
        e.setTextSize(15);e.setBackground(bg(0xff1a1a1a,8));e.setPadding(dp(12),dp(12),dp(12),dp(12));
        e.setMinLines(lines);e.setMaxLines(Math.max(lines,10));e.setGravity(Gravity.TOP);
        add(box,e);return e;
    }
    private Button button(LinearLayout box,String name,Runnable action){
        Button b=new Button(this);b.setText(name);b.setTextColor(FG);b.setAllCaps(false);
        b.setBackground(bg(0xff484848,8));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(10);box.addView(b,lp);
        return b;
    }
    private LinearLayout section(String title){
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14),dp(14),dp(14),dp(14));p.setBackground(bg(PANEL,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);content.addView(p,lp);
        TextView t=text(title,17,FG);t.setTypeface(null,Typeface.BOLD);add(p,t);space(p,12);return p;
    }
    private void showTab(int index){
        currentTab=index;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(dp(12),dp(12),dp(12),dp(8));setContentView(root);
        TextView title=text("OTPOTG",26,FG);title.setTypeface(null,Typeface.BOLD);add(root,title);
        add(root,text("OFFLINE DECIMAL ONE-TIME PAD  •  POC",11,MUTED));space(root,12);
        tabBar=new LinearLayout(this);tabBar.setOrientation(LinearLayout.HORIZONTAL);add(root,tabBar);
        String[] tabs={"PAD","ENCRYPT","DECRYPT","SETTINGS"};
        for(int i=0;i<tabs.length;i++){
            final int n=i;
            TextView tab=text(tabs[i],11,FG);tab.setGravity(Gravity.CENTER);
            tab.setBackground(bg(i==index?0xff555555:0xff292929,6));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);
            lp.rightMargin=dp(3);tabBar.addView(tab,lp);
            tab.setOnClickListener(v->showTab(n));
        }
        space(root,12);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        if(index==0)padTab();
        else if(index==1)encryptTab();
        else if(index==2)decryptTab();
        else settingsTab();
    }
    private void padTab(){
        LinearLayout p=section("PAD STORE");
        stats=text("",14,FG);add(p,stats);refreshStats();
        if(store.testData){TextView t=text("●  TEST DATA PRESENT — development use only",13,0xffff8c8c);add(p,t);}
        space(p,12);
        label(p,"Enter physical dice rolls (0–9)");
        rollInput=input(p,"Enter digits",4);
        rollInput.setInputType(2); // numeric keypad
        button(p,"Add rolls",()->{
            try {
                String digits=OtpCore.onlyDigits(rollInput.getText().toString());
                if(digits.isEmpty())throw new IllegalArgumentException("Enter some rolls");
                store.append(digits,false);rollInput.setText("");refreshStats();
                toast("Added "+digits.length()+" rolls");
            } catch(Exception e){error(e);}
        });
        TextView t=text("Use physical, independently generated D10 rolls for real pad material. "
            +"Never reuse a digit, and never share your pad with anyone who shouldn't read your messages.",13,MUTED);
        add(p,t);
    }
    private void refreshStats(){
        if(stats!=null)stats.setText(String.format(Locale.ROOT,
            "Available: %,d digits\nRetired: %,d digits",store.pad.length(),store.retired));
    }
    private void encryptTab(){
        LinearLayout p=section("ENCRYPT");
        label(p,"Message");
        message=input(p,"Your very important secret message…",5);
        gaugeLabel=text("",12,MUTED);space(p,12);add(p,gaugeLabel);
        gauge=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        gauge.setMax(1000);add(p,gauge);space(p,8);
        warning=text("",13,0xffffb5b5);add(p,warning);
        encryptButton=button(p,"Encrypt & Prune",this::encrypt);
        if(store.testData) {
            TextView test=text("●  TEST DATABASE — encryption is for development only. Purge before secure rolls.",13,0xffff9090);
            add(p,test);
            encryptButton.setOnLongClickListener(v->{new AlertDialog.Builder(this)
                .setTitle("Test data warning").setMessage("This pad contains synthetic digits. "
                    +"Encryption is for testing only. Purge before using physical rolls.")
                .setPositiveButton("OK",null).show();return true;});
        }
        LinearLayout result=section("OUTPUT");
        label(result,"Ciphertext (ID + decimal ciphertext)");
        output=text("No message encrypted yet.",13,FG);output.setTextIsSelectable(true);add(result,output);
        label(result,"Key slip — save before leaving this screen");
        slipOutput=text("",12,FG);slipOutput.setTextIsSelectable(true);add(result,slipOutput);
        button(result,"Save ciphertext to local file / USB",()->{
            if(lastCipher.isEmpty()){toast("Encrypt a message first");return;}
            pendingExport=lastCipher;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE,"otg-otp-message-"+lastCipher.substring(0,12)+".txt");
            startActivityForResult(i,EXPORT_CIPHER);
        });
        button(result,"Save key slip to local file / USB",()->{
            if(lastSlip.isEmpty()){toast("Encrypt a message first");return;}
            pendingExport=lastSlip;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE,"otg-otp-key-"+lastSlip.substring(lastSlip.indexOf("ID: ")+4,lastSlip.indexOf("ID: ")+16)+".txt");
            startActivityForResult(i,EXPORT_SLIP);
        });
        message.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int st,int before,int count){updateGauge();}
            public void afterTextChanged(Editable e){}
        });
        updateGauge();
    }
    private void updateGauge(){
        if(message==null||gauge==null)return;
        int needed=0;
        try{String m=message.getText().toString();if(!m.isEmpty())needed=12+OtpCore.encode(m,store.charset,store.byteOffset,store.block).digits.length();}
        catch(Exception e){warning.setText("Encoding error: "+e.getMessage());encryptButton.setEnabled(false);return;}
        int available=store.pad.length();
        double ratio=available==0?(needed>0?Double.POSITIVE_INFINITY:0):(double)needed/available;
        int shown=(int)Math.min(1000,Math.round(ratio*1000));
        gauge.setProgress(shown);
        int color;
        if(ratio>=2)color=0xfff59ac2;
        else if(ratio>=1)color=0xffd24343;
        else if(ratio>=.85)color=0xffe78f48;
        else if(ratio>=.7)color=0xffe2ca5b;
        else if(ratio>=.35)color=0xff63b79d;
        else color=0xff69a6d3;
        gauge.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        String percent=available==0?(needed==0?"0":"∞"):String.format(Locale.ROOT,"%.1f",ratio*100);
        gaugeLabel.setText("MESSAGE: "+needed+" digits  |  AVAILABLE: "+available+"  |  "+percent+"%");
        if(ratio>=2)warning.setText("Hey, dude, are you paying attention? The application recommends consulting a physician or purchasing more dice. "
            +"You passed your available digits "+(needed-available)+" digits ago.");
        else if(needed>available)warning.setText("You've reached your available digits. Roll "+(needed-available)
            +" more to continue your message. Don't worry, it'll still be here.");
        else warning.setText("");
        encryptButton.setEnabled(needed>0&&needed<=available);
        encryptButton.setAlpha(encryptButton.isEnabled()?1f:.5f);
    }
    private void encrypt(){
        try{
            String m=message.getText().toString();
            if(m.isEmpty())throw new IllegalArgumentException("Message is empty");
            OtpCore.Encoded payload=OtpCore.encode(m,store.charset,store.byteOffset,store.block);
            int count=12+payload.digits.length();
            if(count>store.pad.length())throw new IllegalArgumentException("Not enough rolls");
            String id=store.pad.substring(0,12);
            for(int i=0;i<store.history.length();i++)
                if(id.equals(store.history.getJSONObject(i).optString("id")))
                    throw new IllegalStateException("Message ID collision. No digits consumed.");
            String key=store.pad.substring(12,count);
            String cipher=OtpCore.add(payload.digits,key,1);
            String slip="OTPOTG KEY SLIP\nID: "+id+"\nENCODING: V2 / "+OtpCore.LABELS[store.charset]
                +"\nBLOCK: "+store.block+"\nDIGITS: "+key.length()+"\n\n"+OtpCore.group(key)
                +"\n\nSINGLE USE — KEEP SECRET";
            String ciphertext=id+"\n"+OtpCore.group(cipher);
            // Keep slip in memory and on screen; no automatic clipboard, disk, or network copy.
            store.consume(id,count); // atomically persist prune before claiming success
            lastSlip=slip;lastCipher=ciphertext;
            output.setText(ciphertext);slipOutput.setText(slip);
            updateGauge();
            new AlertDialog.Builder(this).setTitle("Encrypted & pruned")
                .setMessage("Consumed "+count+" digits. The database no longer contains those keys.\n\n"
                    +"SAVE THE KEY SLIP NOW. It cannot be regenerated from the pruned database.")
                .setPositiveButton("Understood",null).show();
        }catch(Exception e){error(e);}
    }
    private void decryptTab(){
        LinearLayout p=section("DECRYPT");
        label(p,"Ciphertext (12-digit ID followed by ciphertext)");
        cipherInput=input(p,"Paste decimal ciphertext",5);
        label(p,"Key slip digits (paste full slip or just digit groups)");
        keyInput=input(p,"Paste key digits",5);
        button(p,"Decrypt",()->{
            try{
                String all=OtpCore.onlyDigits(cipherInput.getText().toString());
                if(all.length()<13)throw new IllegalArgumentException("Missing ID or ciphertext");
                String id=all.substring(0,12),cipher=all.substring(12);
                String entered=keyInput.getText().toString();
                String key;
                if(entered.contains("OTPOTG KEY SLIP")||entered.contains("DICE / OTP KEY SLIP")){
                    int begin=entered.indexOf("\n\n");
                    if(begin<0)throw new IllegalArgumentException("Malformed key slip");
                    int end=entered.indexOf("\n\n",begin+2);
                    key=OtpCore.onlyDigits(entered.substring(begin+2,end<0?entered.length():end));
                    if(!entered.contains("ID: "+id))throw new IllegalArgumentException("Key slip ID mismatch");
                }else key=OtpCore.onlyDigits(entered);
                if(key.length()!=cipher.length())throw new IllegalArgumentException(
                    "Key/cipher length mismatch: "+key.length()+" vs "+cipher.length());
                String plain=OtpCore.decode(OtpCore.add(cipher,key,-1));
                new AlertDialog.Builder(this).setTitle("Decrypted plaintext")
                    .setMessage(plain).setPositiveButton("Close",null).show();
            }catch(Exception e){error(e);}
        });
        add(p,text("The receiver needs the matching key slip. This app never looks up consumed keys "
            +"in its own pad database.",13,MUTED));
    }
    private void settingsTab(){
        LinearLayout m=section("MESSAGE");
        label(m,"Padding block");
        spinner(m,new String[]{"5 digits","30 digits","300 digits","600 digits"},
            store.block==5?0:store.block==30?1:store.block==300?2:3,
            value->{store.block=new int[]{5,30,300,600}[value];persistSettings();});
        label(m,"Character encoding");
        spinner(m,OtpCore.LABELS,store.charset,value->{store.charset=value;persistSettings();});
        label(m,"Byte offset (0–255, reversible; NOT a security enhancement)");
        EditText offset=input(m,"0",1);offset.setInputType(2);offset.setText(String.valueOf(store.byteOffset));
        button(m,"Save byte offset",()->{
            try{int v=Integer.parseInt(offset.getText().toString());
                if(v<0||v>255)throw new IllegalArgumentException("Must be 0–255");
                store.byteOffset=v;store.save();toast("Offset saved");
            }catch(Exception e){error(e);}
        });
        LinearLayout db=section("DATABASE");
        add(db,text("Pad data stays in Android app-private storage. Backups are disabled. "
            +"Pruning does not guarantee forensic erasure from flash storage.",13,MUTED));
        button(db,"Retire remaining pad digits",()->confirm("Retire remaining digits?",
            "Discard all unused digits, preserving settings and history.",()->{store.retire();showTab(3);}));
        button(db,"Purge entire database",()->confirm("PURGE ALL PAD MATERIAL?",
            "Remove all unused digits and allocation history. This cannot be undone.",
            ()->{store.purge();showTab(3);}));
        LinearLayout dev=section("DEV");
        add(dev,text("Synthetic digits are TEST-ONLY. The database is flagged until purged or retired.",13,0xffff9999));
        label(dev,"Number of pseudorandom test digits");
        EditText count=input(dev,"1000",1);count.setInputType(2);count.setText("1000");
        button(dev,"Generate test digits",()->{
            try{
                int n=Integer.parseInt(count.getText().toString());
                if(n<1||n>100000)throw new IllegalArgumentException("Choose 1–100,000");
                confirm("Append TEST digits?","This marks the entire pad as development-only.",
                    ()->{StringBuilder b=new StringBuilder(n);for(int i=0;i<n;i++)b.append(random.nextInt(10));
                        store.append(b.toString(),true);showTab(3);});
            }catch(Exception e){error(e);}
        });
        button(dev,"Run local self-test",()->{
            try{
                for(int block:new int[]{5,30,300,600})
                    for(int off:new int[]{0,4,255})
                        for(String value:new String[]{"Hello, OTP!","", "🥳 UTF-8"}){
                            String enc=OtpCore.encode(value,0,off,block).digits;
                            String key=makeTestDigits(enc.length());
                            if(!OtpCore.decode(OtpCore.add(OtpCore.add(enc,key,1),key,-1)).equals(value))
                                throw new IllegalStateException("Round-trip mismatch");
                        }
                toast("36 encryption/decryption tests passed");
            }catch(Exception e){error(e);}
        });
    }
    private String makeTestDigits(int count){
        StringBuilder b=new StringBuilder(count);
        for(int i=0;i<count;i++)b.append(random.nextInt(10));return b.toString();
    }
    private interface Select {void changed(int index);}
    private void spinner(LinearLayout parent,String[] values,int selected,Select callback){
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,values);spinner.setAdapter(adapter);
        spinner.setSelection(selected);add(parent,spinner);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            boolean first=true;
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(first){first=false;return;}callback.changed(pos);
            }
        });
    }
    private void persistSettings(){try{store.save();}catch(Exception e){error(e);}}
    private interface Work {void run() throws Exception;}
    private void confirm(String title,String description,Work action){
        new AlertDialog.Builder(this).setTitle(title).setMessage(description)
            .setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->{
                try{action.run();}catch(Exception e){error(e);}
            }).show();
    }
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("Operation failed")
        .setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request==EXPORT_SLIP||request==EXPORT_CIPHER)&&result==RESULT_OK&&data!=null&&pendingExport!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IllegalStateException("Unable to open destination");
                out.write(pendingExport.getBytes(StandardCharsets.UTF_8));out.flush();
                toast("Key slip saved to selected destination");
            }catch(Exception e){error(e);}
            finally{pendingExport=null;}
        }
    }
}
