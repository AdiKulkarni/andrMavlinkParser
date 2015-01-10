/* This library is free software; you can redistribute it and/or
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

/*
 * Ported to usb-serial-for-android
 * by Felix Hädicke <felixhaedicke@web.de>
 *
 * Based on the pyprolific driver written
 * by Emmanuel Blot <emmanuel.blot@free.fr>
 * See https://github.com/eblot/pyftdi
 */

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.AccessControlException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProlificSerialDriver extends CdcAcmSerialDriver {
    private static final int USB_READ_TIMEOUT_MILLIS = 1000;
    private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;

    private static final int PROLIFIC_VENDOR_READ_REQUEST = 0x01;
    private static final int PROLIFIC_VENDOR_WRITE_REQUEST = 0x01;

    private static final int PROLIFIC_VENDOR_OUT_REQTYPE = UsbConstants.USB_DIR_OUT
            | UsbConstants.USB_TYPE_VENDOR;

    private static final int PROLIFIC_VENDOR_IN_REQTYPE = UsbConstants.USB_DIR_IN
            | UsbConstants.USB_TYPE_VENDOR;

    private static final int FLUSH_RX_REQUEST = 0x08;
    private static final int FLUSH_TX_REQUEST = 0x09;

    private static final int STATUS_BUFFER_SIZE = 10;
    private static final int STATUS_BYTE_IDX = 8;

    private static final int DEVICE_TYPE_HX = 0;
    private static final int DEVICE_TYPE_0 = 1;
    private static final int DEVICE_TYPE_1 = 2;

    private int mDeviceType = DEVICE_TYPE_HX;

    private int mStatus = 0;
    private volatile Thread mReadStatusThread = null;
    private final Object mReadStatusThreadLock = new Object();
    boolean mStopReadStatusThread = false;
    private IOException mReadStatusException = null;

    private final String TAG = ProlificSerialDriver.class.getSimpleName();

    private final byte[] inControlTransfer(int requestType, int request,
            int value, int index, int length) throws IOException {
        byte[] buffer = new byte[length];
        int result = mConnection.controlTransfer(requestType, request, value,
                index, buffer, length, USB_READ_TIMEOUT_MILLIS);
        if (result != length) {
            throw new IOException(
                    String.format("ControlTransfer with value 0x%x failed: %d",
                            value, result));
        }
        return buffer;
    }

    private final void outControlTransfer(int requestType, int request,
            int value, int index, byte[] data) throws IOException {
        int length = (data == null) ? 0 : data.length;
        int result = mConnection.controlTransfer(requestType, request, value,
                index, data, length, USB_WRITE_TIMEOUT_MILLIS);
        if (result != length) {
            throw new IOException(
                    String.format("ControlTransfer with value 0x%x failed: %d",
                            value, result));
        }
    }

    private final byte[] vendorIn(int value, int index, int length)
            throws IOException {
        return inControlTransfer(PROLIFIC_VENDOR_IN_REQTYPE,
                PROLIFIC_VENDOR_READ_REQUEST, value, index, length);
    }

    private final void vendorOut(int value, int index, byte[] data)
            throws IOException {
        outControlTransfer(PROLIFIC_VENDOR_OUT_REQTYPE,
                PROLIFIC_VENDOR_WRITE_REQUEST, value, index, data);
    }

    private void doBlackMagic() throws IOException {
        vendorIn(0x8484, 0, 1);
        vendorOut(0x0404, 0, null);
        vendorIn(0x8484, 0, 1);
        vendorIn(0x8383, 0, 1);
        vendorIn(0x8484, 0, 1);
        vendorOut(0x0404, 1, null);
        vendorIn(0x8484, 0, 1);
        vendorIn(0x8383, 0, 1);
        vendorOut(0, 1, null);
        vendorOut(1, 0, null);
        vendorOut(2, (mDeviceType == DEVICE_TYPE_HX) ? 0x44 : 0x24, null);
    }

    private void resetDevice() throws IOException {
        purgeHwBuffers(true, true);
    }

    private final void readStatusThreadFunction() {
        try {
            while (!mStopReadStatusThread) {
                byte[] buffer = new byte[STATUS_BUFFER_SIZE];
                int readBytesCount = mConnection.bulkTransfer(mControlEndpoint,
                        buffer,
                        STATUS_BUFFER_SIZE,
                        500);
                if (readBytesCount > 0) {
                    if (readBytesCount == STATUS_BUFFER_SIZE) {
                        mStatus = buffer[STATUS_BYTE_IDX] & 0xff;
                        Log.i(TAG, "mStatus: " + mStatus);
                    } else {
                        throw new IOException(
                                String.format("Invalid CTS / DSR / CD / RI status buffer received, expected %d bytes, but received %d",
                                        STATUS_BUFFER_SIZE,
                                        readBytesCount));
                    }
                }
            }
        } catch (IOException e) {
            mReadStatusException = e;
        }
    }

    private final int getStatus() throws IOException {
        if ((mReadStatusThread == null) && (mReadStatusException == null)) {
            synchronized (mReadStatusThreadLock) {
                if (mReadStatusThread == null) {
                    byte[] buffer = new byte[STATUS_BUFFER_SIZE];
                    int readBytes = mConnection.bulkTransfer(mControlEndpoint,
                            buffer,
                            STATUS_BUFFER_SIZE,
                            100);
                    if (readBytes != STATUS_BUFFER_SIZE) {
                        Log.w(TAG, "Could not read initial CTS / DSR / CD / RI status");
                    } else {
                        mStatus = buffer[STATUS_BYTE_IDX] & 0xff;
                        Log.i(TAG, "mStatus: " + mStatus);
                    }

                    mReadStatusThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            readStatusThreadFunction();
                        }
                    });
                    mReadStatusThread.setDaemon(true);
                    mReadStatusThread.start();
                }
            }
        }

        /* throw and clear an exception which occured in the status read thread */
        IOException readStatusException = mReadStatusException;
        if (mReadStatusException != null) {
            mReadStatusException = null;
            throw readStatusException;
        }

        return mStatus;
    }

    private final boolean testStatusFlag(int flag) throws IOException {
        return ((getStatus() & flag) == flag);
    }

    private final void detectSubtype() {
        if (mDevice.getDeviceClass() == 0x02) {
            mDeviceType = DEVICE_TYPE_0;
        } else {
            try {
                Method getRawDescriptorsMethod
                    = mConnection.getClass().getMethod("getRawDescriptors");
                byte[] rawDescriptors
                    = (byte[]) getRawDescriptorsMethod.invoke(mConnection);
                byte maxPacketSize0 = rawDescriptors[7];
                if (maxPacketSize0 == 64) {
                    mDeviceType = DEVICE_TYPE_HX;
                } else if ((mDevice.getDeviceClass() == 0x00)
                        || (mDevice.getDeviceClass() == 0xff)) {
                    mDeviceType = DEVICE_TYPE_1;
                } else {
                  Log.w(TAG, "Could not detect PL2303 subtype, "
                      + "Assuming that it is a HX device");
                  mDeviceType = DEVICE_TYPE_HX;
                }
            } catch (NoSuchMethodException e) {
                Log.w(TAG, "Method UsbDeviceConnection.getRawDescriptors, "
                        + "required for PL2303 subtype detection, not "
                        + "available! Assuming that it is a HX device");
                mDeviceType = DEVICE_TYPE_HX;
            } catch (Exception e) {
                Log.e(TAG, "An unexpected exception occured while trying "
                        + "to detect PL2303 subtype", e);
            }
        }
    }

    @Override
    protected void initEndpoints() throws IOException {
        UsbInterface usbInterface = mDevice.getInterface(0);

        if (!mConnection.claimInterface(usbInterface, true)) {
            throw new IOException("Error claiming Prolific interface 0");
        }

        for (int i = 0; i < usbInterface.getEndpointCount(); ++i) {
            UsbEndpoint currentEndpoint = usbInterface.getEndpoint(i);

            switch (currentEndpoint.getType()) {
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                if (currentEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    mReadEndpoint = currentEndpoint;
                } else {
                    mWriteEndpoint = currentEndpoint;
                }
                break;

            case UsbConstants.USB_ENDPOINT_XFER_INT:
                mControlEndpoint = currentEndpoint;
                break;
            }
        }
    }

    public ProlificSerialDriver(UsbDevice device) {
        super(device);
    }

    @Override
    protected void initDriverSpecific(UsbManager usbManager) throws IOException, AccessControlException {
        super.initDriverSpecific(usbManager);

        detectSubtype();
        resetDevice();
        doBlackMagic();
    }

    @Override
    protected void deinitDriverSpecific() throws IOException {
        mStopReadStatusThread = true;
        synchronized (mReadStatusThreadLock) {
            if (mReadStatusThread != null) {
                try {
                    mReadStatusThread.join();
                } catch (Exception e) {
                    Log.w(TAG, "An error occured while waiting for status read thread", e);
                }
            }
        }

        resetDevice();
    }

    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits,
            int parity) throws IOException {
        if ((mBaudRate == baudRate) && (mDataBits == dataBits)
                && (mStopBits == stopBits) && (mParity == parity)) {
            // Make sure no action is performed if there is nothing to change
            return;
        }

        super.setParameters(baudRate, dataBits, stopBits, parity);
        resetDevice();
    }

    @Override
    public boolean getCD() throws IOException {
        return testStatusFlag(STATUS_FLAG_CD);
    }

    @Override
    public boolean getCTS() throws IOException {
        return testStatusFlag(STATUS_FLAG_CTS);
    }

    @Override
    public boolean getDSR() throws IOException {
        return testStatusFlag(STATUS_FLAG_DSR);
    }

    @Override
    public boolean getRI() throws IOException {
        return testStatusFlag(STATUS_FLAG_RI);
    }

    @Override
    public boolean purgeHwBuffers(boolean purgeReadBuffers, boolean purgeWriteBuffers) throws IOException {
        if (purgeReadBuffers) {
            vendorOut(FLUSH_RX_REQUEST, 0, null);
        }

        if (purgeWriteBuffers) {
            vendorOut(FLUSH_TX_REQUEST, 0, null);
        }

        return true;
    }

    @Override
    public String getShortDeviceName() {
        return "PL2303";
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_PROLIFIC),
                new int[] { UsbId.PROLIFIC_PL2303, });
        return supportedDevices;
    }
}
