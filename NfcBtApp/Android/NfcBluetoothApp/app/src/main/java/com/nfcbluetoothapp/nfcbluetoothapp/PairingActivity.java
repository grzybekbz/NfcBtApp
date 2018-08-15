package com.nfcbluetoothapp.nfcbluetoothapp;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;

import java.nio.charset.Charset;

public class PairingActivity extends Activity implements NfcAdapter.CreateNdefMessageCallback,
                                                         NfcAdapter.OnNdefPushCompleteCallback
{
    public static final int BLUETOOTH_DISCOVERY_REQUEST_ID = 42;

    NfcAdapter mNfcAdapter;
    BluetoothAdapter mBluetoothAdapter;
    String macAddress;
    String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pairing);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //-------------------------------------------------------------pobierz adress mac urzadzenia
        macAddress = android.provider.Settings.Secure.getString(this.getContentResolver(),
                                                                "bluetooth_address");

        //-------------------------------------------------------------pobierz nazwe urzadzenia
        deviceName = mBluetoothAdapter.getName();
        if (deviceName == null) deviceName = "Phone";

        mNfcAdapter.setNdefPushMessageCallback(this, this);
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        //-------------------------------------------------------------zapytaj o wlaczenie
        //-------------------------------------------------------------widocznosci Bluetooth
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
        {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30);
            startActivityForResult(intent, BLUETOOTH_DISCOVERY_REQUEST_ID);
        }
    }

    //-----------------------------------------------------------------sprawdz czy widocznosc
    //-----------------------------------------------------------------Bluetooh wlaczona
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BLUETOOTH_DISCOVERY_REQUEST_ID)
        {
            if (resultCode == RESULT_CANCELED)
                finish();
        }
    }

    //-----------------------------------------------------------------wykonaj po wyslaniu przez NFC
    @Override
    public void onNdefPushComplete(NfcEvent event)
    {
        this.finish();
    }

    //-----------------------------------------------------------------zawartosc wiadomosci NDEF
    @Override
    public NdefMessage createNdefMessage(NfcEvent event)
    {
        return new NdefMessage(
                NdefRecord.createMime("application/vnd.bluetooth.ep.oob",
                                      payload(macAddress, deviceName))
        );
    }

    //-----------------------------------------------------------------payload rekordu vnd.bluetooth.ep.oob
    public static byte[] payload(String macAddress, String deviceName)
    {
        // 1 Device Friendly Name Data
        byte[] Dev_Name_Data = deviceName.getBytes(Charset.forName("UTF-8"));
        // 2 Device Friendly Name Data Type
        byte[] Device_Name_Data_T = new byte[1];
        Device_Name_Data_T[0] = (byte)0x09;
        // 3 Device Friendly Name Data Length
        byte[] Device_Name_Data_L = new byte[1];
        Device_Name_Data_L[0] = (byte)(Dev_Name_Data.length + 1);
        // 4 Device MAC Address
        String[] macAddressParts = macAddress.split(":");
        byte[] MAC_Address = new byte[6];
        for (int i = 0; i < 6; ++i){
            Integer hex = Integer.parseInt(macAddressParts[i], 16);
            MAC_Address[i] = hex.byteValue();
        }
        reverse(MAC_Address);
        // 5 Bluetooth OOB data length
        byte BT_OOB_L_Val = (byte)(8 + Device_Name_Data_L.length + Device_Name_Data_T.length +
                Dev_Name_Data.length);

        byte[] BT_OOB_Data_L = new byte[2];
        BT_OOB_Data_L[0] = BT_OOB_L_Val;

        return mergeArrays(BT_OOB_Data_L, MAC_Address, Device_Name_Data_L, Device_Name_Data_T,
                Dev_Name_Data);
    }

    static public byte[] mergeArrays(final byte[] ...arrays )
    {
        int size = 0;
        for ( byte[] a: arrays )
            size += a.length;
        byte[] res = new byte[size];
        int destPos = 0;
        for ( int i = 0; i < arrays.length; ++i )
        {
            if ( i > 0 ) destPos += arrays[i-1].length;
            int length = arrays[i].length;
            System.arraycopy(arrays[i], 0, res, destPos, length);
        }
        return res;
    }

    public static void reverse(byte[] array)
    {
        if (array == null) return;
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i)
        {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            --j;
            ++i;
        }
    }
}
