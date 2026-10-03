// Reproduction utility only, not a production dependency or application entry point.
// Compile with PDFBox 3.0.8 and BouncyCastle 1.76 (bcprov/bcpkix/bcutil), then pass output path.
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class GeneratePublicKeyPdf {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair=generator.generateKeyPair();
        var name=new X500Name("CN=Synthetic attachment fixture");
        var holder=new JcaX509v3CertificateBuilder(name,BigInteger.ONE,Date.from(Instant.parse("2026-01-01T00:00:00Z")),
                Date.from(Instant.parse("2036-01-01T00:00:00Z")),name,pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate()));
        X509Certificate certificate=new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        var recipient=new PublicKeyRecipient(); recipient.setX509(certificate); recipient.setPermission(new AccessPermission());
        var policy=new PublicKeyProtectionPolicy(); policy.addRecipient(recipient); policy.setEncryptionKeyLength(128);
        try(var document=new PDDocument()) { document.addPage(new PDPage()); document.protect(policy); document.save(args[0]); }
        // Private key exists only in memory and is not written into the fixture.
    }
}
