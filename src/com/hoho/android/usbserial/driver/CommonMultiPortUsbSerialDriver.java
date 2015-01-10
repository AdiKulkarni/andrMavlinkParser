package com.hoho.android.usbserial.driver;

import java.io.IOException;
import java.security.AccessControlException;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

public abstract class CommonMultiPortUsbSerialDriver extends CommonUsbSerialDriver {

    private final UsbSerialPort mPorts[];

    public CommonMultiPortUsbSerialDriver(UsbDevice device,
            int portCount) {
        super(device);
        mPorts = new UsbSerialPort[portCount];
    }

    // Calls open if the driver is not yet opened.
    // Is usually called by the port implementation as the
    // first action in its initPortSepcific method.
    synchronized void ensureIsOpen(UsbManager usbManager) throws IOException, AccessControlException {
        if (!isOpen()) {
            open(usbManager);
        }
    }

    // Calls close if there are no more open ports.
    // Is usually called by the port implementation
    // in its portClosed method.
    synchronized void closeIfNoPortsOpen() {
        if (isOpen()) {
            boolean hasOpenPorts = false;
            for (int i = 0; !hasOpenPorts && (i < mPorts.length); ++i) {
                if ((mPorts[i] != null) && (mPorts[i].isOpen())) {
                    hasOpenPorts = true;
                }
            }
    
            if (!hasOpenPorts) {
                close();
            }
        }
    }

    abstract protected UsbSerialPort createPortInstance(int portIdx);

    @Override
    public int getPortCount() {
        return mPorts.length;
    }

    @Override
    public UsbSerialPort getPort(int i) throws IndexOutOfBoundsException {
        if ((i >= 0) && i < (mPorts.length)) {
            UsbSerialPort port = mPorts[i];
            if (port == null) {
                port = createPortInstance(i);
                mPorts[i] = port;
            }
            return port;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

}
