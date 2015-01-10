package com.hoho.android.usbserial.driver;

import java.io.IOException;
import java.security.AccessControlException;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

public abstract class CommonSinglePortUsbSerialDriver extends CommonUsbSerialPort implements UsbSerialDriver {

    protected final UsbDevice mDevice;
    protected UsbDeviceConnection mConnection;

    public CommonSinglePortUsbSerialDriver(UsbDevice device) {
        mDevice = device;
    }

 // Implementors implement their port specific initialization
    // in this method.
    protected abstract void initDriverSpecific(UsbManager usbManager)
            throws IOException, AccessControlException;

    // Implementors implement their port specific deinitialization
    // in this method.
    protected abstract void deinitDriverSpecific() throws IOException;

    @Override
    protected final void initPortSepcific(UsbManager usbManager) throws IOException, AccessControlException {
        mConnection = CommonUsbSerialDriver.openDeviceConnection(usbManager, mDevice);
        initDriverSpecific(usbManager);
    }

    @Override
    protected final void deinitPortSpecific() throws IOException {
        try {
            deinitDriverSpecific();
            mConnection.close();
            mConnection = null;
        } finally {
            if (mConnection != null) {
                try {
                    mConnection.close();
                    mConnection = null;
                } catch (Exception e) {
                }
            }
        }
    }
    

    @Override
    protected void portClosed() {
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

    @Override
    public int getPortCount() {
        return 1;
    }

    @Override
    public UsbSerialPort getPort(int i) throws IndexOutOfBoundsException {
        if (i == 0) {
            return this;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

}
