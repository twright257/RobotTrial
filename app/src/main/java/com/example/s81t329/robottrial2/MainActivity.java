package com.example.s81t329.robottrial2;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.example.s81t329.robottrial2.driver.*;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.s81t329.robottrial2.driver.UsbSerialPort;
import com.example.s81t329.robottrial2.util.tangoStuff;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import java.io.IOException;


import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String sTranslationFormat = "Translation: %f, %f, %f";
    private static final String sRotationFormat = "Rotation: %f, %f, %f, %f";

    private static final int SECS_TO_MILLISECS = 1000;
    private static final double UPDATE_INTERVAL_MS = 100.0;

    private double mPreviousTimeStamp;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private TextView mTranslationTextView;
    private TextView mRotationTextView;
    private TextView pointText;

    private final Object mSharedLock = new Object();

    private String localized = "localized";
    private String not_localized = "not_localized";

    private TextView mRelocalizationTextView;
    private boolean mIsRelocalized;
    private boolean mIsLearningMode;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsTangoServiceConnected;
    private TangoPoseData mTangoPose;
    private boolean mIsConstantSpaceRelocalize;

    private UsbSerialPort port;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startActivityForResult(Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                Tango.TANGO_INTENT_ACTIVITYCODE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = getIntent();
        mTranslationTextView = (TextView) findViewById(R.id.mTranslationTextView);
        mRotationTextView = (TextView) findViewById(R.id.mRotationTextView);
        pointText = (TextView) findViewById(R.id.pointText);
        // mIsLearningMode = intent.getBooleanExtra(ALStartActivity.USE_AREA_LEARNING, false);
        //mIsConstantSpaceRelocalize = intent.getBooleanExtra(ALStartActivity.LOAD_ADF, false);


        mTango = new Tango(this);
        mIsRelocalized = false;
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        /*try {
            mConfig.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, uuid);
        } catch (TangoErrorException e) {
            // handle exception
        }*/
        ArrayList<String> fullUUIDList = new ArrayList<String>();
        // Returns a list of ADFs with their UUIDs
        fullUUIDList = mTango.listAreaDescriptions();
        String name;
        for (String uuid : fullUUIDList) {
            TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();
            metadata = mTango.loadAreaDescriptionMetaData(uuid);
            byte[] nameBytes = metadata.get(TangoAreaDescriptionMetaData.KEY_NAME);
            name = new String(nameBytes);
            if (name.equals("Robot")) {
                mConfig.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, uuid);
            }
            System.out.println(name);
            System.out.println();
        }

        mRelocalizationTextView = (TextView) findViewById(R.id.relocalization_textview);
    }


    public void buttonOnClick(View v) {
        byte buffer[] = new byte[1];
        switch (v.getId()) {
            case R.id.forwardButton:
                //doIt();
                buffer[0] = 'w';
                try {
                    port.write(buffer, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("forward");
                break;
            case R.id.rightButton:
                buffer[0] = 'd';
                try {
                    port.write(buffer, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("right");
                break;
            case R.id.leftButton:
                buffer[0] = 'a';
                try {
                    port.write(buffer, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("left");
                break;
            case R.id.reverseButton:
                buffer[0] = 'z';
                try {
                    port.write(buffer, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("reverse");
                break;
            case R.id.stopButton:
                buffer[0] = 's';
                try {
                    port.write(buffer, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("stop");
                break;
            case R.id.connectButton:
                doIt();
                break;
            case R.id.SetPoint:
                setMarker();
        }
    }

    private void setMarker()
    {
        String input = String.valueOf(mTangoPose.translation[0]);
        input = input + " " + String.valueOf(mTangoPose.translation[1]);
        pointText.setText(input);
        System.out.println("this has been called" + input);
    }
    public void doIt() {

        // Find all available drivers from attached devices.
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.android.example.USB_PERMISSION"), 0);
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            System.out.println("empty");
            return;
        }

// Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        manager.requestPermission(driver.getDevice(),mPermissionIntent);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            System.out.println("null");
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            return;
        }

// Read some data! Most have just one port (port 0).
        List<UsbSerialPort> ports = driver.getPorts();
        port = ports.get(0);
        System.out.println("port: " + String.valueOf(port));
        try {
            port.open(connection);
            System.out.println("ok");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE); //setBaudRate(115200);
            //byte buffer[] = new byte[1];
            //buffer[0] = 'w';
            //port.write(buffer, 1);
        } catch (IOException e) {
            // Deal with error.
//        } finally {
//            try {
//                port.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
       }
    }



    @Override
    protected void onResume() {
        super.onResume();
        // Lock the Tango configuration and reconnect to the service each time
        // the app
        // is brought to the foreground.
        super.onResume();
        if (!mIsTangoServiceConnected) {
            try {
                setTangoListeners();
            } catch (TangoErrorException e) {
                Toast.makeText(this, "Tango Error! Restart the app!",
                        Toast.LENGTH_SHORT).show();
            }
            try {
                mTango.connect(mConfig);
                mIsTangoServiceConnected = true;
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Service out of date!", Toast.LENGTH_SHORT)
                        .show();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Error! Restart the app!", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When the app is pushed to the background, unlock the Tango
        // configuration and disconnect
        // from the service so that other apps will behave properly.
        try {
            mTango.disconnect();
            mIsTangoServiceConnected = false;
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), "Tango Error!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setTangoListeners() {
        // Select coordinate frame pairs
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        // Add a listener for Tango pose data
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                boolean updateRenderer = false;
                // Make sure to have atomic access to Tango Data so that
                // UI loop doesn't interfere while Pose call back is updating
                // the data.
                mTangoPose = pose;
                synchronized (mSharedLock) {
                    // Check for Device wrt ADF pose, Device wrt Start of Service pose,
                    // Start of Service wrt ADF pose (This pose determines if the device
                    // is relocalized or not).
                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {

                        if (mIsRelocalized) {
                            updateRenderer = true;
                        }
                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        if (!mIsRelocalized) {
                            updateRenderer = true;
                        }

                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData
                            .COORDINATE_FRAME_START_OF_SERVICE) {
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {
                            System.out.println("adf true");
                            mIsRelocalized = true;
                            // Set the color to green
                        } else {
                            System.out.println("adf false");
                            mIsRelocalized = false;
                            // Set the color blue
                        }
                    }
                }
                // Format Translation and Rotation data
                final String translationMsg = String.format(sTranslationFormat,
                        pose.translation[0], pose.translation[1],
                        pose.translation[2]);
                final String rotationMsg = String.format(sRotationFormat,
                        pose.rotation[0], pose.rotation[1], pose.rotation[2],
                        pose.rotation[3]);

                // Output to LogCat
                String logMsg = translationMsg + " | " + rotationMsg;
                Log.i(TAG, logMsg);

                final double deltaTime = (pose.timestamp - mPreviousTimeStamp)
                        * SECS_TO_MILLISECS;
                mPreviousTimeStamp = pose.timestamp;
                mTimeToNextUpdate -= deltaTime;

                // Throttle updates to the UI based on UPDATE_INTERVAL_MS.
                if (mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                    // Display data in TextViews. This must be done inside a
                    // runOnUiThread call because
                    // it affects the UI, which will cause an error if performed
                    // from the Tango
                    // service thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mSharedLock) {
                                mRotationTextView.setText(rotationMsg);
                                mTranslationTextView.setText(translationMsg);
                                mRelocalizationTextView.setText(mIsRelocalized ? localized  : not_localized);
                            }
                        }
                    });
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData arg0) {
                // Ignoring XyzIj data
            }

            @Override
            public void onTangoEvent(TangoEvent arg0) {
                // Ignoring TangoEvents
            }

            @Override
            public void onFrameAvailable(int arg0) {
                // Ignoring onFrameAvailable Events
            }
        });

    }
}