package com.nfcbluetoothapp.nfcbluetoothapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

class Server extends Thread
{
    private static final String TAG = "Server";

    private static final int LISTENING = 1;
    private static final int CONNECTED = 2;
    private static final int PROGRESS = 3;
    private static final int DONE = 4;
    private static final int LISTENING_FAILED = 5;
    private static final int SOCKET_ACCEPT_FAILED = 6;
    private static final int EXTERNAL_STORAGE_ACCESS_FAILED = 7;
    private static final int FILE_TRANSFER_FAILED = 8;

    private final BluetoothServerSocket mServerSocket;
    private BluetoothSocket mBluetoothSocket;
    private Handler serverHandler;
    private File file;

    //-----------------------------------------------------------------polaczenie przez serial port
    //-----------------------------------------------------------------jako serwer
    Server(Handler h, File f)
    {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        UUID serialPort = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        serverHandler = h;
        file = f;

        BluetoothServerSocket tmp = null;
        try
        {
            tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("btServ", serialPort);
        }
        catch (IOException e)
        {
            serverHandler.sendEmptyMessage(LISTENING_FAILED);
            Log.e(TAG, "Listening failed", e);
        }
        mServerSocket = tmp;
    }

    //-----------------------------------------------------------------nasluchuj polaczenia
    @Override
    public void run()
    {
        while (true)
        {
            serverHandler.sendEmptyMessage(LISTENING);
            try
            {
                mBluetoothSocket = mServerSocket.accept();
            }
            catch (IOException e)
            {
                serverHandler.sendEmptyMessage(SOCKET_ACCEPT_FAILED);
                Log.e(TAG, "Socket's accept() method failed", e);
                return;
            }
            if (mBluetoothSocket != null)
            {
                serverHandler.sendEmptyMessage(CONNECTED);
                sendFile();
                return;
            }
        }
    }

    //-----------------------------------------------------------------rozpocznij wysylanie pliku
    private void sendFile()
    {
        FileInputStream mFileInputStream;
        DataInputStream mDataInputStream;
        DataOutputStream mDataOutputStream;

        int percentage;
        int bufferSize = 16384;
        byte[] buffer = new byte[bufferSize];
        float totalBytes = file.length();
        float readBytesTotal = 0;
        float readBytes;

        //-------------------------------------------------------------sprawdz dostep do pamieci
        if(!canReadFromExternalStorage())
        {
            serverHandler.sendEmptyMessage(EXTERNAL_STORAGE_ACCESS_FAILED);
        }

        //-------------------------------------------------------------strumienie
        try
        {
            mFileInputStream = new FileInputStream(file);
            mDataOutputStream = new DataOutputStream(mBluetoothSocket.getOutputStream());
            mDataInputStream = new DataInputStream(mBluetoothSocket.getInputStream());
        }
        catch (IOException e)
        {
            serverHandler.sendEmptyMessage(FILE_TRANSFER_FAILED);
            Log.e(TAG, "Streams creation failed", e);
            return;
        }

        //-------------------------------------------------------------przesylanie
        try
        {
            while ((readBytes = mFileInputStream.read(buffer)) != -1)
            {
                mDataOutputStream.write(buffer, 0, (int)readBytes);
                mDataOutputStream.flush();

                readBytesTotal += readBytes;
                percentage = (int)((readBytesTotal * 100.0f) / totalBytes);
                while (true)
                {
                    if (mDataInputStream.readBoolean())
                        break;
                }
                Message completeMessage = serverHandler.obtainMessage(PROGRESS, percentage);
                completeMessage.sendToTarget();
            }
        }
        catch (IOException e)
        {
            try
            {
                mFileInputStream.close();
                mDataInputStream.close();
                mDataOutputStream.close();
            }
            catch (IOException ex)
            {
                Log.e(TAG, "Couldn't close streams", ex);
            }
            serverHandler.sendEmptyMessage(FILE_TRANSFER_FAILED);
            Log.e(TAG, "File transfer failed", e);
            return;
        }

        //-------------------------------------------------------------zakoncz przesylanie
        try
        {
            mFileInputStream.close();
            mDataInputStream.close();
            mDataOutputStream.close();
        }
        catch (IOException ex)
        {
            Log.e(TAG, "Couldn't close streams", ex);
        }

        serverHandler.sendEmptyMessage(DONE);
    }

    private boolean canReadFromExternalStorage()
    {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    //-----------------------------------------------------------------zamknij socket
    void stopSocket()
    {
        try
        {
            if (mServerSocket != null)
                mServerSocket.close();
            if (mBluetoothSocket != null)
                mBluetoothSocket.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }
}