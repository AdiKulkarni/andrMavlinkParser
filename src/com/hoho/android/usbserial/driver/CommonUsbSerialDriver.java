/* Copyright 2013 Google Inc.
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

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.security.AccessControlException;

/**
 * A base class shared by several driver implementations.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
abstract class CommonUsbSerialDriver implements UsbSerialDriver {

    public static final int DEFAULT_WRITE_TIMEOUT = 1000;

    protected final UsbDevice mDevice;
    protected UsbDeviceConnection mConnection;

    public CommonUsbSerialDriver(UsbDevice device) {
        mDevice = device;
    }

    protected final void open(UsbManager usbManager) throws IOException, AccessControlException {
        if (mConnection != null) {
            throw new IllegalStateException("Driver already open");
        }
        mConnection = openDeviceConnection(usbManager, mDevice);
    }

    protected final void close() {
        if (mConnection == null) {
            throw new IllegalStateException("Driver not open");
        }

        mConnection.close();
        mConnection = null;
    }

    protected final boolean isOpen() {
        return (mConnection != null);
    }

    static UsbDeviceConnection openDeviceConnection(UsbManager usbManager, UsbDevice device) throws IOException, AccessControlException {
        if (!usbManager.hasPermission(device)) {
            throw new AccessControlException("No permission to access USB device " + device);
        }
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            throw new IOException("Could not open USB device " + device);
        }
        return connection;
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

}
