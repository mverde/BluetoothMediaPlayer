package com.martin.bluetoothmediaplayer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity {
    //music-related variables
    private int[] resources = {R.raw.never_gonna_give_you_up, R.raw.muse_madness_lyrics, R.raw.upside_down,
            R.raw.were_going_to_be_friends, R.raw.lullaby_feat_matt_costa};
    private int currentSong = 0;
    private MediaPlayer mediaPlayer = null;
    //bluetooth-related variables
   private String messageReceived;
   private byte[] ast = "*".getBytes();
   private BluetoothAdapter btAdapter;
   private BluetoothDevice btDevice;
   private ConnectThread btConnectThread;
   private Handler btHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //Log.v("MyActivity", "I GOT TO THE MESSAGE HANDLER");
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;
            printData();

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);
                    messageReceived = writeMessage;

                    if(messageReceived.equals("x"))
                    {
                        pause(getCurrentFocus());
                    }
                    else if(messageReceived.equals("p"))
                    {
                        play(getCurrentFocus());
                    }
                    else if(messageReceived.equals("r"))
                    {
                        skipNext(getCurrentFocus());
                    }
                    else if(messageReceived.equals("l"))
                    {
                        skipPrev(getCurrentFocus());
                    }
                    else
                    {
                    }
                    break;
            }
        }
    };

    public void printData()
    {
        Log.v("MyActivity", "GOT: " + messageReceived);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(mediaPlayer != null)
        {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null)
            System.out.println("Bluetooth not supported.");

        if(!btAdapter.isEnabled())
        {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, 1);
        }

        while(!btAdapter.isEnabled())
        {
            int i = 0;
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                btDevice = device;
            }
        }

        btConnectThread = new ConnectThread(btDevice);
        btConnectThread.start();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    //use to set Completion Listener in any method
    public void setCompletionListener()
    {
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mediaPlayer.stop();
                try{
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch(Exception e){
                    System.out.println("Sleep error.");
                }
                mediaPlayer.release();
                mediaPlayer = null;
                skipNext(getCurrentFocus());
            }
        });
    }

    //called when user presses skip forward button
    public void skipNext(View view)
    {
        if(mediaPlayer != null)
        {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        currentSong++;
        if(currentSong >= resources.length)
            currentSong = 0;

        mediaPlayer = MediaPlayer.create(this, resources[currentSong]);
        mediaPlayer.start();
        setCompletionListener();
    }

    //called when user presses skip backward button
    public void skipPrev(View view)
    {
        if(mediaPlayer != null)
        {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        currentSong--;
        if(currentSong < 0)
            currentSong = resources.length - 1;

        mediaPlayer = MediaPlayer.create(this, resources[currentSong]);
        mediaPlayer.start();
        setCompletionListener();
    }

    //called when user presses pause button while song is playing
    public void pause(View view)
    {
        if(mediaPlayer != null)
            if(mediaPlayer.isPlaying())
                mediaPlayer.pause();
    }

    //called when user presses play button while song is paused, not at start
    public void play(View view)
    {
        if(mediaPlayer == null)
            startPlaying();

        if(!mediaPlayer.isPlaying())
            mediaPlayer.start();
    }

    //called when app first starts, initialize MediaPlayer and play first song in list
    public void startPlaying()
    {
        mediaPlayer = MediaPlayer.create(this, resources[currentSong]);
        mediaPlayer.start();
        setCompletionListener();
    }

    //background thread used to connect to teensy device
    private class ConnectThread extends Thread
    {
        private BluetoothSocket mSocket;
        private BluetoothDevice mDevice;
        private ConnectedThread mConnectedThread;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        public ConnectThread(BluetoothDevice device)
        {
            //Log.v("MyActivity", "I GOT TO THE CONNECT CONST");
            BluetoothSocket temp = null;
            mDevice = device;
            try{
                temp = device.createRfcommSocketToServiceRecord(MY_UUID);
            }
            catch(IOException e){}
            mSocket = temp;
        }

        public void run()
        {
            btAdapter.cancelDiscovery();
            while(!mSocket.isConnected())
            {
                try{
                    mSocket.connect();
                }
                catch(IOException connectException){
                    try{
                        mSocket.close();
                    }
                    catch(IOException closeException){
                        return;
                    }
                }
            }

            mConnectedThread = new ConnectedThread(mSocket);
            mConnectedThread.start();
            //Log.v("MyActivity", "I GOT TO THE CONNECTED THREAD");
        }

        public void cancel()
        {
            try{
                mSocket.close();
            }
            catch(IOException e){}
        }
    }

    //thread to handle data transfer when connected
    private class ConnectedThread extends Thread {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            //Log.v("MyActivity", "I GOT TO THE CONSTRUCTOR");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            //Log.v("MyActivity", "I GOT TO CONNECTED RUN");
            byte[] buffer = new byte[1024];
            //byte[] buffer = "u#".getBytes();    //temp
            int begin = 0;
            int bytes = 0;  //temp, should be 0
            int avail = 0;
            while (true) {
                try {
                    //Log.v("MyActivity", "I GOT TO THE LOOP");
                    //write(new byte[] {ast[0]});
                    //Log.v("MyActivity", "I GOT PAST WRITE");
                    if(mmInStream != null)
                    {
                        //avail = mmInStream.available();
                        bytes += mmInStream.read(buffer, bytes, 2);
                    }
                    //Log.v("MyActivity", "I GOT PAST READ");
                    for(int i = begin; i < bytes; i++) {
                        if(buffer[i] == "#".getBytes()[0]) {
                            btHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            //Log.v("MyActivity", "I OBTAINED MESSAGE");
                            begin = i + 1;
                            if(i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) { //temp, should be IOException
                    Log.e("YOUR_APP_LOG_TAG", "I got an error", e);
                    break;
                }
            }
            //Log.v("MyActivity", "I GOT OUTSIDE OF CONNECTED RUN LOOP FOR SOME REASON");
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}
