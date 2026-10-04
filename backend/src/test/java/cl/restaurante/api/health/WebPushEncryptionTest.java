package cl.restaurante.api.health;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.security.*;
import java.util.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import nl.martijndwars.webpush.*;

class WebPushEncryptionTest {
    @Test void encryptsPayloadAndSignsVapidWithoutSendingAnything() throws Exception {
        if(Security.getProvider("BC")==null)Security.addProvider(new BouncyCastleProvider());
        var generator=KeyPairGenerator.getInstance("ECDH","BC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var server=generator.generateKeyPair();var browser=generator.generateKeyPair();
        var b64=Base64.getUrlEncoder().withoutPadding();
        String publicKey=b64.encodeToString(((ECPublicKey)server.getPublic()).getQ().getEncoded(false));
        byte[] secret=((ECPrivateKey)server.getPrivate()).getD().toByteArray();
        byte[] fixed=new byte[32];System.arraycopy(secret,Math.max(0,secret.length-32),fixed,Math.max(0,32-secret.length),Math.min(32,secret.length));
        var service=new PushService(publicKey,b64.encodeToString(fixed),"mailto:test@example.invalid");
        String text="Mensaje privado de prueba";
        var post=service.preparePost(new Notification("https://fcm.googleapis.com/fcm/send/local-test",b64.encodeToString(((ECPublicKey)browser.getPublic()).getQ().getEncoded(false)),b64.encodeToString(new byte[16]),text.getBytes(java.nio.charset.StandardCharsets.UTF_8),60),Encoding.AES128GCM);
        assertEquals("aes128gcm",post.getFirstHeader("Content-Encoding").getValue());
        assertTrue(post.getFirstHeader("Authorization").getValue().startsWith("vapid "));
        byte[] encrypted=post.getEntity().getContent().readAllBytes();
        assertTrue(encrypted.length>text.length());
        assertFalse(new String(encrypted,java.nio.charset.StandardCharsets.UTF_8).contains(text));
    }
}
