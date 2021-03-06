package sun.security.pkcs;

import java.io.IOException;
import sun.misc.HexDumpEncoder;
import sun.security.util.DerValue;
import sun.security.x509.GeneralNames;
import sun.security.x509.SerialNumber;

/* compiled from: SigningCertificateInfo */
class ESSCertId {
    private static volatile HexDumpEncoder hexDumper;
    private byte[] certHash;
    private GeneralNames issuer;
    private SerialNumber serialNumber;

    ESSCertId(DerValue certId) throws IOException {
        this.certHash = certId.data.getDerValue().toByteArray();
        if (certId.data.available() > 0) {
            DerValue issuerSerial = certId.data.getDerValue();
            this.issuer = new GeneralNames(issuerSerial.data.getDerValue());
            this.serialNumber = new SerialNumber(issuerSerial.data.getDerValue());
        }
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[\n\tCertificate hash (SHA-1):\n");
        if (hexDumper == null) {
            hexDumper = new HexDumpEncoder();
        }
        buffer.append(hexDumper.encode(this.certHash));
        if (!(this.issuer == null || this.serialNumber == null)) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("\n\tIssuer: ");
            stringBuilder.append(this.issuer);
            stringBuilder.append("\n");
            buffer.append(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append("\t");
            stringBuilder.append(this.serialNumber);
            buffer.append(stringBuilder.toString());
        }
        buffer.append("\n]");
        return buffer.toString();
    }
}
