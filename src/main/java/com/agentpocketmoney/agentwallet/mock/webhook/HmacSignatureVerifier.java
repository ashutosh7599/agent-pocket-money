package com.agentpocketmoney.agentwallet.mock.webhook;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class HmacSignatureVerifier {
 private final String secret;
 public HmacSignatureVerifier(@Value("${WEBHOOK_SIGNING_SECRET:${webhooks.signing-secret:}}") String secret){this.secret=secret;}
 public boolean verify(String body,String signature){
  if(secret.isBlank()||body==null||signature==null||!signature.startsWith("sha256="))return false;
  try{
   byte[] expected=mac(body);
   byte[] supplied=HexFormat.of().parseHex(signature.substring(7));
   return MessageDigest.isEqual(expected,supplied);
  }catch(IllegalArgumentException e){return false;}
 }
 public String sign(String body){
  if(secret.isBlank())throw new IllegalStateException("Webhook signing secret is not configured.");
  return "sha256="+HexFormat.of().formatHex(mac(body));
 }
 private byte[] mac(String body){
  try{
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
  }catch(Exception e){throw new IllegalStateException("Webhook signature could not be calculated.",e);}
 }
}
