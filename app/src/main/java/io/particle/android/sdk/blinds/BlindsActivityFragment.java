package io.particle.android.sdk.blinds;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


import com.f2prateek.bundler.FragmentBundlerCompat;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.hoang8f.android.segmented.SegmentedGroup;
import io.particle.android.sdk.circular_seek_bar.CircularSeekBar;
import io.particle.android.sdk.cloud.BroadcastContract;
import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.ParticleCloudSDK;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.cloud.ParticleEventVisibility;
import io.particle.android.sdk.utils.Async;
import io.particle.android.sdk.utils.Toaster;
import io.particle.sdk.app.R;

import static io.particle.android.sdk.utils.Py.list;
import static io.particle.android.sdk.utils.Py.truthy;


public class BlindsActivityFragment extends Fragment{

    public static BlindsActivityFragment newInstance(ParticleDevice device) {
        return FragmentBundlerCompat.create(new BlindsActivityFragment())
                .put(BlindsActivityFragment.ARG_DEVICE, device)
                .build();
    }

    // The device that this fragment represents
    public static final String ARG_DEVICE = "ARG_DEVICE";

    private ParticleDevice device;
    private DevicesUpdatedListener devicesUpdatedListener = new DevicesUpdatedListener();
    private boolean autoModeOn = false;
    private boolean startAngleGetOnce = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        device = getArguments().getParcelable(ARG_DEVICE);
    }


    @Override
    public void onStart() {
        super.onStart();
        updateTitle();
        updateBlinds();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                devicesUpdatedListener, devicesUpdatedListener.buildIntentFilter());
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(
                devicesUpdatedListener);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_blinds, container, false);

        ImageView blindsAngleImage = (ImageView) view.findViewById(R.id.blindsAngleImage);
        blindsAngleImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // if auto mode is on disable it by rotating slats to current angle.
                if(autoModeOn) {
                    View view = getView();
                    if(view == null) return;
                    CircularSeekBar seekbar = (CircularSeekBar) view.findViewById(R.id.rotationAngleSeekbar);
                    setBlindsRotationAngle(seekbar.getProgress());
                    Toaster.l(getActivity(), "Auto mode is off");
                }
                else {
                    switch (getScopeSelectionIndex()) {
                        case 0:
                            publishBlindsTask(getDeviceGroup(device), "auto");
                            break;
                        case 1:
                            setBlindsAuto();
                            break;
                        case 2:
                            publishBlindsTask("all", "auto");
                            break;
                    }
                }
            }
        });

        setupCircularSeekBar(view);

        return view;
    }

    private void setupCircularSeekBar(View view){
        CircularSeekBar seekbar = (CircularSeekBar) view.findViewById(R.id.rotationAngleSeekbar);
        seekbar.setMax(180);
        seekbar.setOnSeekBarChangeListener(new CircularSeekBarListener());
    }

    private class CircularSeekBarListener implements CircularSeekBar.OnCircularSeekBarChangeListener {
        @Override
        public void onProgressChanged(CircularSeekBar circularSeekBar, int progress, boolean fromUser) {
            updateSlatImage(progress);
        }

        @Override
        public void onStartTrackingTouch(CircularSeekBar circularSeekBar) {
            // Do nothing
        }

        @Override
        public void onStopTrackingTouch(CircularSeekBar circularSeekBar) {
            switch(getScopeSelectionIndex()){
                case 0:
                    publishBlindsTask(getDeviceGroup(device), Integer.toString(circularSeekBar.getProgress()));
                    break;
                case 1:
                    setBlindsRotationAngle(circularSeekBar.getProgress());
                    break;
                case 2:
                    publishBlindsTask("all", Integer.toString(circularSeekBar.getProgress()));
                    break;
            }
        }
    }

    private void updateSlatImage(int slatAngle){
        View view = getView();
        if(view == null) return;
        ImageView slatImage = (ImageView) view.findViewById(R.id.blindsAngleImage);
        slatImage.setRotation(slatAngle);
    }

    private void updateTitle() {
        String name = truthy(device.getName()) ? device.getName() : "(Unnamed device)";
        getActivity().setTitle(name);
    }

    private class DevicesUpdatedListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitle();
        }

        IntentFilter buildIntentFilter() {
            return new IntentFilter(BroadcastContract.BROADCAST_DEVICES_UPDATED);
        }
    }

    private void setBlindsRotationAngle(final Integer myAngle){   // Rotation angle 0 to 180
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, Integer>() {
            int resultCode;

            @Override
            public Integer callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    resultCode = particleDevice.callFunction("rotate", list(Integer.toString(myAngle)));
                } catch (IOException | ParticleCloudException | ParticleDevice.FunctionDoesNotExistException error) {
                    return null;
                }
                return resultCode;
            }

            @Override
            public void onSuccess(@Nullable Integer value) {
                if(value == null) return;

                updateBlinds();
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Log.e("setBlindsRotationAngle", "Cannot rotate slats", e);
            }
        });
    }

    private void setBlindsAuto(){   // Turns on auto mode on this device
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, Integer>() {
            int resultCode;

            @Override
            public Integer callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    resultCode = particleDevice.callFunction("auto", list(""));
                } catch (IOException | ParticleCloudException | ParticleDevice.FunctionDoesNotExistException error) {
                    return null;
                }
                return resultCode;
            }

            @Override
            public void onSuccess(@Nullable Integer value) {
                if (value == null) return;

                Toaster.l(getActivity(), "Auto mode is set");
                updateBlinds();
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Log.e("setBlindsAuto", "Cannot set auto", e);
            }
        });
    }

    // Returns selected scope index
    private int getScopeSelectionIndex(){
        View view = getView();
        if(view == null) return -1;
        SegmentedGroup scopeSelectionGroup = (SegmentedGroup) view.findViewById(R.id.scopeSelection);
        return scopeSelectionGroup.indexOfChild(getView().findViewById(scopeSelectionGroup.getCheckedRadioButtonId()));
    }

    private String getDeviceGroup(ParticleDevice device){
        String groupName;

        Map<String, ParticleDevice.VariableType> varNames = device.getVariables();
        for (String name : varNames.keySet()) {
            if(name.startsWith("g_")){
                // Name after g_ is groupName
                groupName = name.substring(2);
                return groupName;
            }
        }
        // Not found group
        return null;
    }

    private void publishBlindsTask(final String groupName, final String task){
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, Boolean>() {

            @Override
            public Boolean callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    ParticleCloudSDK.getCloud().publishEvent("blinds", groupName + " " + task, ParticleEventVisibility.PRIVATE, 60);
                } catch (ParticleCloudException e) {
                    return null;
                }
                return true;
            }

            @Override
            public void onSuccess(@Nullable Boolean value) {
                if(value == null) return;
                updateBlinds();
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Log.e("PublishBlindsTask", "Could not publish specified task", e);
            }
        });
    }

    private void updateBlinds(){
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, String>() {
            String status;

            @Override
            public String callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    status = particleDevice.getStringVariable("status");
                } catch (IOException | ParticleCloudException | ParticleDevice.VariableDoesNotExistException error) {
                    Log.e("updateBlinds", "Cannot update data, exception occurred");
                    return null;
                }
                return status;
            }

            @Override
            public void onSuccess(@Nullable String value) {
                if(value == null) return;
                updateBlindsView(value);
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Log.e("updateBlinds", "Failed to get status variable from cloud.");
                Toaster.l(getActivity(), getString(R.string.blinds_error_cannot_get_blinds));
            }
        });
    }

    private void updateBlindsView(String blindsValue){
        if(blindsValue == null){
            Toaster.l(getActivity(), getString(R.string.blinds_error_cannot_get_blinds));
            return;
        }

        View view = getView();
        if(view == null) return;

        ImageView slatsImage = (ImageView)view.findViewById(R.id.blindsAngleImage);
        CircularSeekBar seekbar = (CircularSeekBar) view.findViewById(R.id.rotationAngleSeekbar);

        // Regular expressions decoding
        String ri="(\\d+)"; // Regex for integer
        String rn=".*?";  // Non-greedy match on filler

        Pattern p = Pattern.compile(ri+rn+ri, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(blindsValue);

        if (m.find()){

            if(m.group(2).equals("2")){
                // Auto mode is on
                // Make blinds image transparent, seek bar locked and red
                slatsImage.setImageAlpha(100);
                seekbar.setCircleProgressColor(ContextCompat.getColor(getContext(), R.color.hot));
                seekbar.setPointerColor(ContextCompat.getColor(getContext(), R.color.hot));
                seekbar.setProgress(Integer.parseInt(m.group(1)));
                seekbar.setIsTouchEnabled(false);
                autoModeOn = true;
            } else{
                // Auto mode is off
                slatsImage.setImageAlpha(255);
                seekbar.setCircleProgressColor(ContextCompat.getColor(getContext(), R.color.primary_color));
                seekbar.setPointerColor(ContextCompat.getColor(getContext(), R.color.primary_color));
                seekbar.setIsTouchEnabled(true);
                autoModeOn = false;

                // Do once on startup
                if(!startAngleGetOnce) {
                    seekbar.setProgress(Integer.parseInt(m.group(1)));
                    startAngleGetOnce = true;
                }
            }
        }
    }
}

