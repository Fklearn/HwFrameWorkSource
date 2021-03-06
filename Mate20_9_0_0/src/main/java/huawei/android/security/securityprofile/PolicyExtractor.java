package huawei.android.security.securityprofile;

import android.util.Pair;
import android.util.Slog;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

public class PolicyExtractor {
    private static final int APK_SECURITY_POLICY_BLOCK_ID = 1212241481;
    private static final long APK_SIG_BLOCK_MAGIC_HI = 3617552046287187010L;
    private static final long APK_SIG_BLOCK_MAGIC_LO = 2334950737559900225L;
    private static final int APK_SIG_BLOCK_MIN_SIZE = 32;
    private static final long HUAWEI_BLOCK_MAGIC_HI = 8746950815728558949L;
    private static final long HUAWEI_BLOCK_MAGIC_LO = 5989903388918248776L;
    private static final String TAG = "PolicyExtractor";

    private static class ApkInfo {
        long centralDirOffset;
        ByteBuffer eocdBuffer;
        long eocdOffset;
        ByteBuffer originalEocdBuffer;
        byte[] policyBlock;
        ByteBuffer signingBlockBuffer;
        long signingBlockOffset;

        private ApkInfo() {
            this.signingBlockOffset = -1;
            this.signingBlockBuffer = null;
            this.policyBlock = null;
            this.centralDirOffset = -1;
            this.eocdOffset = -1;
            this.eocdBuffer = null;
            this.originalEocdBuffer = null;
        }
    }

    public static class PolicyNotFoundException extends Exception {
        private static final long serialVersionUID = 1;

        public PolicyNotFoundException(String message) {
            super(message);
        }

        public PolicyNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static ApkDigest getDigest(String packageName, byte[] policyBlock) {
        String str;
        StringBuilder stringBuilder;
        if (packageName == null || policyBlock == null) {
            Slog.e(TAG, "getDigest err,package name or policyBlock is null");
            return null;
        }
        try {
            String[] parts = StringFactory.newStringFromBytes(policyBlock, StandardCharsets.UTF_8).split("\\.");
            if (parts.length != 3) {
                return null;
            }
            JSONObject apkDigest = new JSONObject(StringFactory.newStringFromBytes(Base64.getUrlDecoder().decode(parts[1].getBytes()), StandardCharsets.UTF_8)).getJSONObject("domains").getJSONArray(packageName).getJSONObject(0).getJSONObject("apk_digest");
            return new ApkDigest(apkDigest.optString("signature_scheme", "v2"), apkDigest.optString("digest_algorithm", "SHA-256"), apkDigest.optString("digest", ""));
        } catch (JSONException e) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append(packageName);
            stringBuilder.append(" getDigest err:");
            stringBuilder.append(e.getMessage());
            Slog.e(str, stringBuilder.toString());
            return null;
        } catch (Exception e2) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append(packageName);
            stringBuilder.append(" getDigest err:");
            stringBuilder.append(e2.getMessage());
            Slog.e(str, stringBuilder.toString());
            return null;
        }
    }

    public static byte[] getPolicy(String apkFile) throws IOException, PolicyNotFoundException {
        if (apkFile == null) {
            Slog.e(TAG, "getPolicy err,apkFile is null");
            return null;
        }
        long t = System.nanoTime();
        ApkInfo info = getApkInfo(apkFile);
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("getPolicy took");
        stringBuilder.append(String.valueOf(System.nanoTime() - t));
        Slog.d(str, stringBuilder.toString());
        return info.policyBlock;
    }

    private static ApkInfo getApkInfo(String apkFile) throws PolicyNotFoundException, IOException {
        ApkInfo info = new ApkInfo();
        RandomAccessFile apk;
        try {
            apk = new RandomAccessFile(apkFile, "r");
            Pair<ByteBuffer, Long> eocdAndOffsetInFile = getEocd(apk);
            info.eocdBuffer = (ByteBuffer) eocdAndOffsetInFile.first;
            info.eocdOffset = ((Long) eocdAndOffsetInFile.second).longValue();
            if (ZipUtils.isZip64EndOfCentralDirectoryLocatorPresent(apk, info.eocdOffset)) {
                throw new PolicyNotFoundException("ZIP64 APK not supported");
            }
            Pair<ByteBuffer, Long> apkSigningBlockAndOffsetInFile;
            info.centralDirOffset = getCentralDirOffset(info.eocdBuffer, info.eocdOffset);
            Pair<ByteBuffer, Long> apkSigningBlockAndOffsetInFile2 = null;
            try {
                apkSigningBlockAndOffsetInFile = findApkSigningBlock(apk, info.centralDirOffset, APK_SIG_BLOCK_MAGIC_HI, APK_SIG_BLOCK_MAGIC_LO);
            } catch (PolicyNotFoundException e) {
                apkSigningBlockAndOffsetInFile = findApkSigningBlock(apk, info.centralDirOffset, HUAWEI_BLOCK_MAGIC_HI, HUAWEI_BLOCK_MAGIC_LO);
            }
            info.signingBlockBuffer = (ByteBuffer) apkSigningBlockAndOffsetInFile.first;
            info.signingBlockOffset = ((Long) apkSigningBlockAndOffsetInFile.second).longValue();
            ByteBuffer policyBlockBuffer = findApkSignatureSchemeV2Block(info.signingBlockBuffer, APK_SECURITY_POLICY_BLOCK_ID);
            info.policyBlock = new byte[policyBlockBuffer.remaining()];
            policyBlockBuffer.get(info.policyBlock);
            info.originalEocdBuffer = (ByteBuffer) getEocd(apk).first;
            ZipUtils.setZipEocdCentralDirectoryOffset(info.originalEocdBuffer, info.signingBlockOffset);
            info.originalEocdBuffer.rewind();
            apk.close();
            return info;
        } catch (Exception e2) {
            throw e2;
        } catch (Throwable th) {
            r1.addSuppressed(th);
        }
    }

    private static Pair<ByteBuffer, Long> getEocd(RandomAccessFile apk) throws IOException, PolicyNotFoundException {
        Pair<ByteBuffer, Long> eocdAndOffsetInFile = ZipUtils.findZipEndOfCentralDirectoryRecord(apk);
        if (eocdAndOffsetInFile != null) {
            return eocdAndOffsetInFile;
        }
        throw new PolicyNotFoundException("Not an APK file: ZIP End of Central Directory record not found");
    }

    private static long getCentralDirOffset(ByteBuffer eocd, long eocdOffset) throws PolicyNotFoundException {
        long centralDirOffset = ZipUtils.getZipEocdCentralDirectoryOffset(eocd);
        if (centralDirOffset > eocdOffset) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ZIP Central Directory offset out of range: ");
            stringBuilder.append(centralDirOffset);
            stringBuilder.append(". ZIP End of Central Directory offset: ");
            stringBuilder.append(eocdOffset);
            throw new PolicyNotFoundException(stringBuilder.toString());
        } else if (centralDirOffset + ZipUtils.getZipEocdCentralDirectorySizeBytes(eocd) == eocdOffset) {
            return centralDirOffset;
        } else {
            throw new PolicyNotFoundException("ZIP Central Directory is not immediately followed by End of Central Directory");
        }
    }

    private static ByteBuffer sliceFromTo(ByteBuffer source, int start, int end) {
        StringBuilder stringBuilder;
        if (start < 0) {
            stringBuilder = new StringBuilder();
            stringBuilder.append("start: ");
            stringBuilder.append(start);
            throw new IllegalArgumentException(stringBuilder.toString());
        } else if (end >= start) {
            int capacity = source.capacity();
            if (end <= source.capacity()) {
                int originalLimit = source.limit();
                int originalPosition = source.position();
                try {
                    source.position(0);
                    source.limit(end);
                    source.position(start);
                    ByteBuffer result = source.slice();
                    result.order(source.order());
                    return result;
                } finally {
                    source.position(0);
                    source.limit(originalLimit);
                    source.position(originalPosition);
                }
            } else {
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("end > capacity: ");
                stringBuilder2.append(end);
                stringBuilder2.append(" > ");
                stringBuilder2.append(capacity);
                throw new IllegalArgumentException(stringBuilder2.toString());
            }
        } else {
            stringBuilder = new StringBuilder();
            stringBuilder.append("end < start: ");
            stringBuilder.append(end);
            stringBuilder.append(" < ");
            stringBuilder.append(start);
            throw new IllegalArgumentException(stringBuilder.toString());
        }
    }

    private static ByteBuffer getByteBuffer(ByteBuffer source, int size) throws BufferUnderflowException {
        if (size >= 0) {
            int originalLimit = source.limit();
            int position = source.position();
            int limit = position + size;
            if (limit < position || limit > originalLimit) {
                throw new BufferUnderflowException();
            }
            source.limit(limit);
            try {
                ByteBuffer result = source.slice();
                result.order(source.order());
                source.position(limit);
                return result;
            } finally {
                source.limit(originalLimit);
            }
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("size: ");
            stringBuilder.append(size);
            throw new IllegalArgumentException(stringBuilder.toString());
        }
    }

    private static Pair<ByteBuffer, Long> findApkSigningBlock(RandomAccessFile apk, long centralDirOffset, long hi, long lo) throws IOException, PolicyNotFoundException {
        RandomAccessFile randomAccessFile = apk;
        long j = centralDirOffset;
        StringBuilder stringBuilder;
        if (j >= 32) {
            ByteBuffer footer = ByteBuffer.allocate(24);
            footer.order(ByteOrder.LITTLE_ENDIAN);
            randomAccessFile.seek(j - ((long) footer.capacity()));
            randomAccessFile.readFully(footer.array(), footer.arrayOffset(), footer.capacity());
            if (footer.getLong(8) == lo && footer.getLong(16) == hi) {
                long apkSigBlockSizeInFooter = footer.getLong(0);
                if (apkSigBlockSizeInFooter < ((long) footer.capacity()) || apkSigBlockSizeInFooter > 2147483639) {
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("APK Signing Block size out of range: ");
                    stringBuilder.append(apkSigBlockSizeInFooter);
                    throw new PolicyNotFoundException(stringBuilder.toString());
                }
                int totalSize = (int) (8 + apkSigBlockSizeInFooter);
                long apkSigBlockOffset = j - ((long) totalSize);
                if (apkSigBlockOffset >= 0) {
                    ByteBuffer apkSigBlock = ByteBuffer.allocate(totalSize);
                    apkSigBlock.order(ByteOrder.LITTLE_ENDIAN);
                    randomAccessFile.seek(apkSigBlockOffset);
                    randomAccessFile.readFully(apkSigBlock.array(), apkSigBlock.arrayOffset(), apkSigBlock.capacity());
                    footer = apkSigBlock.getLong(null);
                    if (footer == apkSigBlockSizeInFooter) {
                        return Pair.create(apkSigBlock, Long.valueOf(apkSigBlockOffset));
                    }
                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("APK Signing Block sizes in header and footer do not match: ");
                    stringBuilder2.append(footer);
                    stringBuilder2.append(" vs ");
                    stringBuilder2.append(apkSigBlockSizeInFooter);
                    throw new PolicyNotFoundException(stringBuilder2.toString());
                }
                int i = totalSize;
                stringBuilder = new StringBuilder();
                stringBuilder.append("APK Signing Block offset out of range: ");
                stringBuilder.append(apkSigBlockOffset);
                throw new PolicyNotFoundException(stringBuilder.toString());
            }
            throw new PolicyNotFoundException("No APK Signing Block before ZIP Central Directory");
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append("APK too small for APK Signing Block. ZIP Central Directory offset: ");
        stringBuilder.append(j);
        throw new PolicyNotFoundException(stringBuilder.toString());
    }

    private static ByteBuffer findApkSignatureSchemeV2Block(ByteBuffer apkSigningBlock, int blockId) throws PolicyNotFoundException {
        checkByteOrderLittleEndian(apkSigningBlock);
        ByteBuffer pairs = sliceFromTo(apkSigningBlock, 8, apkSigningBlock.capacity() - 24);
        int entryCount = 0;
        while (pairs.hasRemaining()) {
            entryCount++;
            if (pairs.remaining() >= 8) {
                long lenLong = pairs.getLong();
                if (lenLong < 4 || lenLong > 2147483647L) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("APK Signing Block entry #");
                    stringBuilder.append(entryCount);
                    stringBuilder.append(" size out of range: ");
                    stringBuilder.append(lenLong);
                    throw new PolicyNotFoundException(stringBuilder.toString());
                }
                int len = (int) lenLong;
                int nextEntryPos = pairs.position() + len;
                if (len > pairs.remaining()) {
                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("APK Signing Block entry #");
                    stringBuilder2.append(entryCount);
                    stringBuilder2.append(" size out of range: ");
                    stringBuilder2.append(len);
                    stringBuilder2.append(", available: ");
                    stringBuilder2.append(pairs.remaining());
                    throw new PolicyNotFoundException(stringBuilder2.toString());
                } else if (pairs.getInt() == blockId) {
                    return getByteBuffer(pairs, len - 4);
                } else {
                    pairs.position(nextEntryPos);
                }
            } else {
                StringBuilder stringBuilder3 = new StringBuilder();
                stringBuilder3.append("Insufficient data to read size of APK Signing Block entry #");
                stringBuilder3.append(entryCount);
                throw new PolicyNotFoundException(stringBuilder3.toString());
            }
        }
        throw new PolicyNotFoundException("No APK Signature Scheme v2 block in APK Signing Block");
    }

    private static void checkByteOrderLittleEndian(ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer byte order must be little endian");
        }
    }
}
