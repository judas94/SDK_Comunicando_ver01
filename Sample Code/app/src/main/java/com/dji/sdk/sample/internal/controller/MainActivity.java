package com.dji.sdk.sample.internal.controller;

import android.Manifest;
import android.animation.AnimatorInflater;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.model.ViewWrapper;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.DemoListView;
import com.dji.sdk.sample.internal.view.PresentableView;
import com.squareup.otto.Subscribe;

import org.bouncycastle.jcajce.provider.symmetric.ARC4;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
        Manifest.permission.VIBRATE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO
    };
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private FrameLayout contentFrameLayout;
    private ObjectAnimator pushInAnimator;
    private ObjectAnimator pushOutAnimator;
    private ObjectAnimator popInAnimator;
    private LayoutTransition popOutTransition;
    private Stack<ViewWrapper> stack;
    private TextView titleTextView;
    private SearchView searchView;
    private MenuItem searchViewItem;
    private MenuItem hintItem;
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {

        @Override
        public void onConnectivityChange(boolean isConnected) {
            Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
            notifyStatusChange();
        }
    };


    //region Life-cycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        DJISampleApplication.getEventBus().register(this);

        setContentView(R.layout.activity_main);

        setupActionBar();
        contentFrameLayout = (FrameLayout) findViewById(R.id.framelayout_content);

        initParams();
    }


    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions!!!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        DJISampleApplication.getEventBus().unregister(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the options menu from XML
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchViewItem = menu.findItem(R.id.action_search);
        hintItem = menu.findItem(R.id.action_hint);
        searchView = (SearchView) searchViewItem.getActionView();
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false); // Do not iconify the widget; expand it by default
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(""));
                return false;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(query));
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(newText));
                return false;
            }
        });

        // Hint click
        hintItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                showHint();
                return false;
            }
        });
        return true;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            popView();
        } else {
            super.onBackPressed();
        }
    }

    //endregion


    //region Registration n' Permissions Helpers

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_doing_message));
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_success_message));


                            } else {
                                ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_message) + djiError.getDescription());
                            }
                            Log.v(TAG, djiError.getDescription());
                        }
                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            notifyStatusChange();
                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));

                            showToast("Entrando em communicatitewith");
                            //BaseProduct product = DJISDKManager.getInstance().getProduct();
                            BaseProduct product = DJISampleApplication.getProductInstance();  //Qualquer um dos dois funciona aparentemente
                            communicateWithOnboardSDK(product);

                            notifyStatusChange();
                        }
                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                                      BaseComponent oldComponent,
                                                      BaseComponent newComponent) {
                            if (newComponent != null) {
                                newComponent.setComponentListener(mDJIComponentListener);
                            }
                            Log.d(TAG,
                                  String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                                componentKey,
                                                oldComponent,
                                                newComponent));

                            notifyStatusChange();
                        }

                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }
                    });
                }
            });
        }
    }


    private void showToast(final String toastMsg) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });

    }


    private void notifyStatusChange() {
        DJISampleApplication.getEventBus().post(new ConnectivityChangeEvent());
    }
    //endregion

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (null != actionBar) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.actionbar_custom);

            titleTextView = (TextView) (actionBar.getCustomView().findViewById(R.id.title_tv));
        }
    }


    private void setupInAnimations() {
        pushInAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.slide_in_right);
        pushOutAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.fade_out);
        popInAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.fade_in);
        ObjectAnimator popOutAnimator =
                (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.slide_out_right);

        pushOutAnimator.setStartDelay(100);

        popOutTransition = new LayoutTransition();
        popOutTransition.setAnimator(LayoutTransition.DISAPPEARING, popOutAnimator);
        popOutTransition.setDuration(popOutAnimator.getDuration());
    }

    private void initParams() {
        setupInAnimations();

        stack = new Stack<ViewWrapper>();
        View view = contentFrameLayout.getChildAt(0);
        stack.push(new ViewWrapper(view, R.string.activity_component_list));
    }

    private void pushView(ViewWrapper wrapper) {
        if (stack.size() <= 0) {
            return;
        }

        contentFrameLayout.setLayoutTransition(null);

        View showView = wrapper.getView();

        View preView = stack.peek().getView();

        stack.push(wrapper);

        if (showView.getParent() != null) {
            ((ViewGroup) showView.getParent()).removeView(showView);
        }
        contentFrameLayout.addView(showView);

        pushOutAnimator.setTarget(preView);
        pushOutAnimator.start();

        pushInAnimator.setTarget(showView);
        pushInAnimator.setFloatValues(contentFrameLayout.getWidth(), 0);
        pushInAnimator.start();


        showToast("Entrando no refreshTitle");
        refreshTitle();
        refreshOptionsMenu();
    }

    private void refreshTitle() {
        if (stack.size() > 1) {
            ViewWrapper wrapper = stack.peek();
            titleTextView.setText(wrapper.getTitleId());
        } else if (stack.size() == 1) {
            BaseProduct product = DJISampleApplication.getProductInstance();

            if (product != null && product.getModel() != null) {
                titleTextView.setText("" + product.getModel().getDisplayName());
            } else {
                titleTextView.setText(R.string.sample_app_name);
            }
        }
    }

    private void popView() {

        if (stack.size() <= 1) {
            finish();
            return;
        }

        ViewWrapper removeWrapper = stack.pop();

        View showView = stack.peek().getView();
        View removeView = removeWrapper.getView();

        contentFrameLayout.setLayoutTransition(popOutTransition);
        contentFrameLayout.removeView(removeView);

        popInAnimator.setTarget(showView);
        popInAnimator.start();

        refreshTitle();
        refreshOptionsMenu();
    }

    private void refreshOptionsMenu() {
        if (stack.size() == 2 && stack.peek().getView() instanceof DemoListView) {
            searchViewItem.setVisible(true);
        } else {
            searchViewItem.setVisible(false);
            searchViewItem.collapseActionView();
        }
        if (stack.size() == 3 && stack.peek().getView() instanceof PresentableView) {
            hintItem.setVisible(true);
        } else {
            hintItem.setVisible(false);
        }
    }


    private void showHint() {
        if (stack.size() != 0 && stack.peek().getView() instanceof PresentableView) {
            ToastUtils.setResultToToast(((PresentableView) stack.peek().getView()).getHint());
        }
    }


    //region Event-Bus
    @Subscribe
    public void onReceiveStartFullScreenRequest(RequestStartFullScreenEvent event) {
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Subscribe
    public void onReceiveEndFullScreenRequest(RequestEndFullScreenEvent event) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getSupportActionBar().show();
    }

    @Subscribe
    public void onPushView(final ViewWrapper wrapper) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pushView(wrapper);
            }
        });
    }

    @Subscribe
    public void onConnectivityChange(ConnectivityChangeEvent event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshTitle();
            }
        });
    }

    public static class SearchQueryEvent {
        private final String query;

        public SearchQueryEvent(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }
    }

    public static class RequestStartFullScreenEvent {
    }

    public static class RequestEndFullScreenEvent {
    }

    public static class ConnectivityChangeEvent {
    }
    //endregion

    /**
     *
     * Enviar comando
     */

    //private Aircraft mAircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
    //private FlightController mFlightController = mAircraft.getFlightController();

    private FlightController mFlightController;
    private EditText comandoEnviado;

    private int test = 1;


    private void communicateWithOnboardSDK(BaseProduct mProduct) {
        //BaseProduct mProduct = DJISampleApplication.getProductInstance();

        showToast("Entrou");
        mFlightController = ((Aircraft)mProduct).getFlightController();

        /**
        Button saveButton = (Button) findViewById(R.id.btn_Salvar);
        saveButton.setOnClickListener(saveButtonListener);

        Button stopButton = (Button) findViewById(R.id.btn_stop);
        stopButton.setOnClickListener(stopButtonListener);

        Button scanButton = (Button) findViewById(R.id.btn_Scan);
        scanButton.setOnClickListener(scanButtonListener);

        Button refreshButton = (Button) findViewById(R.id.btn_Refresh);
        refreshButton.setOnClickListener(refreshButtonListener);
*/

        Button sendButton = (Button) findViewById(R.id.btn_send);
        sendButton.setOnClickListener(sendButtonListener);
        comandoEnviado = (EditText) findViewById(R.id.editText_Comando);

        /** if (test == 1) {
            showToast("Sending data");
            mFlightController.sendDataToOnboardSDKDevice("A".getBytes(), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.toString());
                    } else{
                        showToast("Deu bom pvt");
                    }
                }
            });
        } else {
            showToast("Receiving data");
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

        } */

    }

    public View.OnClickListener sendButtonListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            //Testa se tem algo digitado no comando enviado
            if(comandoEnviado.getText().length()>0){
                showToast("Sending data");
                mFlightController.sendDataToOnboardSDKDevice(comandoEnviado.getText().toString().getBytes(), new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast(djiError.toString());
                        } else{
                            showToast("Deu bom pvt");
                        }
                    }
                });
                comandoEnviado.setText(""); //limpa o comando enviado

            } else{
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.missingData);
                builder.setPositiveButton(R.string.ok,null);
                builder.setMessage(R.string.missingMsg);
                AlertDialog errorDialog = builder.create();
                errorDialog.show();
            }
        }
    };

    public OnClickListener scanButtonListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            showToast("Starting Scan");
            mFlightController.sendDataToOnboardSDKDevice("I".getBytes(), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.toString());
                    } else{
                        //testaRecebimento(1);
                    }
                }
            });
        }
    };

    public OnClickListener stopButtonListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            showToast("Stopping Scan");
            mFlightController.sendDataToOnboardSDKDevice("T".getBytes(), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.toString());
                    } else{
                        //testaRecebimento(1);
                    }
                }
            });
        }
    };

    public OnClickListener refreshButtonListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            showToast("Cleaning Scan");
            mFlightController.sendDataToOnboardSDKDevice("R".getBytes(), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.toString());
                    } else{
                        //testaRecebimento(1);
                    }
                }
            });
        }
    };

    public OnClickListener saveButtonListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            showToast("Saving Scan");
            mFlightController.sendDataToOnboardSDKDevice("S".getBytes(), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.toString());
                    } else{
                        //testaRecebimento(1);
                    }
                }
            });
        }
    };

/**
    public void testaRecebimento(int index) {
        test = index;
        while (test == 1) {
            try {
                Thread.sleep(2000); // 2 segundo
            } catch (InterruptedException e) {
                System.out.println("Got Interrupted!");
            }
            mFlightController.setOnboardSDKDeviceDataCallback(new FlightController.OnboardSDKDeviceDataCallback() {
                @Override
                public void onReceive(byte[] bytes) {
                    String text = new String(bytes);
                    showToast(text); //Apresenta o texto recebido
                    if (text.equals("OK")) {
                        test = 2;
                    }
                }
            });
        }
    }



    public View.OnClickListener sendButtonListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            //Testa se tem algo digitado no comando enviado
            if(comandoEnviado.getText().length()>0){
                showToast("Sending data");
                mFlightController.sendDataToOnboardSDKDevice(comandoEnviado.getText().toString().getBytes(), new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast(djiError.toString());
                        } else{
                            showToast("Deu bom pvt");
                        }
                    }
                });
                comandoEnviado.setText(""); //limpa o comando enviado

            } else{
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.missingData);
                builder.setPositiveButton(R.string.ok,null);
                builder.setMessage(R.string.missingMsg);
                AlertDialog errorDialog = builder.create();
                errorDialog.show();
            }
        }
    };  */

}
