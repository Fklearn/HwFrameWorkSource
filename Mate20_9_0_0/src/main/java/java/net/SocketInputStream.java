package java.net;

import dalvik.system.BlockGuard;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import sun.net.ConnectionResetException;

class SocketInputStream extends FileInputStream {
    private boolean closing = false;
    private boolean eof;
    private AbstractPlainSocketImpl impl = null;
    private Socket socket = null;
    private byte[] temp;

    private native int socketRead0(FileDescriptor fileDescriptor, byte[] bArr, int i, int i2, int i3) throws IOException;

    SocketInputStream(AbstractPlainSocketImpl impl) throws IOException {
        super(impl.getFileDescriptor());
        this.impl = impl;
        this.socket = impl.getSocket();
    }

    public final FileChannel getChannel() {
        return null;
    }

    private int socketRead(FileDescriptor fd, byte[] b, int off, int len, int timeout) throws IOException {
        return socketRead0(fd, b, off, len, timeout);
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int length) throws IOException {
        return read(b, off, length, this.impl.getTimeout());
    }

    /* JADX WARNING: Removed duplicated region for block: B:20:0x004f  */
    /* JADX WARNING: Removed duplicated region for block: B:40:0x00a9  */
    /* JADX WARNING: Removed duplicated region for block: B:31:0x0088  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    int read(byte[] b, int off, int length, int timeout) throws IOException {
        if (this.eof) {
            return -1;
        }
        if (this.impl.isConnectionReset()) {
            throw new SocketException("Connection reset");
        } else if (length > 0 && off >= 0 && length <= b.length - off) {
            boolean gotReset = false;
            FileDescriptor fd = this.impl.acquireFD();
            int n;
            try {
                BlockGuard.getThreadPolicy().onNetwork();
                n = socketRead(fd, b, off, length, timeout);
                if (n > 0) {
                    return n;
                }
                this.impl.releaseFD();
                if (gotReset) {
                    this.impl.setConnectionResetPending();
                    this.impl.acquireFD();
                    try {
                        n = socketRead(fd, b, off, length, timeout);
                        if (n > 0) {
                            return n;
                        }
                        this.impl.releaseFD();
                    } catch (ConnectionResetException e) {
                    } finally {
                        n = this.impl;
                        n.releaseFD();
                    }
                }
                if (this.impl.isClosedOrPending()) {
                    if (this.impl.isConnectionResetPending()) {
                        this.impl.setConnectionReset();
                    }
                    if (this.impl.isConnectionReset()) {
                        throw new SocketException("Connection reset");
                    }
                    this.eof = true;
                    return -1;
                }
                throw new SocketException("Socket closed");
            } catch (ConnectionResetException e2) {
                gotReset = true;
            } finally {
                n = this.impl;
                n.releaseFD();
                if (gotReset) {
                }
                if (this.impl.isClosedOrPending()) {
                }
            }
        } else if (length == 0) {
            return 0;
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("length == ");
            stringBuilder.append(length);
            stringBuilder.append(" off == ");
            stringBuilder.append(off);
            stringBuilder.append(" buffer length == ");
            stringBuilder.append(b.length);
            throw new ArrayIndexOutOfBoundsException(stringBuilder.toString());
        }
    }

    public int read() throws IOException {
        if (this.eof) {
            return -1;
        }
        this.temp = new byte[1];
        if (read(this.temp, 0, 1) <= 0) {
            return -1;
        }
        return this.temp[0] & 255;
    }

    public long skip(long numbytes) throws IOException {
        if (numbytes <= 0) {
            return 0;
        }
        long n = numbytes;
        int buflen = (int) Math.min(1024, n);
        byte[] data = new byte[buflen];
        while (n > 0) {
            int r = read(data, 0, (int) Math.min((long) buflen, n));
            if (r < 0) {
                break;
            }
            n -= (long) r;
        }
        return numbytes - n;
    }

    public int available() throws IOException {
        if (this.eof) {
            return 0;
        }
        return this.impl.available();
    }

    public void close() throws IOException {
        if (!this.closing) {
            this.closing = true;
            if (this.socket == null) {
                this.impl.close();
            } else if (!this.socket.isClosed()) {
                this.socket.close();
            }
            this.closing = false;
        }
    }

    void setEOF(boolean eof) {
        this.eof = eof;
    }

    protected void finalize() {
    }
}
