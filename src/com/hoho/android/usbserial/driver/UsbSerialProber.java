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

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class which finds compatible {@link UsbDevice}s and creates
 * {@link UsbSerialDriver} instances.
 *
 * <p/>
 * You don't need a Prober to use the rest of the library: it is perfectly
 * acceptable to instantiate driver instances manually. The Prober simply
 * provides convenience functions.
 *
 * <p/>
 * For most drivers, the corresponding {@link #probe(UsbManager, UsbDevice)}
 * method will either return an empty list (device unknown / unsupported) or a
 * singleton list. However, multi-port drivers may return multiple instances.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public enum UsbSerialProber {

    // TODO(mikey): Too much boilerplate.

    /**
     * Prober for {@link FtdiSerialDriver}.
     *
     * @see FtdiSerialDriver
     */
    FTDI_SERIAL {
        @Override
        public UsbSerialDriver getDevice(final UsbDevice usbDevice) {
            if (testIfSupported(usbDevice)) {
                return new FtdiSerialDriver(usbDevice);
            } else {
                return null;
            }
        }

        @Override
        public boolean testIfSupported(final UsbDevice usbDevice) {
            return testIfInSupportedDevicesMap(usbDevice, FtdiSerialDriver.getSupportedDevices());
        }
    },

    CDC_ACM_SERIAL {
        @Override
        public UsbSerialDriver getDevice(UsbDevice usbDevice) {
            if (testIfSupported(usbDevice)) {
               return new CdcAcmSerialDriver(usbDevice);
            } else {
                return null;
            }
        }

        @Override
        public boolean testIfSupported(final UsbDevice usbDevice) {
            return testIfInSupportedDevicesMap(usbDevice, CdcAcmSerialDriver.getSupportedDevices());
        }
    },

    SILAB_SERIAL {
        @Override
        public UsbSerialDriver getDevice(final UsbDevice usbDevice) {
            if (testIfSupported(usbDevice)) {
                return new Cp21xxSerialDriver(usbDevice);
            } else {
                return null;
            }
        }

        @Override
        public boolean testIfSupported(final UsbDevice usbDevice) {
            return testIfInSupportedDevicesMap(usbDevice, Cp21xxSerialDriver.getSupportedDevices());
        }
    },

    PROLIFIC_SERIAL {
        @Override
        public UsbSerialDriver getDevice(final UsbDevice usbDevice) {
            if (testIfSupported(usbDevice)) {
                return new ProlificSerialDriver(usbDevice);
            } else {
                return null;
            }
        }

        @Override
        public boolean testIfSupported(final UsbDevice usbDevice) {
            return testIfInSupportedDevicesMap(usbDevice, ProlificSerialDriver.getSupportedDevices());
        }
    };

    /**
     * Builds a new {@link UsbSerialDriver} instance from the raw device, or
     * returns <code>null</code> if it could not be built (for example, if the
     * probe failed).
     *
     * @param usbDevice the raw {@link UsbDevice} to use
     * @return the first available {@link UsbSerialDriver}, or {@code null} if
     *         no device could be acquired
     */
    public abstract UsbSerialDriver getDevice(final UsbDevice usbDevice);

    /**
     * Check if a USB device is supported.
     * 
     * @param usbDevice the raw {@link UsbDevice} to use
     * @return {@code true} if the device supported
     */
    public abstract boolean testIfSupported(final UsbDevice usbDevice);

    /**
     * Creates and returns a new {@link UsbSerialDriver} instance for the first
     * compatible {@link UsbDevice} found on the bus.  If none are found,
     * returns {@code null}.
     *
     * <p/>
     * The order of devices is undefined, therefore if there are multiple
     * devices on the bus, the chosen device may not be predictable (clients
     * should use {@link #findAllDevices(UsbManager)} instead).
     *
     * @param usbManager the {@link UsbManager} to use.
     * @return the first available {@link UsbSerialDriver}, or {@code null} if
     *         none are available.
     */
    public static UsbSerialDriver findFirstDevice(final UsbManager usbManager) {
        for (final UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            final UsbSerialDriver probedDevice = probeSingleDevice(usbDevice);
            if (probedDevice != null) {
                return probedDevice;
            }
        }
        return null;
    }

    /**
     * Special method for testing a specific device for driver support,
     * returning any compatible driver(s).
     *
     * Clients should ordinarily use {@link #findAllDevices(UsbManager)}, which
     * operates against the entire bus of devices. This method is useful when
     * testing against only a single target is desired.
     *
     * @param usbDevice the device to test against.
     * @return a list containing zero or more {@link UsbSerialDriver} instances.
     */
    public static UsbSerialDriver probeSingleDevice(final UsbDevice usbDevice) {
        for (final UsbSerialProber prober : values()) {
            final UsbSerialDriver probedDevice = prober.getDevice(usbDevice);
            if (probedDevice != null) {
                return probedDevice;
            }
        }
        return null;
    }

    /**
     * Special method for testing a specific device for driver support.
     * 
     * @param usbDevice the device to test against.
     * @return {@code true} if the device supported
     */
    public static boolean isDeviceSupported(final UsbDevice usbDevice) {
        for (final UsbSerialProber prober : values()) {
            final boolean supported = prober.testIfSupported(usbDevice);
            if (supported) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a new {@link UsbSerialDriver} instance for all compatible
     * {@link UsbDevice}s found on the bus. If no compatible devices are found,
     * the list will be empty.
     *
     * @param usbManager the {@link UsbManager} to use.
     * @return a list containing zero or more {@link UsbSerialDriver} instances.
     */
    public static List<UsbSerialDriver> findAllDevices(final UsbManager usbManager) {
        final List<UsbSerialDriver> result = new ArrayList<UsbSerialDriver>();

        // For each UsbDevice, call probe() for each prober.
        for (final UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            final UsbSerialDriver usbSerialDriver = probeSingleDevice(usbDevice);
            if (usbSerialDriver != null) {
                result.add(usbSerialDriver);
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if the given device is found in the driver's
     * vendor/product map.
     *
     * @param usbDevice the device to test
     * @param supportedDevices map of vendor IDs to product ID(s)
     * @return {@code true} if supported
     */
    private static boolean testIfInSupportedDevicesMap(final UsbDevice usbDevice,
            final Map<Integer, int[]> supportedDevices) {
        final int[] supportedProducts = supportedDevices.get(
                Integer.valueOf(usbDevice.getVendorId()));
        if (supportedProducts == null) {
            return false;
        }

        final int productId = usbDevice.getProductId();
        for (int supportedProductId : supportedProducts) {
            if (productId == supportedProductId) {
                return true;
            }
        }
        return false;
    }

}
