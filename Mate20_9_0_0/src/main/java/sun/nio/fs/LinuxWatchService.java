package sun.nio.fs;

import com.sun.nio.file.SensitivityWatchEventModifier;
import dalvik.system.CloseGuard;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import sun.misc.Unsafe;

class LinuxWatchService extends AbstractWatchService {
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private final Poller poller;

    private static class LinuxWatchKey extends AbstractWatchKey {
        private final int ifd;
        private volatile int wd;

        LinuxWatchKey(UnixPath dir, LinuxWatchService watcher, int ifd, int wd) {
            super(dir, watcher);
            this.ifd = ifd;
            this.wd = wd;
        }

        int descriptor() {
            return this.wd;
        }

        void invalidate(boolean remove) {
            if (remove) {
                try {
                    LinuxWatchService.inotifyRmWatch(this.ifd, this.wd);
                } catch (UnixException e) {
                }
            }
            this.wd = -1;
        }

        public boolean isValid() {
            return this.wd != -1;
        }

        public void cancel() {
            if (isValid()) {
                ((LinuxWatchService) watcher()).poller.cancel(this);
            }
        }
    }

    private static class Poller extends AbstractPoller {
        private static final int BUFFER_SIZE = 8192;
        private static final int IN_ATTRIB = 4;
        private static final int IN_CREATE = 256;
        private static final int IN_DELETE = 512;
        private static final int IN_IGNORED = 32768;
        private static final int IN_MODIFY = 2;
        private static final int IN_MOVED_FROM = 64;
        private static final int IN_MOVED_TO = 128;
        private static final int IN_Q_OVERFLOW = 16384;
        private static final int IN_UNMOUNT = 8192;
        private static final int OFFSETOF_LEN = offsets[3];
        private static final int OFFSETOF_MASK = offsets[1];
        private static final int OFFSETOF_NAME = offsets[4];
        private static final int OFFSETOF_WD = offsets[0];
        private static final int SIZEOF_INOTIFY_EVENT = LinuxWatchService.eventSize();
        private static final int[] offsets = LinuxWatchService.eventOffsets();
        private final long address;
        private final UnixFileSystem fs;
        private final CloseGuard guard = CloseGuard.get();
        private final int ifd;
        private final int[] socketpair;
        private final LinuxWatchService watcher;
        private final Map<Integer, LinuxWatchKey> wdToKey;

        Poller(UnixFileSystem fs, LinuxWatchService watcher, int ifd, int[] sp) {
            this.fs = fs;
            this.watcher = watcher;
            this.ifd = ifd;
            this.socketpair = sp;
            this.wdToKey = new HashMap();
            this.address = LinuxWatchService.unsafe.allocateMemory(8192);
            this.guard.open("close");
        }

        void wakeup() throws IOException {
            try {
                UnixNativeDispatcher.write(this.socketpair[1], this.address, 1);
            } catch (UnixException x) {
                throw new IOException(x.errorString());
            }
        }

        Object implRegister(Path obj, Set<? extends Kind<?>> events, Modifier... modifiers) {
            int access$500;
            UnixPath dir = (UnixPath) obj;
            int mask = 0;
            for (Kind<?> event : events) {
                if (event == StandardWatchEventKinds.ENTRY_CREATE) {
                    mask |= 384;
                } else if (event == StandardWatchEventKinds.ENTRY_DELETE) {
                    mask |= 576;
                } else if (event == StandardWatchEventKinds.ENTRY_MODIFY) {
                    mask |= 6;
                }
            }
            if (modifiers.length > 0) {
                for (Modifier modifier : modifiers) {
                    if (modifier == null) {
                        return new NullPointerException();
                    }
                    if (!(modifier instanceof SensitivityWatchEventModifier)) {
                        return new UnsupportedOperationException("Modifier not supported");
                    }
                }
            }
            try {
                if (!UnixFileAttributes.get(dir, true).isDirectory()) {
                    return new NotDirectoryException(dir.getPathForExceptionMessage());
                }
                NativeBuffer buffer;
                try {
                    buffer = NativeBuffers.asNativeBuffer(dir.getByteArrayForSysCalls());
                    access$500 = LinuxWatchService.inotifyAddWatch(this.ifd, buffer.address(), mask);
                    buffer.release();
                    buffer = (LinuxWatchKey) this.wdToKey.get(Integer.valueOf(access$500));
                    if (buffer == null) {
                        buffer = new LinuxWatchKey(dir, this.watcher, this.ifd, access$500);
                        this.wdToKey.put(Integer.valueOf(access$500), buffer);
                    }
                    return buffer;
                } catch (UnixException x) {
                    if (x.errno() == UnixConstants.ENOSPC) {
                        return new IOException("User limit of inotify watches reached");
                    }
                    return x.asIOException(dir);
                } catch (Throwable th) {
                    buffer.release();
                }
            } catch (UnixException x2) {
                return x2.asIOException(dir);
            }
        }

        void implCancelKey(WatchKey obj) {
            LinuxWatchKey key = (LinuxWatchKey) obj;
            if (key.isValid()) {
                this.wdToKey.remove(Integer.valueOf(key.descriptor()));
                key.invalidate(true);
            }
        }

        void implCloseAll() {
            this.guard.close();
            for (Entry<Integer, LinuxWatchKey> entry : this.wdToKey.entrySet()) {
                ((LinuxWatchKey) entry.getValue()).invalidate(true);
            }
            this.wdToKey.clear();
            LinuxWatchService.unsafe.freeMemory(this.address);
            UnixNativeDispatcher.close(this.socketpair[0]);
            UnixNativeDispatcher.close(this.socketpair[1]);
            UnixNativeDispatcher.close(this.ifd);
        }

        protected void finalize() throws Throwable {
            try {
                if (this.guard != null) {
                    this.guard.warnIfOpen();
                }
                close();
            } finally {
                super.finalize();
            }
        }

        public void run() {
            while (true) {
                int i;
                int nReady;
                int bytesRead;
                int i2;
                try {
                    i = 0;
                    nReady = LinuxWatchService.poll(this.ifd, this.socketpair[0]);
                    bytesRead = UnixNativeDispatcher.read(this.ifd, this.address, 8192);
                } catch (UnixException x) {
                    if (x.errno() != UnixConstants.EAGAIN) {
                        i2 = bytesRead;
                        throw x;
                    }
                } catch (UnixException x2) {
                    if (x2.errno() == UnixConstants.EAGAIN) {
                        bytesRead = 0;
                    } else {
                        throw x2;
                    }
                } catch (UnixException x22) {
                    x22.printStackTrace();
                    return;
                }
                int bytesRead2 = bytesRead;
                if (nReady > 1 || (nReady == 1 && bytesRead2 == 0)) {
                    UnixNativeDispatcher.read(this.socketpair[0], this.address, 8192);
                    if (processRequests()) {
                        return;
                    }
                }
                UnixException x222 = null;
                while (x222 < bytesRead2) {
                    int wd;
                    long event;
                    long event2 = this.address + ((long) x222);
                    int wd2 = LinuxWatchService.unsafe.getInt(((long) OFFSETOF_WD) + event2);
                    int mask = LinuxWatchService.unsafe.getInt(((long) OFFSETOF_MASK) + event2);
                    int len = LinuxWatchService.unsafe.getInt(((long) OFFSETOF_LEN) + event2);
                    UnixPath name = null;
                    if (len > 0) {
                        int actual = len;
                        while (actual > 0) {
                            if (LinuxWatchService.unsafe.getByte(((((long) OFFSETOF_NAME) + event2) + ((long) actual)) - 1) != (byte) 0) {
                                break;
                            }
                            actual--;
                        }
                        if (actual > 0) {
                            byte[] buf = new byte[actual];
                            int i3 = i;
                            while (i3 < actual) {
                                wd = wd2;
                                i2 = bytesRead2;
                                event = event2;
                                buf[i3] = LinuxWatchService.unsafe.getByte((((long) OFFSETOF_NAME) + event2) + ((long) i3));
                                i3++;
                                wd2 = wd;
                                bytesRead2 = i2;
                                event2 = event;
                            }
                            wd = wd2;
                            i2 = bytesRead2;
                            event = event2;
                            name = new UnixPath(this.fs, buf);
                            processEvent(wd, mask, name);
                            x222 += SIZEOF_INOTIFY_EVENT + len;
                            bytesRead2 = i2;
                            i = 0;
                        }
                    }
                    wd = wd2;
                    i2 = bytesRead2;
                    event = event2;
                    processEvent(wd, mask, name);
                    x222 += SIZEOF_INOTIFY_EVENT + len;
                    bytesRead2 = i2;
                    i = 0;
                }
            }
        }

        private Kind<?> maskToEventKind(int mask) {
            if ((mask & 2) > 0) {
                return StandardWatchEventKinds.ENTRY_MODIFY;
            }
            if ((mask & 4) > 0) {
                return StandardWatchEventKinds.ENTRY_MODIFY;
            }
            if ((mask & 256) > 0) {
                return StandardWatchEventKinds.ENTRY_CREATE;
            }
            if ((mask & 128) > 0) {
                return StandardWatchEventKinds.ENTRY_CREATE;
            }
            if ((mask & 512) > 0) {
                return StandardWatchEventKinds.ENTRY_DELETE;
            }
            if ((mask & 64) > 0) {
                return StandardWatchEventKinds.ENTRY_DELETE;
            }
            return null;
        }

        private void processEvent(int wd, int mask, UnixPath name) {
            if ((mask & 16384) > 0) {
                for (Entry<Integer, LinuxWatchKey> entry : this.wdToKey.entrySet()) {
                    ((LinuxWatchKey) entry.getValue()).signalEvent(StandardWatchEventKinds.OVERFLOW, null);
                }
                return;
            }
            LinuxWatchKey key = (LinuxWatchKey) this.wdToKey.get(Integer.valueOf(wd));
            if (key != null) {
                if ((32768 & mask) > 0) {
                    this.wdToKey.remove(Integer.valueOf(wd));
                    key.invalidate(false);
                    key.signal();
                } else if (name != null) {
                    Kind<?> kind = maskToEventKind(mask);
                    if (kind != null) {
                        key.signalEvent(kind, name);
                    }
                }
            }
        }
    }

    private static native void configureBlocking(int i, boolean z) throws UnixException;

    private static native int[] eventOffsets();

    private static native int eventSize();

    private static native int inotifyAddWatch(int i, long j, int i2) throws UnixException;

    private static native int inotifyInit() throws UnixException;

    private static native void inotifyRmWatch(int i, int i2) throws UnixException;

    private static native int poll(int i, int i2) throws UnixException;

    private static native void socketpair(int[] iArr) throws UnixException;

    LinuxWatchService(UnixFileSystem fs) throws IOException {
        try {
            int ifd = inotifyInit();
            int[] sp = new int[2];
            try {
                configureBlocking(ifd, false);
                socketpair(sp);
                configureBlocking(sp[0], false);
                this.poller = new Poller(fs, this, ifd, sp);
                this.poller.start();
            } catch (UnixException x) {
                UnixNativeDispatcher.close(ifd);
                throw new IOException(x.errorString());
            }
        } catch (UnixException x2) {
            throw new IOException(x2.errno() == UnixConstants.EMFILE ? "User limit of inotify instances reached or too many open files" : x2.errorString());
        }
    }

    WatchKey register(Path dir, Kind<?>[] events, Modifier... modifiers) throws IOException {
        return this.poller.register(dir, events, modifiers);
    }

    void implClose() throws IOException {
        this.poller.close();
    }
}
