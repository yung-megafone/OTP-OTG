import us.norec.otpotg.OtpCore;
public class CoreSmokeTest {
    public static void main(String[] args) throws Exception {
        int tested=0;
        for(int block:new int[]{5,30,300,600})
            for(int offset:new int[]{0,4,255})
                for(String msg:new String[]{"Hello, OTP!","", "🥳 UTF-8"}) {
                    String encoded=OtpCore.encode(msg,0,offset,block).digits;
                    if(encoded.length()%block!=0)throw new AssertionError("Block padding");
                    String key="1234567890".repeat((encoded.length()+9)/10).substring(0,encoded.length());
                    String ciphertext=OtpCore.add(encoded,key,1);
                    String recovered=OtpCore.decode(OtpCore.add(ciphertext,key,-1));
                    if(!msg.equals(recovered))throw new AssertionError("Failed round trip");
                    tested++;
                }
        for(int mode=1;mode<=2;mode++) {
            String value="Hello World";
            String encoded=OtpCore.encode(value,mode,4,5).digits;
            if(!value.equals(OtpCore.decode(encoded)))throw new AssertionError("Charset mismatch");
            tested++;
        }
        String v1="99999900000000306506606700";
        if(!"ABC".equals(OtpCore.decode(v1)))throw new AssertionError("V1 decode");
        if(!"ABC".equals(OtpCore.decode("065066067")))throw new AssertionError("Legacy decode");
        System.out.println("PASS: "+(tested+2)+" core compatibility / round-trip tests");
    }
}
