package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * USB CDC/ACM serial driver implementation.
 *
 * @author mike wakerly (opensource@hoho.com)
 * @see <a
 *      href="http://www.usb.org/developers/devclass_docs/usbcdc11.pdf">Universal
 *      Serial Bus Class Definitions for Communication Devices, v1.1</a>
 */
public class CdcAcmSerialDriver extends CommonSinglePortUsbSerialDriver {

    private final String TAG = CdcAcmSerialDriver.class.getSimpleName();

    protected UsbEndpoint mControlEndpoint;
    protected UsbEndpoint mReadEndpoint;
    protected UsbEndpoint mWriteEndpoint;

    private boolean mRts = false;
    private boolean mDtr = false;

    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_RT_ACM = UsbConstants.USB_DIR_OUT
            | UsbConstants.USB_TYPE_CLASS
            | USB_RECIP_INTERFACE;

    private static final int SET_LINE_CODING = 0x20;  // USB CDC 1.1 section 6.2
//    private static final int GET_LINE_CODING = 0x21;
    private static final int SET_CONTROL_LINE_STATE = 0x22;
//    private static final int SEND_BREAK = 0x23;

    protected static final int STATUS_FLAG_CD = 0x01;
    protected static final int STATUS_FLAG_DSR = 0x02;
    protected static final int STATUS_FLAG_RI = 0x08;
    protected static final int STATUS_FLAG_CTS = 0x80;

    protected int mBaudRate = -1, mDataBits = -1, mStopBits = -1, mParity = -1;

    public CdcAcmSerialDriver(UsbDevice device) {
        super(device);
    }

    @Override
    protected void initDriverSpecific(UsbManager usbManager) throws IOException, AccessControlException {
        initEndpoints();
    }

    @Override
    protected void deinitDriverSpecific() throws IOException {
    }

    protected int sendAcmControlMessage(int request, int value, byte[] buf) {
        return mConnection.controlTransfer(
                USB_RT_ACM, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }

    protected void initEndpoints() throws IOException {
        Log.d(TAG, "Claiming control interface.");
        UsbInterface controlInterface = mDevice.getInterface(0);
        Log.d(TAG, "Control iface=" + controlInterface);
        // class should be USB_CLASS_COMM

        if (!mConnection.claimInterface(controlInterface, true)) {
            throw new IOException("Could not claim control interface.");
        }
        mControlEndpoint = controlInterface.getEndpoint(0);
        Log.d(TAG, "Control endpoint direction: " + mControlEndpoint.getDirection());

        Log.d(TAG, "Claiming data interface.");
        UsbInterface dataInterface = mDevice.getInterface(1);
        Log.d(TAG, "data iface=" + dataInterface);
        // class should be USB_CLASS_CDC_DATA

        if (!mConnection.claimInterface(dataInterface, true)) {
            throw new IOException("Could not claim data interface.");
        }
        mReadEndpoint = dataInterface.getEndpoint(1);
        Log.d(TAG, "Read endpoint direction: " + mReadEndpoint.getDirection());
        mWriteEndpoint = dataInterface.getEndpoint(0);
        Log.d(TAG, "Write endpoint direction: " + mWriteEndpoint.getDirection());
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

    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
        if ((mBaudRate == baudRate) && (mDataBits == dataBits)
                && (mStopBits == stopBits) && (mParity == parity)) {
            // Make sure no action is performed if there is nothing to change
            return;
        }

        byte stopBitsByte;
        switch (stopBits) {
            case STOPBITS_1: stopBitsByte = 0; break;
            case STOPBITS_1_5: stopBitsByte = 1; break;
            case STOPBITS_2: stopBitsByte = 2; break;
            default: throw new IllegalArgumentException("Bad value for stopBits: " + stopBits);
        }

        byte parityBitesByte;
        switch (parity) {
            case PARITY_NONE: parityBitesByte = 0; break;
            case PARITY_ODD: parityBitesByte = 1; break;
            case PARITY_EVEN: parityBitesByte = 2; break;
            case PARITY_MARK: parityBitesByte = 3; break;
            case PARITY_SPACE: parityBitesByte = 4; break;
            default: throw new IllegalArgumentException("Bad value for parity: " + parity);
        }

        byte[] msg = {
                (byte) ( baudRate & 0xff),
                (byte) ((baudRate >> 8 ) & 0xff),
                (byte) ((baudRate >> 16) & 0xff),
                (byte) ((baudRate >> 24) & 0xff),
                stopBitsByte,
                parityBitesByte,
                (byte) dataBits};
        sendAcmControlMessage(SET_LINE_CODING, 0, msg);

        mBaudRate = baudRate;
        mDataBits = dataBits;
        mStopBits = stopBits;
        mParity = parity;
    }

    @Override
    public boolean getCD() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getCTS() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getDSR() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getDTR() throws IOException {
        return mDtr;
    }

    @Override
    public void setDTR(boolean value) throws IOException {
        mDtr = value;
        setDtrRts();
    }

    @Override
    public boolean getRI() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getRTS() throws IOException {
        return mRts;
    }

    @Override
    public void setRTS(boolean value) throws IOException {
        mRts = value;
        setDtrRts();
    }

    @Override
    public String getShortDeviceName() {
        String shortDeviceName = null;

        switch (mDevice.getVendorId()) {
        case UsbId.VENDOR_ARDUINO:
            shortDeviceName = "Arduino";
            break;

        case UsbId.VENDOR_VAN_OOIJEN_TECH:
            if (mDevice.getProductId() == UsbId.VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL) {
                shortDeviceName = "Teensyduino";
            }
            break;

        case UsbId.VENDOR_LEAFLABS:
            if (mDevice.getProductId() == UsbId.LEAFLABS_MAPLE) {
                shortDeviceName = "Maple";
            }
            break;
        }

        if (shortDeviceName == null) {
            shortDeviceName = "CDC";
        }

        return shortDeviceName;
    }

    private void setDtrRts() {
        int value = (mRts ? 0x2 : 0) | (mDtr ? 0x1 : 0);
        sendAcmControlMessage(SET_CONTROL_LINE_STATE, value, null);
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_ARDUINO),
                new int[] {
                        UsbId.ARDUINO_UNO,
                        UsbId.ARDUINO_UNO_R3,
                        UsbId.ARDUINO_MEGA_2560,
                        UsbId.ARDUINO_MEGA_2560_R3,
                        UsbId.ARDUINO_SERIAL_ADAPTER,
                        UsbId.ARDUINO_SERIAL_ADAPTER_R3,
                        UsbId.ARDUINO_MEGA_ADK,
                        UsbId.ARDUINO_MEGA_ADK_R3,
                        UsbId.ARDUINO_LEONARDO,
                });
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_VAN_OOIJEN_TECH),
                new int[] {
                    UsbId.VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL,
                });
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_ATMEL),
                new int[] {
                    UsbId.ATMEL_LUFA_CDC_DEMO_APP,
                });
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_LEAFLABS),
                new int[] {
                    UsbId.LEAFLABS_MAPLE,
                });
        return supportedDevices;
    }

}
