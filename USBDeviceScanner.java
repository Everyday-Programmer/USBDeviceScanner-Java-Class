import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.HashMap;
import java.util.Map;

public class USBDeviceScanner {
    private final Context context;
    private final UsbManager usbManager;
    private final Handler mainHandler;

    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbEndpoint usbEndpoint;

    public USBDeviceScanner(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void startScanning() {
        // Find and open the USB device
        findAndOpenUSBDevice();

        // Initialize USB communication
        initializeUSBCommunication();
    }

    public void release() {
        if (usbConnection != null) {
            usbConnection.close();
        }
    }

    private void findAndOpenUSBDevice() {
        HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();
        for (Map.Entry<String, UsbDevice> entry : usbDeviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            // Check if the device is your QR scanner
            if (device.getVendorId() == YOUR_VENDOR_ID && device.getProductId() == YOUR_PRODUCT_ID) {
                usbDevice = device;
                break;
            }
        }

        if (usbDevice != null) {
            UsbDeviceConnection connection = usbManager.openDevice(usbDevice);
            if (connection != null) {
                usbConnection = connection;
            }
        }
    }

    private void initializeUSBCommunication() {
        if (usbConnection != null) {
            UsbInterface usbInterface = usbDevice.getInterface(0);
            if (usbInterface.getEndpointCount() > 0) {
                usbEndpoint = usbInterface.getEndpoint(0);
                // Start reading from the endpoint
                startReadingFromEndpoint();
            }
        }
    }

    private void startReadingFromEndpoint() {
        // Start a thread to read data from the USB endpoint
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[usbEndpoint.getMaxPacketSize()];
            while (true) {
                int bytesRead = usbConnection.bulkTransfer(usbEndpoint, buffer, buffer.length, 1000);
                if (bytesRead > 0) {
                    String qrCodeData = new String(buffer, 0, bytesRead);
                    // Decode the QR code
                    decodeQRCode(qrCodeData);
                }
            }
        });
        thread.start();
    }

    private void decodeQRCode(String qrCodeData) {
        mainHandler.post(() -> {
            MultiFormatReader reader = new MultiFormatReader();
            Result result = null;
            try {
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(qrCodeData));
                result = reader.decode(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (result != null) {
                onQRCodeScanned(result.getText());
            }
        });
    }

    // Listener interface for QR code scanning events
    public interface QRCodeListener {
        void onQRCodeScanned(String qrCodeData);
    }

    private QRCodeListener qrCodeListener;

    public void setQRCodeListener(QRCodeListener listener) {
        qrCodeListener = listener;
    }

    private void onQRCodeScanned(String qrCodeData) {
        if (qrCodeListener != null) {
            qrCodeListener.onQRCodeScanned(qrCodeData);
        }
    }
}
