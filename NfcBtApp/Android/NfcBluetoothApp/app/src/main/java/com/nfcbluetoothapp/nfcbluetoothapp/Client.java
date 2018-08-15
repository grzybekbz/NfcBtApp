package com.nfcbluetoothapp.nfcbluetoothapp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

class Client extends Thread
{
    private static final String TAG = "Client";

    private static final int PROGRESS = 1;
    private static final int DONE = 2;
    private static final int CONNECTING_FAILED = 3;
    private static final int SOCKET_CONNECT_FAILED = 4;
    private static final int EXTERNAL_STORAGE_ACCESS_FAILED = 5;
    private static final int FILE_TRANSFER_FAILED = 6;

    private final BluetoothSocket mBluetoothSocket;
    private Handler clientHandler;
    private String fileName;
    private long fileSize;

    //-----------------------------------------------------------------polaczenie przez serial port
    //-----------------------------------------------------------------jako klient
    Client(Handler h, BluetoothDevice d, String fn, long fs)
    {
        UUID serialPort = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        clientHandler = h;
        fileName = fn;
        fileSize = fs;

        BluetoothSocket tmp = null;
        try
        {
            tmp = d.createRfcommSocketToServiceRecord(serialPort);
        }
        catch (IOException e)
        {
            clientHandler.sendEmptyMessage(CONNECTING_FAILED);
            Log.e(TAG, "Socket's create() method failed", e);
        }
        mBluetoothSocket = tmp;
    }

    //-----------------------------------------------------------------proba polaczenia
    public void run()
    {
        try
        {
            mBluetoothSocket.connect();
        }
        catch (IOException e)
        {
            clientHandler.sendEmptyMessage(SOCKET_CONNECT_FAILED);
            Log.e(TAG, "Socket's connect() method failed", e);
            return;
        }

        receiveFile();
    }

    //-----------------------------------------------------------------rozpocznij odbieranie pliku
    private void receiveFile()
    {
        FileOutputStream mFileOutputStream;
        DataInputStream mDataInputStream;
        DataOutputStream mDataOutputStream;

        File file;
        Message completeMessage;
        int percentage;
        int bufferSize = 16384;
        long time = System.currentTimeMillis() / 1000;
        long count = fileSize / bufferSize;
        long rest = fileSize - (count * bufferSize);
        byte[] buffer = new byte[bufferSize];
        float totalBytes = fileSize;
        float readBytesTotal = 0;

        //-------------------------------------------------------------sprawdz dostep do pamieci
        if(!canWriteOnExternalStorage())
        {
            clientHandler.sendEmptyMessage(EXTERNAL_STORAGE_ACCESS_FAILED);
        }

        //-------------------------------------------------------------nowy plik
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        file = new File(path, fileName);
        if (file.exists())
            file = new File(path, (time + "_") + fileName);

        //-------------------------------------------------------------strumienie
        try
        {
            mFileOutputStream = new FileOutputStream(file);
            mDataInputStream = new DataInputStream(mBluetoothSocket.getInputStream());
            mDataOutputStream = new DataOutputStream(mBluetoothSocket.getOutputStream());
        }
        catch (IOException e)
        {
            clientHandler.sendEmptyMessage(FILE_TRANSFER_FAILED);
            Log.e(TAG, "Couldn't create socket", e);
            return;
        }

        //-------------------------------------------------------------przesylanie
        try
        {
            for (int i = 0; i < count; ++i)
            {
                mDataInputStream.readFully(buffer, 0, bufferSize);
                mFileOutputStream.write(buffer, 0, bufferSize);
                mFileOutputStream.flush();

                readBytesTotal += bufferSize;
                percentage = (int)((readBytesTotal * 100.0f) / totalBytes);

                mDataOutputStream.writeBoolean(true);
                mDataOutputStream.flush();

                completeMessage = clientHandler.obtainMessage(PROGRESS, percentage);
                completeMessage.sendToTarget();
            }
            if (rest != 0)
            {
                mDataInputStream.readFully(buffer, 0, (int)rest);
                mFileOutputStream.write(buffer, 0, (int)rest);
                mFileOutputStream.flush();

                readBytesTotal += rest;
                percentage = (int)((readBytesTotal * 100.0f) / totalBytes);

                mDataOutputStream.writeBoolean(true);
                mDataOutputStream.flush();

                completeMessage = clientHandler.obtainMessage(PROGRESS, percentage);
                completeMessage.sendToTarget();
            }
        }
        catch (IOException e)
        {
            try
            {
                mFileOutputStream.close();
                mDataInputStream.close();
                mDataOutputStream.close();
            }
            catch (IOException ex)
            {
                Log.e(TAG, "Couldn't close streams", ex);
            }
            file.delete();
            clientHandler.sendEmptyMessage(FILE_TRANSFER_FAILED);
            Log.e(TAG, "File transfer failed", e);
            return;
        }

        //-------------------------------------------------------------zakoncz przesylanie
        try
        {
            mFileOutputStream.close();
            mDataInputStream.close();
            mDataOutputStream.close();
        }
        catch (IOException ex)
        {
            Log.e(TAG, "Couldn't close streams", ex);
        }
        clientHandler.sendEmptyMessage(DONE);
    }

    private boolean canWriteOnExternalStorage()
    {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    //-----------------------------------------------------------------zamknij socket
    void stopSocket()
    {
        try
        {
            mBluetoothSocket.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}