package org.bouncycastle.cert;

import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.Holder;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.asn1.x509.ObjectDigestInfo;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Selector;

public class AttributeCertificateHolder implements Selector {
    private static DigestCalculatorProvider digestCalculatorProvider;
    final Holder holder;

    public AttributeCertificateHolder(int i, ASN1ObjectIdentifier aSN1ObjectIdentifier, ASN1ObjectIdentifier aSN1ObjectIdentifier2, byte[] bArr) {
        this.holder = new Holder(new ObjectDigestInfo(i, aSN1ObjectIdentifier2, new AlgorithmIdentifier(aSN1ObjectIdentifier), Arrays.clone(bArr)));
    }

    AttributeCertificateHolder(ASN1Sequence aSN1Sequence) {
        this.holder = Holder.getInstance(aSN1Sequence);
    }

    public AttributeCertificateHolder(X500Name x500Name) {
        this.holder = new Holder(generateGeneralNames(x500Name));
    }

    public AttributeCertificateHolder(X500Name x500Name, BigInteger bigInteger) {
        this.holder = new Holder(new IssuerSerial(generateGeneralNames(x500Name), new ASN1Integer(bigInteger)));
    }

    public AttributeCertificateHolder(X509CertificateHolder x509CertificateHolder) {
        this.holder = new Holder(new IssuerSerial(generateGeneralNames(x509CertificateHolder.getIssuer()), new ASN1Integer(x509CertificateHolder.getSerialNumber())));
    }

    private GeneralNames generateGeneralNames(X500Name x500Name) {
        return new GeneralNames(new GeneralName(x500Name));
    }

    private X500Name[] getPrincipals(GeneralName[] generalNameArr) {
        ArrayList arrayList = new ArrayList(generalNameArr.length);
        for (int i = 0; i != generalNameArr.length; i++) {
            if (generalNameArr[i].getTagNo() == 4) {
                arrayList.add(X500Name.getInstance(generalNameArr[i].getName()));
            }
        }
        return (X500Name[]) arrayList.toArray(new X500Name[arrayList.size()]);
    }

    private boolean matchesDN(X500Name x500Name, GeneralNames generalNames) {
        GeneralName[] names = generalNames.getNames();
        for (int i = 0; i != names.length; i++) {
            GeneralName generalName = names[i];
            if (generalName.getTagNo() == 4 && X500Name.getInstance(generalName.getName()).equals(x500Name)) {
                return true;
            }
        }
        return false;
    }

    public static void setDigestCalculatorProvider(DigestCalculatorProvider digestCalculatorProvider) {
        digestCalculatorProvider = digestCalculatorProvider;
    }

    public Object clone() {
        return new AttributeCertificateHolder((ASN1Sequence) this.holder.toASN1Primitive());
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof AttributeCertificateHolder)) {
            return false;
        }
        return this.holder.equals(((AttributeCertificateHolder) obj).holder);
    }

    public AlgorithmIdentifier getDigestAlgorithm() {
        return this.holder.getObjectDigestInfo() != null ? this.holder.getObjectDigestInfo().getDigestAlgorithm() : null;
    }

    public int getDigestedObjectType() {
        return this.holder.getObjectDigestInfo() != null ? this.holder.getObjectDigestInfo().getDigestedObjectType().getValue().intValue() : -1;
    }

    public X500Name[] getEntityNames() {
        return this.holder.getEntityName() != null ? getPrincipals(this.holder.getEntityName().getNames()) : null;
    }

    public X500Name[] getIssuer() {
        return this.holder.getBaseCertificateID() != null ? getPrincipals(this.holder.getBaseCertificateID().getIssuer().getNames()) : null;
    }

    public byte[] getObjectDigest() {
        return this.holder.getObjectDigestInfo() != null ? this.holder.getObjectDigestInfo().getObjectDigest().getBytes() : null;
    }

    public ASN1ObjectIdentifier getOtherObjectTypeID() {
        if (this.holder.getObjectDigestInfo() != null) {
            ASN1ObjectIdentifier aSN1ObjectIdentifier = new ASN1ObjectIdentifier(this.holder.getObjectDigestInfo().getOtherObjectTypeID().getId());
        }
        return null;
    }

    public BigInteger getSerialNumber() {
        return this.holder.getBaseCertificateID() != null ? this.holder.getBaseCertificateID().getSerial().getValue() : null;
    }

    public int hashCode() {
        return this.holder.hashCode();
    }

    /* JADX WARNING: Missing block: B:23:0x0080, code skipped:
            r2.write(r5);
     */
    /* JADX WARNING: Missing block: B:25:0x008d, code skipped:
            r2.close();
     */
    /* JADX WARNING: Missing block: B:26:0x009c, code skipped:
            if (org.bouncycastle.util.Arrays.areEqual(r0.getDigest(), getObjectDigest()) != false) goto L_0x00a0;
     */
    /* JADX WARNING: Missing block: B:27:0x009e, code skipped:
            return false;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean match(Object obj) {
        boolean z = false;
        if (!(obj instanceof X509CertificateHolder)) {
            return false;
        }
        X509CertificateHolder x509CertificateHolder = (X509CertificateHolder) obj;
        if (this.holder.getBaseCertificateID() != null) {
            if (this.holder.getBaseCertificateID().getSerial().getValue().equals(x509CertificateHolder.getSerialNumber()) && matchesDN(x509CertificateHolder.getIssuer(), this.holder.getBaseCertificateID().getIssuer())) {
                z = true;
            }
            return z;
        } else if (this.holder.getEntityName() != null && matchesDN(x509CertificateHolder.getSubject(), this.holder.getEntityName())) {
            return true;
        } else {
            if (this.holder.getObjectDigestInfo() != null) {
                try {
                    DigestCalculator digestCalculator = digestCalculatorProvider.get(this.holder.getObjectDigestInfo().getDigestAlgorithm());
                    OutputStream outputStream = digestCalculator.getOutputStream();
                    byte[] encoded;
                    switch (getDigestedObjectType()) {
                        case 0:
                            encoded = x509CertificateHolder.getSubjectPublicKeyInfo().getEncoded();
                            break;
                        case 1:
                            encoded = x509CertificateHolder.getEncoded();
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                }
            }
            return false;
        }
    }
}
