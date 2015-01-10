package com.hoho.android.usbserial.driver;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.LinkedHashMap;
import java.util.Map;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class Cp21xxSerialDriver extends CommonMultiPortUsbSerialDriver {

    private static final String TAG = Cp21xxSerialDriver.class.getSimpleName();

    public class Cp21xxPort extends CommonUsbSerialPort {

        private static final int DEFAULT_BAUD_RATE = 9600;

        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;

        /*
         * Configuration Request Types
         */
        private static final int REQTYPE_HOST_TO_DEVICE = 0x41;

        /*
         * Configuration Request Codes
         */
        private static final int SILABSER_IFC_ENABLE_REQUEST_CODE = 0x00;
        private static final int SILABSER_SET_BAUDDIV_REQUEST_CODE = 0x01;
        private static final int SILABSER_SET_LINE_CTL_REQUEST_CODE = 0x03;
        private static final int SILABSER_SET_MHS_REQUEST_CODE = 0x07;
        private static final int SILABSER_SET_BAUDRATE = 0x1E;
        private static final int SILABSER_FLUSH_REQUEST_CODE = 0x12;

        private static final int FLUSH_READ_CODE = 0x0a;
        private static final int FLUSH_WRITE_CODE = 0x05;

        /*
         * SILABSER_IFC_ENABLE_REQUEST_CODE
         */
        private static final int UART_ENABLE = 0x0001;
        private static final int UART_DISABLE = 0x0000;

        /*
         * SILABSER_SET_BAUDDIV_REQUEST_CODE
         */
        private static final int BAUD_RATE_GEN_FREQ = 0x384000;

        /*
         * SILABSER_SET_MHS_REQUEST_CODE
         */
//        private static final int MCR_DTR = 0x0001;
//        private static final int MCR_RTS = 0x0002;
        private static final int MCR_ALL = 0x0003;

        private static final int CONTROL_WRITE_DTR = 0x0100;
        private static final int CONTROL_WRITE_RTS = 0x0200;

        private final int mPortIdx;

        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;

        public Cp21xxPort(int portIdx) {
            mPortIdx = portIdx;
        }

        private int setConfigSingle(int request, int value) {
            return mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, request, value, 
                    mPortIdx, null, 0, USB_WRITE_TIMEOUT_MILLIS);
        }

        @Override
        protected void initPortSepcific(UsbManager usbManager) throws IOException, AccessControlException {
            Cp21xxSerialDriver.this.ensureIsOpen(usbManager);

            boolean opened = false;
            try {
                
                UsbInterface usbIface = mDevice.getInterface(mPortIdx);
                if (mConnection.claimInterface(usbIface, true)) {
                    Log.d(TAG, "claimInterface " + mPortIdx + " SUCCESS");
                } else {
                    Log.d(TAG, "claimInterface " + mPortIdx + " FAIL");
                }

                UsbInterface dataIface = mDevice.getInterface(mPortIdx);
                for (int i = 0; i < dataIface.getEndpointCount(); i++) {
                    UsbEndpoint ep = dataIface.getEndpoint(i);
                    if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                            mReadEndpoint = ep;
                        } else {
                            mWriteEndpoint = ep;
                        }
                    }
                }

                setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_ENABLE);
                setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE, MCR_ALL | CONTROL_WRITE_DTR | CONTROL_WRITE_RTS);
                setConfigSingle(SILABSER_SET_BAUDDIV_REQUEST_CODE, BAUD_RATE_GEN_FREQ / DEFAULT_BAUD_RATE);
                //            setParameters(DEFAULT_BAUD_RATE, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY);
                opened = true;
            } finally {
                if (!opened) {
                    try {
                        Cp21xxSerialDriver.this.closeIfNoPortsOpen();
                    } catch (Exception e) {
                    }
                }
            }
        }

        @Override
        protected void deinitPortSpecific() throws IOException {
            setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_DISABLE);
        }

        protected void portClosed() throws IOException {
            Cp21xxSerialDriver.this.closeIfNoPortsOpen();
        }

        @Override
        public int read(final byte[] dest,
                final int timeoutMillis) throws IOException {
            final int count = mConnection.bulkTransfer(mReadEndpoint, dest, dest.length, timeoutMillis);
            return (count < 0) ? 0 : count;
        }

        @Override
        public int write(byte[] src, final int length, int timeoutMillis) throws IOException {
            final int count = mConnection.bulkTransfer(mWriteEndpoint, src, length, timeoutMillis);
            return (count < 0) ? 0 : count;
        }

        private void setBaudRate(int baudRate) throws IOException {
            byte[] data = new byte[] {
                    (byte) ( baudRate & 0xff),
                    (byte) ((baudRate >> 8 ) & 0xff),
                    (byte) ((baudRate >> 16) & 0xff),
                    (byte) ((baudRate >> 24) & 0xff)
            };
            int ret = mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, SILABSER_SET_BAUDRATE,
                    0, mPortIdx, data, 4, USB_WRITE_TIMEOUT_MILLIS);
            if (ret < 0) {
                throw new IOException("Error setting baud rate.");
            }
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, int parity)
                throws IOException {
            setBaudRate(baudRate);

            int configDataBits = 0;
            switch (dataBits) {
            case DATABITS_5:
                configDataBits |= 0x0500;
                break;
            case DATABITS_6:
                configDataBits |= 0x0600;
                break;
            case DATABITS_7:
                configDataBits |= 0x0700;
                break;
            case DATABITS_8:
                configDataBits |= 0x0800;
                break;
            default:
                configDataBits |= 0x0800;
                break;
            }
            setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configDataBits);

            int configParityBits = 0; // PARITY_NONE
            switch (parity) {
            case PARITY_ODD:
                configParityBits |= 0x0010;
                break;
            case PARITY_EVEN:
                configParityBits |= 0x0020;
                break;            
            }
            setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configParityBits);

            int configStopBits = 0;
            switch (stopBits) {
            case STOPBITS_1:
                configStopBits |= 0;
                break;
            case STOPBITS_2:
                configStopBits |= 2;
                break;
            }
            setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configStopBits);
        }

        @Override
        public boolean getCD() throws IOException {
            return false;
        }

        @Override
        public boolean getCTS() throws IOException {
            return false;
        }

        @Override
        public boolean getDSR() throws IOException {
            return false;
        }

        @Override
        public boolean getDTR() throws IOException {
            return true;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
        }

        @Override
        public boolean getRI() throws IOException {
            return false;
        }

        @Override
        public boolean getRTS() throws IOException {
            return true;
        }

        @Override
        public boolean purgeHwBuffers(boolean purgeReadBuffers,
                boolean purgeWriteBuffers) throws IOException {
            int value = (purgeReadBuffers ? FLUSH_READ_CODE : 0)
                    | (purgeWriteBuffers ? FLUSH_WRITE_CODE : 0);

            if (value != 0) {
                setConfigSingle(SILABSER_FLUSH_REQUEST_CODE, value);
            }

            return true;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
        }
    }

    public Cp21xxSerialDriver(UsbDevice device) {
        super(device, getExpectedPortsCount(device));
    }

    @Override
    public String getShortDeviceName() {
        String shortDeviceName = null;

        if (mDevice.getVendorId() == UsbId.VENDOR_SILABS) {
            switch (mDevice.getProductId()) {
            case UsbId.SILABS_CP2102:
                shortDeviceName = "CP2102";
                break;

            case UsbId.SILABS_CP2105:
                shortDeviceName = "CP2105";
                break;

            case UsbId.SILABS_CP2108:
                shortDeviceName = "CP2108";
                break;

            case UsbId.SILABS_CP2110:
                shortDeviceName = "CP2110";
                break;
            }
        }

        if (shortDeviceName == null) {
            shortDeviceName = "CP21xx";
        }

        return shortDeviceName;
    }

    @Override
    protected UsbSerialPort createPortInstance(int portIdx) {
        return new Cp21xxPort(portIdx);
    }

    private static final int getExpectedPortsCount(UsbDevice device) {
        switch (device.getProductId()) {
        case UsbId.SILABS_CP2105:
            return 2;

        case UsbId.SILABS_CP2108:
            return 4;

        default:
            return 1;
        }
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_SILABS),
                new int[] {
            UsbId.SILABS_CP2102,
            UsbId.SILABS_CP2105,
            UsbId.SILABS_CP2108,
            UsbId.SILABS_CP2110
        });
        return supportedDevices;
    }

}
