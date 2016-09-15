/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.app.help.HelpOption;
import com.mbientlab.metawear.app.help.HelpOptionAdapter;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Barometer;
import com.mbientlab.metawear.module.Bma255Accelerometer;
import com.mbientlab.metawear.module.Bme280Barometer;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.Bmm150Magnetometer;
import com.mbientlab.metawear.module.Bmp280Barometer;
import com.mbientlab.metawear.module.Gyro;
import com.mbientlab.metawear.module.Mma8452qAccelerometer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by etsai on 8/19/2015.
 */
public class AccelerometerFragment extends ThreeAxisChartFragment {
    private static final float[] MMA845Q_RANGES= {2.f, 4.f, 8.f}, BMI160_RANGES= {2.f, 4.f, 8.f, 16.f};
    private static final float INITIAL_RANGE= 2.f, ACC_FREQ= 50.f;
    private static final String STREAM_KEY= "accel_stream";

    private Spinner accRangeSelection;
    private Accelerometer accelModule= null;
    private int rangeIndex= 0;

    private String ipAddress = "192.168.100.137";
    private int port = 7474;
    private OSCPortOut oscPortOut = null;

    private static final float BAROMETER_SAMPLE_FREQ = 26.32f, LIGHT_SAMPLE_PERIOD= 1 / BAROMETER_SAMPLE_FREQ;
    private static String PRESSURE_STREAM_KEY= "pressure_stream", ALTITUDE_STREAM_KEY= "altitude";

    private Barometer barometerModule;
    private RouteManager altitudeRouteManager= null;
    private final ArrayList<Entry> altitudeData= new ArrayList<>(), pressureData= new ArrayList<>();

    private static final String GYR_STREAM_KEY= "gyro_stream";
    private Gyro gyroModule= null;
    private static final float[] GYRAVAILABLE_RANGES= {125.f, 250.f, 500.f, 1000.f, 2000.f};
    private static final float GYRINITIAL_RANGE= 125.f, GYR_ODR= 25.f;

    private static final float B_FIELD_RANGE= 250.f, MAG_ODR= 10.f;
    private static final String MAGSTREAM_KEY = "b_field_stream";
    private Bmm150Magnetometer magModule= null;

    public AccelerometerFragment() {
        super("acceleration", R.layout.fragment_sensor_config_spinner,
                R.string.navigation_fragment_accelerometer, STREAM_KEY, -INITIAL_RANGE, INITIAL_RANGE);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((TextView) view.findViewById(R.id.config_option_title)).setText(R.string.config_name_acc_range);

        accRangeSelection= (Spinner) view.findViewById(R.id.config_option_spinner);
        accRangeSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                rangeIndex = position;

                final YAxis leftAxis = chart.getAxisLeft();
                if (accelModule instanceof Bmi160Accelerometer || accelModule instanceof Bma255Accelerometer) {
                    leftAxis.setAxisMaxValue(BMI160_RANGES[rangeIndex]);
                    leftAxis.setAxisMinValue(-BMI160_RANGES[rangeIndex]);
                } else if (accelModule instanceof Mma8452qAccelerometer) {
                    leftAxis.setAxisMaxValue(MMA845Q_RANGES[rangeIndex]);
                    leftAxis.setAxisMinValue(-MMA845Q_RANGES[rangeIndex]);
                }

                refreshChart(false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        fillRangeAdapter();
    }

    @Override
    protected void boardReady() throws UnsupportedModuleException{
        accelModule= mwBoard.getModule(Accelerometer.class);
        barometerModule= mwBoard.getModule(Barometer.class);
        gyroModule= mwBoard.getModule(Gyro.class);
        magModule= mwBoard.getModule(Bmm150Magnetometer.class);
        fillRangeAdapter();
        initializeOSC();
    }

    private void initializeOSC() {
        try {

            if(oscPortOut != null) {
                oscPortOut.close();
            }

            oscPortOut = new OSCPortOut(InetAddress.getByName(ipAddress), port);
        }
        catch(Exception exp) {
            Log.i("OSC Port Error" ,"Cannt make the port");
            oscPortOut = null;
        }
    }

    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {
        adapter.add(new HelpOption(R.string.config_name_acc_range, R.string.config_desc_acc_range));
    }

    @Override
    protected void setup() {
        samplePeriod = 1 / accelModule.setOutputDataRate(ACC_FREQ);

        if (accelModule instanceof Bmi160Accelerometer || accelModule instanceof Bma255Accelerometer) {
            accelModule.setAxisSamplingRange(BMI160_RANGES[rangeIndex]);
        } else if (accelModule instanceof Mma8452qAccelerometer) {
            accelModule.setAxisSamplingRange(MMA845Q_RANGES[rangeIndex]);
        }

        AsyncOperation<RouteManager> routeManagerResult = accelModule.routeData().fromAxes().stream(STREAM_KEY).commit();
        //routeManagerResult.onComplete(dataStreamManager);
        routeManagerResult.onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {
                result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
                    @Override
                    public void process(Message msg) {
                        Log.i("test", "high freq: " + msg.getData(CartesianFloat.class));

                        sendOSC("/Acc HF Sample/"+msg.getData(CartesianFloat.class).toString());

                    }
                });
                accelModule.setOutputDataRate(200.f);
                accelModule.enableAxisSampling();
                accelModule.start();
            }
        });

        if (barometerModule instanceof Bmp280Barometer) {
            ((Bmp280Barometer) barometerModule).configure()
                    .setPressureOversampling(Bmp280Barometer.OversamplingMode.ULTRA_HIGH)
                    .setFilterMode(Bmp280Barometer.FilterMode.OFF)
                    .setStandbyTime(Bmp280Barometer.StandbyTime.TIME_0_5)
                    .commit();
            ((Bmp280Barometer) barometerModule).enableAltitudeSampling();
        } else if (barometerModule instanceof Bme280Barometer) {
            ((Bme280Barometer) barometerModule).configure()
                    .setPressureOversampling(Bmp280Barometer.OversamplingMode.ULTRA_HIGH)
                    .setFilterMode(Bmp280Barometer.FilterMode.OFF)
                    .setStandbyTime(Bme280Barometer.StandbyTime.TIME_0_5)
                    .commit();
            ((Bme280Barometer) barometerModule).enableAltitudeSampling();
        }
        barometerModule.routeData().fromPressure().stream(PRESSURE_STREAM_KEY).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        streamRouteManager= result;
                        result.subscribe(PRESSURE_STREAM_KEY, new RouteManager.MessageHandler() {
                            @Override
                            public void process(Message msg) {
                                Log.i("Baro ", String.format(" //Pressure=// %.3fPa",
                                        msg.getData(Float.class)));
                                sendOSC("/Bar Sample/"+msg.getData(Float.class).toString());
                                // sendOSC("/Acc HF Sample/"+msg.getData(CartesianFloat.class).toString());
                            }
                        });
                        barometerModule.start();
                    }
                });
        barometerModule.routeData().fromAltitude().stream(ALTITUDE_STREAM_KEY).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        altitudeRouteManager= result;
                        result.subscribe(ALTITUDE_STREAM_KEY, new BarometerMessageHandler(altitudeData, 1));

                        barometerModule.start();
                    }
                });

        gyroModule.setOutputDataRate(GYR_ODR);
        gyroModule.setAngularRateRange(GYRAVAILABLE_RANGES[rangeIndex]);

        AsyncOperation<RouteManager> gyrrouteManagerResult= gyroModule.routeData().fromAxes().stream(GYR_STREAM_KEY).commit();
        //routeManagerResult.onComplete(dataStreamManager);
        gyrrouteManagerResult.onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {
                result.subscribe(GYR_STREAM_KEY, new RouteManager.MessageHandler() {
                    @Override
                    public void process(Message msg) {

                        final CartesianFloat  spinData= msg.getData(CartesianFloat.class);
                        Log.i("GYO" ,spinData.toString());
                        sendOSC("/GYO /"+spinData.toString());
                    }
                });
                gyroModule.start();
            }
        });


        magModule.setPowerPrsest(Bmm150Magnetometer.PowerPreset.LOW_POWER);
        magModule.enableBFieldSampling();

        AsyncOperation<RouteManager> magrouteManagerResult= magModule.routeData().fromBField().stream(MAGSTREAM_KEY).commit();
       // magrouteManagerResult.onComplete(dataStreamManager);
        magrouteManagerResult.onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {
                result.subscribe(MAGSTREAM_KEY, new RouteManager.MessageHandler(){
                    @Override
                    public void process(Message msg) {

                        final CartesianFloat bField = msg.getData(CartesianFloat.class);
                        Log.i(" MAG"  , bField.toString());
                        sendOSC("/MAG /"+bField.toString());
                    }
                });
                magModule.enableBFieldSampling();
                magModule.start();
            }
        });



    }

    private class BarometerMessageHandler implements RouteManager.MessageHandler {
        private final ArrayList<Entry> dataEntries;
        private final int setIndex;

        public BarometerMessageHandler(ArrayList<Entry> dataEntries, int setIndex) {
            this.dataEntries= dataEntries;
            this.setIndex= setIndex;
        }
        @Override
        public void process(Message message) {
            final Float pressureValue = message.getData(Float.class);

            LineData data = chart.getData();
            if (dataEntries.size() >= sampleCount) {
                data.addXValue(String.format(Locale.US, "%.2f", sampleCount * LIGHT_SAMPLE_PERIOD));
                sampleCount++;
            }
            data.addEntry(new Entry(pressureValue, sampleCount), setIndex);
        }
    }
    public void sendOSC(String message) {
        try {
            new AsyncSendOSCTask(this,this.oscPortOut).execute(new OSCMessage(message));
        } catch (Exception exp) {
            Log.i("test", "Cannt send Message "+ exp);
        }
    }

    @Override
    protected void clean() {
        accelModule.stop();
        accelModule.disableAxisSampling();

        barometerModule.stop();
        gyroModule.stop();

        magModule.stop();
        magModule.disableBFieldSampling();
    }

    private void fillRangeAdapter() {
        ArrayAdapter<CharSequence> spinnerAdapter= null;
        if (accelModule instanceof Bmi160Accelerometer || accelModule instanceof Bma255Accelerometer) {
            spinnerAdapter= ArrayAdapter.createFromResource(getContext(), R.array.values_bmi160_acc_range, android.R.layout.simple_spinner_item);
        } else if (accelModule instanceof Mma8452qAccelerometer) {
            spinnerAdapter= ArrayAdapter.createFromResource(getContext(), R.array.values_mma8452q_acc_range, android.R.layout.simple_spinner_item);
        }

        if (spinnerAdapter != null) {
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            accRangeSelection.setAdapter(spinnerAdapter);
        }
    }
}
