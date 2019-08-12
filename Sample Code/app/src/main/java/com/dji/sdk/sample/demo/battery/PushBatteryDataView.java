package com.dji.sdk.sample.demo.battery;

import android.content.Context;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.view.BasePushDataView;
import dji.common.battery.BatteryState;

/**
 * Class for getting the battery information.
 */
public class PushBatteryDataView extends BasePushDataView {
    public PushBatteryDataView(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        try {
            DJISampleApplication.getProductInstance().getBattery().setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState djiBatteryState) {
                    stringBuffer.delete(0, stringBuffer.length());

                    stringBuffer.append("BatteryEnergyRemainingPercent: ").
                        append(djiBatteryState.getChargeRemainingInPercent()).
                                    append("%\n");
                    stringBuffer.append("CurrentVoltage: ").
                        append(djiBatteryState.getVoltage()).append("mV\n");
                    stringBuffer.append("CurrentCurrent: ").
                        append(djiBatteryState.getCurrent()).append("mA\n");

                    showStringBufferResult();
                }
            });
        } catch (Exception ignored) {

        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        try {
            DJISampleApplication.getProductInstance().getBattery().setStateCallback(null);
        } catch (Exception ignored) {

        }
    }

    @Override
    public int getDescription() {
        return R.string.battery_listview_push_info;
    }

    /**
    Aircraft mAircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
    FlightController mFlightController = mAircraft.getFlightController();


    private void communicateWithOnboardSDK() {
        mFlightController.sendDataToOnboardSDKDevice("HelloWorld".getBytes(), new CommonCallbacks.CompletionCallback() {
        @Override
        public void onResult(DJIError djiError) {
            if (djiError != null) {
                showToast(djiError.toString());
                }
            }
        });

        mFlightController.setOnboardSDKDeviceDataCallback(new FlightController.OnboardSDKDeviceDataCallback() {
        @Override
        public void onReceive(byte[] bytes) {
            StringBuilder buffer = new StringBuilder();
            for (byte b : bytes) {
                buffer.append((char) b);
            }
            showToast(buffer.toString()); //Apresenta o texto recebido
            }
        });
     }

    private void showToast(final String toastMsg) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });

    } */
}
