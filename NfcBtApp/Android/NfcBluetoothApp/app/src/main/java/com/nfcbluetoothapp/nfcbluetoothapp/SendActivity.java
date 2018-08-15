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

import android.bluetooth.BluetoothAdapter;
import android.nfc.NfcEvent;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NdefMessage;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.UnsupportedEncodingException;

public class SendActivity extends Activity implements NfcAdapter.CreateNdefMessageCallback,
                                                      NfcAdapter.OnNdefPushCompleteCallback
{
    public static final int BLUETOOTH_ENABLE_REQUEST_ID = 17;
    public static final int READ_EXTERNAL_STORAGE_REQUEST_ID = 42;

    private static final int LISTENING = 1;
    private static final int CONNECTED = 2;
    private static final int PROGRESS = 3;
    private static final int DONE = 4;
    private static final int LISTENING_FAILED = 5;
    private static final int SOCKET_ACCEPT_FAILED = 6;
    private static final int EXTERNAL_STORAGE_ACCESS_FAILED = 7;
    private static final int FILE_TRANSFER_FAILED = 8;

    private static final int DIALOG_MODE_ERROR = 1;
    private static final int DIALOG_MODE_DONE = 2;
    private static final int DIALOG_MODE_EXIT = 3;

    private NfcAdapter mNfcAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler serverHandler;
    private ProgressBar progress;
    private ProgressBar waiting;
    private ImageView tapImage;
    private TextView tapText;
    private TextView statusText;
    private Button fileButton;
    private Button sendButton;
    private Server mServer;
    private File file;
    private Long fileSize;
    private boolean acceptMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        acceptMessages = true;

        //-------------------------------------------------------------zapytaj o uprawnienia
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(SendActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_EXTERNAL_STORAGE_REQUEST_ID);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        createHandler();

        progress = (ProgressBar)findViewById(R.id.progressBar);
        progress.setVisibility(View.INVISIBLE);

        waiting = (ProgressBar) findViewById(R.id.waiting);
        waiting.setVisibility(View.INVISIBLE);

        tapImage = (ImageView)findViewById(R.id.tapImage);
        tapImage.setVisibility(View.INVISIBLE);

        tapText = (TextView)findViewById(R.id.tapText);
        tapText.setVisibility(View.INVISIBLE);

        statusText = (TextView) findViewById(R.id.statusText);

        //-------------------------------------------------------------przycisk wyslij
        sendButton = (Button)findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                checkBluetoothEnabled();
            }
        });
        sendButton.setVisibility(View.INVISIBLE);

        //-------------------------------------------------------------przycisk anuluj
        final Button cancelButton = (Button)findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                resetUi();
            }
        });

        //-------------------------------------------------------------przycisk wybierz
        fileButton = (Button)findViewById(R.id.fileButton);
        fileButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                FileChooser FileOpenDialog =  new FileChooser(SendActivity.this,
                new FileChooser.SimpleFileDialogListener()
                {
                    @Override
                    public void onChosenDir(String chosenFile)
                    {
                        file = new File(chosenFile);
                        fileSize = file.length();
                        statusText.setText("Nazwa pliku: " +
                                file.getName() + "\n" + "Rozmiar: " + file.length() + " bajtów");
                        sendButtonVisibility();
                    }
                });
                FileOpenDialog.chooseFile_or_Dir();
            }
        });
    }

    //-----------------------------------------------------------------sprawdz uprawnienia
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case READ_EXTERNAL_STORAGE_REQUEST_ID:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    break;
                else
                {
                    messageDialog("Odmowa dostępu do plików\nPowrót!", DIALOG_MODE_EXIT);
                }
            }
        }
    }

    //-----------------------------------------------------------------poproś o wlaczenie bluetooth
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BLUETOOTH_ENABLE_REQUEST_ID)
        {
            if (resultCode == RESULT_OK)
                mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            else if (resultCode == RESULT_CANCELED)
                Toast.makeText(this, "Aby wysyłać, włącz Bluetooth", Toast.LENGTH_LONG).show();
        }
    }

    //-----------------------------------------------------------------przycisk wstecz
    @Override
    public void onBackPressed()
    {
        acceptMessages = false;
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("Przerwij wysyłanie")
            .setMessage("Jesteś pewien, że chcesz wrócić do menu głównego?")
            .setPositiveButton("Tak", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    if (mServer != null)
                        mServer.stopSocket();
                    finish();
                }
            })
            .setNegativeButton("Nie", null)
            .show();
    }

    //-----------------------------------------------------------------wykonaj po wyslaniu przez NFC
    //-----------------------------------------------------------------start serwera
    @Override
    public void onNdefPushComplete(NfcEvent event)
    {
        mServer.start();

        SendActivity.this.runOnUiThread(new Runnable()
        {
            public void run()
            {
                tapImage.setVisibility(View.INVISIBLE);
                tapText.setVisibility(View.INVISIBLE);
            }
        });
        mNfcAdapter.setNdefPushMessageCallback(null, this);
    }

    //-----------------------------------------------------------------zawartosc wiadomosci NDEF
    @Override
    public NdefMessage createNdefMessage(NfcEvent event)
    {
        return new NdefMessage(
            createTextRecord("en", "ngi8aw6"),
            createTextRecord("en", mBluetoothAdapter.getName()),
            createTextRecord("en", file.getName()),
            createTextRecord("en", fileSize.toString()),
            NdefRecord.createApplicationRecord("com.nfcbluetoothapp.nfcbluetoothapp")
        );
    }

    //-----------------------------------------------------------------sprawdz czy bluetooh wlaczone
    private void checkBluetoothEnabled()
    {
        if (!mBluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST_ID);
        } else
            checkNfcEnabled();
    }

    //-----------------------------------------------------------------sprawdz czy NFC i Beam wlaczone
    private void checkNfcEnabled()
    {
        if (!mNfcAdapter.isEnabled())
        {
            Toast.makeText(this, "Aby wysyłać, włącz NFC", Toast.LENGTH_LONG).show();
            if(!mNfcAdapter.isNdefPushEnabled())
                Toast.makeText(this, "Aby wysyłać, włącz Android Beam", Toast.LENGTH_LONG).show();
            else
                sendNdefMessage();
        }
        else
        {
            if (!mNfcAdapter.isNdefPushEnabled())
                Toast.makeText(this, "Aby wysyłać, włącz Android Beam", Toast.LENGTH_LONG).show();
            else
                sendNdefMessage();
        }
    }

    //-----------------------------------------------------------------rozpocznij rozgłaszanie przez NFC
    private void sendNdefMessage()
    {
        mServer = new Server(serverHandler, file);
        statusText.setText("");
        tapImage.setVisibility(View.VISIBLE);
        tapText.setVisibility(View.VISIBLE);
        sendButton.setVisibility(View.INVISIBLE);
        fileButton.setVisibility(View.INVISIBLE);
        nfcPushEnabled();
    }

    private void nfcPushEnabled()
    {
        mNfcAdapter.setNdefPushMessageCallback(this, this);
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }

    //-----------------------------------------------------------------rekord tekstowy wiadomosci NDEF
    private static NdefRecord createTextRecord(String language, String text)
    {
        byte[] languageBytes;
        byte[] textBytes;
        try
        {
            languageBytes = language.getBytes("US-ASCII");
            textBytes = text.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new AssertionError(e);
        }

        byte[] recordPayload = new byte[1 + (languageBytes.length & 0x03F) + textBytes.length];

        recordPayload[0] = (byte)(languageBytes.length & 0x03F);
        System.arraycopy(languageBytes, 0, recordPayload, 1, languageBytes.length & 0x03F);
        System.arraycopy(textBytes, 0, recordPayload, 1 +
                (languageBytes.length & 0x03F), textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, null, recordPayload);
    }

    //-----------------------------------------------------------------utworz handler miedzy glownym
    //-----------------------------------------------------------------a watkiem a watkiem serwera
    private void createHandler()
    {
        serverHandler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message inputMessage)
            {
                int percentage;
                switch (inputMessage.what)
                {
                    case LISTENING:
                        waiting.setVisibility(View.VISIBLE);
                        statusText.setText("Oczekiwanie na połączenie");
                        break;
                    case CONNECTED:
                        waiting.setVisibility(View.INVISIBLE);
                        progress.setVisibility(View.VISIBLE);
                        statusText.setText("Przesyłanie pliku");
                        break;
                    case PROGRESS:
                        percentage = (int) inputMessage.obj;
                        progress.setProgress(percentage);
                        break;
                    case DONE:
                        messageDialog("Przesyłanie zakończone!", DIALOG_MODE_DONE);
                        break;
                    case LISTENING_FAILED:
                        if(acceptMessages)
                            messageDialog("Nie udało się utworzyć gniazda", DIALOG_MODE_ERROR);
                        break;
                    case SOCKET_ACCEPT_FAILED:
                        if(acceptMessages)
                            messageDialog("Wystąpił błąd połączenia!\nPowrót", DIALOG_MODE_ERROR);
                        break;
                    case EXTERNAL_STORAGE_ACCESS_FAILED:
                        messageDialog("Brak dostępu do pamięci masowej!\nPowrót", DIALOG_MODE_ERROR);
                        break;
                    case FILE_TRANSFER_FAILED:
                        if(acceptMessages)
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
                public void onClick(DialogInterface dialog, int id)
                {
                    switch (mode)
                    {
                        case DIALOG_MODE_EXIT:
                            finish();
                            break;
                        case DIALOG_MODE_ERROR:
                            resetUi();
                            break;
                        case DIALOG_MODE_DONE:
                            mServer.stopSocket();
                            recreate();
                            break;
                        default:
                            break;
                    }
                }
            });
        AlertDialog alert = builder.create();
        alert.show();
    }

    //-----------------------------------------------------------------zresetuj UI
    private void resetUi()
    {
        if (file != null)
        {
            if (mServer != null)
                mServer.stopSocket();
            progress.setVisibility(View.INVISIBLE);
            waiting.setVisibility(View.INVISIBLE);
            tapImage.setVisibility(View.INVISIBLE);
            tapText.setVisibility(View.INVISIBLE);
            progress.setProgress(0);
            fileButton.setVisibility(View.VISIBLE);
            sendButton.setVisibility(View.VISIBLE);
            statusText.setText("Nazwa pliku: " + file.getName() + "\n" +
                    "Rozmiar: " + file.length() + " bajtów");
            acceptMessages = true;
        }
    }

    //-----------------------------------------------------------------widocznosc przycisku wysylania
    private void sendButtonVisibility()
    {
        sendButton.setVisibility(View.VISIBLE);
    }
}
