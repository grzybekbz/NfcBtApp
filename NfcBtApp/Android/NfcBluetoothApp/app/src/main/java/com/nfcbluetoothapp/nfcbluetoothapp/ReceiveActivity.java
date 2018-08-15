package com.nfcbluetoothapp.nfcbluetoothapp;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.annotation.NonNull;

import java.io.UnsupportedEncodingException;
import java.util.Set;

public class ReceiveActivity extends Activity
{
    public static final int BLUETOOTH_ENABLE_REQUEST_ID = 18;
    public static final int WRITE_EXTERNAL_STORAGE_REQUEST_ID = 43;

    private static final int PROGRESS = 1;
    private static final int DONE = 2;
    private static final int CONNECTING_FAILED = 3;
    private static final int SOCKET_CONNECT_FAILED = 4;
    private static final int EXTERNAL_STORAGE_ACCESS_FAILED = 5;
    private static final int FILE_TRANSFER_FAILED = 6;

    private static final int DIALOG_MODE_ERROR = 1;
    private static final int DIALOG_MODE_DONE = 2;
    private static final int DIALOG_MODE_EXIT = 3;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler clientHandler;
    private ProgressBar progress;
    private Client mClient;
    private String deviceName;
    private String fileName;
    private long fileSize;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        //-------------------------------------------------------------zapytaj o uprawnienia
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(ReceiveActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_EXTERNAL_STORAGE_REQUEST_ID);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        createHandler();

        progress = (ProgressBar)findViewById(R.id.progressBar2);

        //-------------------------------------------------------------przycisk anuluj
        final Button cancelButton = (Button)findViewById(R.id.cancelButton2);
        cancelButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mClient.stopSocket();
            }
        });

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction()))
            processIntent(getIntent());

        acceptFileDialog();
    }

    //-----------------------------------------------------------------sprawdz uprawnienia
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case WRITE_EXTERNAL_STORAGE_REQUEST_ID:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    break;
                else
                    messageDialog("Odmowa dostępu do plików\nPowrót!", DIALOG_MODE_EXIT);
            }
        }
    }

    //-----------------------------------------------------------------sprawdz czy bluetooh wlaczone
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BLUETOOTH_ENABLE_REQUEST_ID)
        {
            if (resultCode == RESULT_OK)
                findDevice();
            else if (resultCode == RESULT_CANCELED)
                messageDialog("Aby odebrać plik wlącz Bluetooth\nPowrót!", DIALOG_MODE_EXIT);
        }
    }

    //-----------------------------------------------------------------nowy intent z wiadomoscia NDEF
    @Override
    public void onNewIntent(Intent intent)
    {
        setIntent(intent);
    }

    //-----------------------------------------------------------------odbierz wiadomosc NDEF
    void processIntent(Intent intent)
    {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        NdefRecord[] recs = msg.getRecords();
        try
        {
            if (readText(recs[0]).equals("no8g-Sj5i-i8aw6"))
            {
                deviceName = readText(recs[1]);
                fileName = readText(recs[2]);
                fileSize = Long.parseLong(readText(recs[3]));
            }
            else
                finish();
        }
        catch (UnsupportedEncodingException e)
        {
            android.util.Log.e("NDEF", "Unsupported Encoding", e);
            finish();
        }
    }

    //-----------------------------------------------------------------odczytaj rekord tekstowy NDEF
    private String readText(NdefRecord record) throws UnsupportedEncodingException
    {
        byte[] payload = record.getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int languageCodeLength = payload[0] & 0063;
        return new String(payload, languageCodeLength + 1,
                payload.length - languageCodeLength - 1, textEncoding);
    }

    //-----------------------------------------------------------------okno dialogowe akceptacji pliku
    public void acceptFileDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Odebrac plik:\nNazwa: " + fileName +
                           "\nRozmiar: " + fileSize + " bajtów?")
            .setCancelable(false)
            .setPositiveButton("Tak", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    dialog.cancel();
                    checkBluetoothEnabled();
                }
            })
            .setNegativeButton("Nie", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    ReceiveActivity.this.finish();
                }
            });
        AlertDialog alert = builder.create();
        alert.show();
    }

    //-----------------------------------------------------------------utworz handler miedzy glownym
    //-----------------------------------------------------------------a watkiem a watkiem clienta
    private void createHandler()
    {
        clientHandler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message inputMessage)
            {
                int percentage;
                switch (inputMessage.what)
                {
                    case PROGRESS:
                        percentage = (int) inputMessage.obj;
                        progress.setProgress(percentage);
                        break;
                    case DONE:
                        messageDialog("Przesyłanie zakończone!", DIALOG_MODE_DONE);
                        break;
                    case CONNECTING_FAILED:
                        messageDialog("Nie udało się utworzyć gniazda", DIALOG_MODE_ERROR);
                        break;
                    case SOCKET_CONNECT_FAILED:
                        messageDialog("Wystąpił błąd połączenia!\nPowrót", DIALOG_MODE_ERROR);
                        break;
                    case EXTERNAL_STORAGE_ACCESS_FAILED:
                        messageDialog("Brak dostępu do pamięci masowej!\nPowrót", DIALOG_MODE_ERROR);
                        break;
                    case FILE_TRANSFER_FAILED:
                        messageDialog("Wystąpił błąd podczas przesyłania danych!\nPowrót",
                                    DIALOG_MODE_ERROR);
                        break;
                    default:
                        super.handleMessage(inputMessage);
                }
            }
        };
    }

    //-----------------------------------------------------------------utworz okienka dialogowe
    private void messageDialog(String message, final int mode)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    switch (mode)
                    {
                        case DIALOG_MODE_EXIT:
                            finish();
                            break;
                        case DIALOG_MODE_ERROR:
                            mClient.stopSocket();
                            finish();
                            break;
                        case DIALOG_MODE_DONE:
                            mClient.stopSocket();
                            finish();
                            break;
                        default:
                            break;
                    }
                }
            });
        AlertDialog alert = builder.create();
        alert.show();
    }

    //-----------------------------------------------------------------sprawdz czy bluetooh wlaczone
    private void checkBluetoothEnabled()
    {
        if (mBluetoothAdapter.isEnabled())
            findDevice();
        else
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST_ID);
        }
    }

    //-----------------------------------------------------------------sprawdz czy wysylajace urzadzenie jest
    //-----------------------------------------------------------------na liscie sparowanych
    //-----------------------------------------------------------------jesli jest, uruchom clienta
    private void findDevice()
    {
        boolean found = false;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if (device.getName().equals(deviceName))
                {
                    found = true;
                    mClient = new Client(clientHandler, device, fileName, fileSize);
                    mClient.start();
                    break;
                }
            }
            if (!found)
                messageDialog("Nie znaleziono urządzenia na liście sparowanych!\nPowrót",
                        DIALOG_MODE_EXIT);
        }
        else
            messageDialog("Nie znaleziono sparowanych urządzeń!\nPowrót", DIALOG_MODE_EXIT);
    }
}
