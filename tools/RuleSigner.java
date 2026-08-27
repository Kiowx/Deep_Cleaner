import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RuleSigner {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java tools/RuleSigner.java <rules.json> <private-key-directory> <signature-file>");
            System.exit(2);
        }
        Path payloadPath = Path.of(args[0]);
        Path keyDirectory = Path.of(args[1]);
        Path signaturePath = Path.of(args[2]);
        Files.createDirectories(keyDirectory);
        Path privatePath = keyDirectory.resolve("rules-private.pk8");
        Path publicPath = keyDirectory.resolve("rules-public.der");
        KeyPair pair = loadOrCreate(privatePath, publicPath);

        byte[] payload = Files.readAllBytes(payloadPath);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        Files.writeString(signaturePath, signature + System.lineSeparator(), StandardCharsets.US_ASCII);
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private static KeyPair loadOrCreate(Path privatePath, Path publicPath) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (Files.isRegularFile(privatePath) && Files.isRegularFile(publicPath)) {
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath)));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicPath)));
            return new KeyPair(publicKey, privateKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        Files.write(privatePath, pair.getPrivate().getEncoded());
        Files.write(publicPath, pair.getPublic().getEncoded());
        return pair;
    }
}
