/* Copyright 2011 Google Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: http://code.google.com/p/usb-serial-for-android/
 */

package com.hoho.android.usbserial.util;

import android.hardware.usb.UsbRequest;
import android.util.Log;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Utility class which services a {@link UsbSerialDriver} in its {@link #run()}
 * method.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialInputOutputManager implements Runnable {

    private static final String TAG = SerialInputOutputManager.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final int READ_WAIT_MILLIS = 2000;
    private static final int WRITE_STEP_TIMEOUT_MILLIS = 200;
    private static final int BUFSIZ = 8192;

    private final UsbSerialPort mPort;

    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);

    // Synchronized by 'mWriteBuffer'
    private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(BUFSIZ);

    private final Object mReadLock = new Object();
    private final Object mWriteLock = new Object();

    private boolean purgingWriteBuffers = false;
    private boolean purgingReadBuffers = false;

    private boolean writing = false;

    private enum State {
        STOPPED,
        RUNNING,
        STOPPING
    }

    // Synchronized by 'this'
    private State mState = State.STOPPED;

    // Synchronized by 'this'
    private Listener mListener;

    public interface Listener {
        /**
         * Called when new incoming data is available.
         */
        public void onNewData(byte[] data);

        /**
         * Called when {@link SerialInputOutputManager#run()} aborts due to an
         * error.
         */
        public void onRunError(Exception e);
    }

    /**
     * Creates a new instance with no listener.
     */
    public SerialInputOutputManager(UsbSerialPort port) {
        this(port, null);
    }

    /**
     * Creates a new instance with the provided listener.
     */
    public SerialInputOutputManager(UsbSerialPort driver, Listener listener) {
        mPort = driver;
        mListener = listener;
    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;
    }

    public synchronized Listener getListener() {
        return mListener;
    }

    public void writeAsync(byte[] data) {
        synchronized (mWriteBuffer) {
            mWriteBuffer.put(data);
        }
    }

    public void writeSync(byte[] data) throws IOException {
        synchronized (mWriteLock) {
            mPort.write(data);
        }
    }

    /**
     * Trigger purging non-transmitted output data and / or non-read input
     * from the read / write buffers. Also performs a purge operation of the device
     * buffers, if the device / driver supports it.
     * @param purgeReadBuffers {@code true} to purge non-transmitted output data.
     * @param purgeWriteBuffers {@code true} to purge non-read input data.
     * @throws IOException if an I/O error occurred.
     */
    public synchronized void purge(boolean purgeReadBuffers,
            boolean purgeWriteBuffers) throws IOException {
        try {
            purgingReadBuffers = purgeReadBuffers;
            purgingWriteBuffers = purgeWriteBuffers;

            if (purgeReadBuffers) {
                synchronized (mReadLock) {
                    mPort.purgeHwBuffers(true, false);
                }
            }

            synchronized (mWriteLock) {
                synchronized (mWriteBuffer) {
                    mPort.purgeHwBuffers(false, true);
                    mWriteBuffer.clear();
                }
            }
        } finally {
            purgingReadBuffers = false;
            purgingWriteBuffers = false;
        }
    }

    /**
     * Wait until all pending write operations are completed.
     */
    public void drain() {
        boolean writeBufferEmpty = false;
        while (!writeBufferEmpty || writing) {
            synchronized (mWriteLock) {
                synchronized (mWriteBuffer) {
                    writeBufferEmpty = (mWriteBuffer.position() <= 0);
                }
            }
        }
    }

    public synchronized void stop() {
        if (getState() == State.RUNNING) {
            Log.i(TAG, "Stop requested");
            mState = State.STOPPING;
        }
    }

    private synchronized State getState() {
        return mState;
    }

    /**
     * Continuously services the read and write buffers until {@link #stop()} is
     * called, or until a driver exception is raised.
     *
     * NOTE(mikey): Uses inefficient read/write-with-timeout.
     * TODO(mikey): Read asynchronously with {@link UsbRequest#queue(ByteBuffer, int)}
     */
    @Override
    public void run() {
        synchronized (this) {
            if (getState() != State.STOPPED) {
                throw new IllegalStateException("Already running.");
            }
            mState = State.RUNNING;
        }

        Log.i(TAG, "Running ..");
        try {
            while (true) {
                if (getState() != State.RUNNING) {
                    Log.i(TAG, "Stopping mState=" + getState());
                    break;
                }
                step();
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            final Listener listener = getListener();
            if (listener != null) {
              listener.onRunError(e);
            }
        } finally {
            synchronized (this) {
                mState = State.STOPPED;
                Log.i(TAG, "Stopped.");
            }
        }
    }

    private void step() throws IOException {
        // Handle incoming data.
        int len;
        synchronized (mReadLock) {
            len = mPort.read(mReadBuffer.array(), READ_WAIT_MILLIS);
        }
        if ((len > 0) && (!purgingReadBuffers)) {
            if (DEBUG) Log.d(TAG, "Read data len=" + len);
            final Listener listener = getListener();
            if (listener != null) {
                final byte[] data = new byte[len];
                mReadBuffer.get(data, 0, len);
                if (!purgingReadBuffers) {
                    listener.onNewData(data);
                }
            }
            mReadBuffer.clear();
        }

        // Handle outgoing data.
        try {
            byte[] outBuff = null;
            synchronized (mWriteBuffer) {
                if (mWriteBuffer.position() > 0) {
                    len = mWriteBuffer.position();
                    outBuff = new byte[len];
                    mWriteBuffer.rewind();
                    mWriteBuffer.get(outBuff, 0, len);
                    mWriteBuffer.clear();
                    writing = true;
                }
            }
            if (outBuff != null) {
                if (DEBUG) {
                    Log.d(TAG, "Writing data len=" + len);
                }
    
                synchronized (mWriteLock) {
                    int writtenBytesCount = 0;
                    while ((writtenBytesCount < outBuff.length) && !purgingWriteBuffers) {
                        int writeRet = mPort.write(outBuff,
                                outBuff.length - writtenBytesCount,
                                WRITE_STEP_TIMEOUT_MILLIS);
                        if (writeRet == 0) {
                            throw new IOException("Could not write data to device");
                        } else if ((writeRet < outBuff.length - writtenBytesCount)
                                && !purgingWriteBuffers) {
                            System.arraycopy(outBuff,
                                    writeRet,
                                    outBuff,
                                    0,
                                    outBuff.length - writtenBytesCount);
                        }
                        writtenBytesCount += writeRet;
                    }
                }
            }
        } finally {
            writing = false;
        }
    }

}
