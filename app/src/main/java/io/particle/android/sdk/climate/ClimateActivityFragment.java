package io.particle.android.sdk.climate;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.f2prateek.bundler.FragmentBundlerCompat;


import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.particle.android.sdk.cloud.BroadcastContract;
import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.utils.Async;
import io.particle.android.sdk.utils.Toaster;
import io.particle.sdk.app.R;

import static io.particle.android.sdk.utils.Py.list;
import static io.particle.android.sdk.utils.Py.truthy;


public class ClimateActivityFragment extends Fragment{

    public static ClimateActivityFragment newInstance(ParticleDevice device) {
        return FragmentBundlerCompat.create(new ClimateActivityFragment())
                .put(ClimateActivityFragment.ARG_DEVICE, device)
                .build();
    }

    // The device that this fragment represents
    public static final String ARG_DEVICE = "ARG_DEVICE";

    private ParticleDevice device;
    private DevicesUpdatedListener devicesUpdatedListener = new DevicesUpdatedListener();

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
        updateClimate();
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
        View view=inflater.inflate(R.layout.fragment_climate,container,false);

        ImageView temperatureImage = (ImageView) view.findViewById(R.id.imageTemperature);
        ImageView lightImage = (ImageView) view.findViewById(R.id.imageLight);
        ImageView humidityImage = (ImageView) view.findViewById(R.id.imageHumidity);
        ImageView pressureImage = (ImageView) view.findViewById(R.id.imagePressure);
        RelativeLayout temperatureRow = (RelativeLayout) view.findViewById(R.id.temperature_row);
        RelativeLayout lightRow = (RelativeLayout) view.findViewById(R.id.light_row);
        RelativeLayout humidityRow = (RelativeLayout) view.findViewById(R.id.humidity_row);
        RelativeLayout pressureRow = (RelativeLayout) view.findViewById(R.id.pressure_row);

        temperatureImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                climateSetPriority("temperature");
            }
        });

        lightImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                climateSetPriority("light");
            }
        });

        humidityImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                climateSetPriority("humidity");
            }
        });

        pressureImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                climateSetPriority("pressure");
            }
        });

        temperatureRow.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                buildEditPreferredDialog("temperature", getString(R.string.temperature_m_units), 1, 1, 1, 49);
            }
        });

        lightRow.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                buildEditPreferredDialog("light", getString(R.string.light_m_units), 1, 50, 50, 39);
            }
        });

        humidityRow.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                buildEditPreferredDialog("humidity", getString(R.string.humidity_m_units), 1, 10, 1, 80);
            }
        });

        pressureRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildEditPreferredDialog("pressure", getString(R.string.pressure_m_units), 1000, 90, 0.2f , 100);
            }
        });

        return view;
    }

    private void buildEditPreferredDialog(final String editValueName, final String editValueUnits,
                                          final int valueMultiplier, final int minValue,
                                          final float step, int numSteps){
        // Create adjust preferred values dialog
        final AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());

        alert.setTitle(String.format(getString(R.string.climate_alert_title), editValueName));
        alert.setMessage(String.format(getString(R.string.climate_alert_message), editValueName));

        LinearLayout linear=new LinearLayout(getActivity());

        linear.setOrientation(linear.VERTICAL);
        final TextView text=new TextView(getActivity());
        text.setText(String.format("%1$s %2$s", (float) minValue, editValueUnits));
        text.setTextSize(30);
        text.setGravity(Gravity.CENTER);
        text.setPadding(6, 6, 6, 6);

        final SeekBar seek=new SeekBar(getActivity());
        seek.setMax(numSteps);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update text
                text.setText(String.format("%1$s %2$s", (progress * step + minValue), editValueUnits));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Empty
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Empty
            }
        });

        linear.addView(seek);
        linear.addView(text);

        alert.setView(linear);

        alert.setPositiveButton("Set",new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog,int id)
            {
                // Set chosen value
                climateSetPreferredValue(editValueName, Math.round((seek.getProgress() * step + minValue) * valueMultiplier));
            }
        });

        alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog,int id)
            {
                // Do nothing
            }
        });

        alert.show();
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

    private void updateClimate(){
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, String>() {
            String status;

            @Override
            public String callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    status = particleDevice.getStringVariable("status");
                } catch (IOException | ParticleCloudException | ParticleDevice.VariableDoesNotExistException error) {
                    return null;
                }
                return status;
            }

            @Override
            public void onSuccess(@Nullable String value) {
                if(value == null) return;

                updateClimateView(value);
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Toaster.l(getActivity(), getString(R.string.climate_error_cannot_get_climate));
            }
        });
    }

    private void climateSetPriority(final String myPriority){
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, Integer>() {
            int resultCode;

            @Override
            public Integer callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    resultCode = particleDevice.callFunction("setup", list("priority " + myPriority));
                } catch (IOException | ParticleCloudException | ParticleDevice.FunctionDoesNotExistException error) {
                    return null;
                }
                return resultCode;
            }

            @Override
            public void onSuccess(@Nullable Integer value) {
                if (value == null) return;

                Toaster.l(getActivity(), String.format(getString(R.string.climate_priority_set_to), myPriority));
                updateClimate();
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Toaster.l(getActivity(), getString(R.string.climate_error_cannot_set_priority));
            }
        });
    }

    private void climateSetPreferredValue(final String myValueName, final Integer value){
        Async.executeAsync(device, new Async.ApiWork<ParticleDevice, Integer>() {
            int resultCode;

            @Override
            public Integer callApi(@NonNull ParticleDevice particleDevice)
                    throws ParticleCloudException, IOException {
                try {
                    resultCode = particleDevice.callFunction("setup", list(myValueName + " " + Integer.toString(value)));
                } catch (IOException | ParticleCloudException | ParticleDevice.FunctionDoesNotExistException error) {
                    return null;
                }
                return resultCode;
            }

            @Override
            public void onSuccess(@Nullable Integer value) {
                if (value == null) return;

                Toaster.l(getActivity(), getString(R.string.climate_preferred_value_is_set));
                updateClimate();
            }

            @Override
            public void onFailure(@NonNull ParticleCloudException e) {
                Toaster.l(getActivity(), getString(R.string.climate_error_cannot_set_preferred_value));
            }
        });
    }

    private void updateClimateView(String climateValue){
        if(climateValue == null){
            Toaster.l(getActivity(), getString(R.string.climate_error_cannot_get_climate));
            return;
        }

        View view = getView();
        if(view == null) return; //No view any more do nothing.

        TextView temperature = (TextView)view.findViewById(R.id.temperatureValue);
        TextView light = (TextView)view.findViewById(R.id.lightValue);
        TextView humidity = (TextView)view.findViewById(R.id.humidityValue);
        TextView pressure = (TextView)view.findViewById(R.id.pressureValue);
        TextView preferred_temperature = (TextView)view.findViewById(R.id.temperaturePreferred);
        TextView preferred_light = (TextView)view.findViewById(R.id.lightPreferred);
        TextView preferred_humidity = (TextView)view.findViewById(R.id.humidityPreferred);
        TextView preferred_pressure = (TextView)view.findViewById(R.id.pressurePreferred);


        // Regular expressions decoding
        String rf="([+-]?\\d*\\.\\d+)(?![-+0-9\\.])"; // Regex for float
        String rn=".*?";  // Non-greedy match on filler
        String ri="(\\d+)"; // Regex for integer
        String rw="((?:[a-z][a-z]+))"; // Regex for word

        Pattern p = Pattern.compile(rf+rn+ri+rn+rf+rn+rf+rn+ri+rn+ri+rn+ri+rn+ri+rn+rw, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(climateValue);
        if (m.find()){
            // Setup climate data strings
            String isStr = getString(R.string.climate_is);
            String preferredStr = getString(R.string.climate_preferred);

            temperature.setText(String.format(isStr, m.group(1), getString(R.string.temperature_m_units)));
            light.setText(String.format(isStr, m.group(2), getString(R.string.light_m_units)));
            humidity.setText(String.format(isStr, m.group(3), getString(R.string.humidity_m_units)));
            // For pressure convert to kPa
            Float pressureInkPA = Float.parseFloat(m.group(4)) / 1000;
            pressure.setText(String.format(isStr, String.format("%.2f", pressureInkPA), getString(R.string.pressure_m_units)));

            preferred_temperature.setText(String.format(preferredStr, m.group(5), getString(R.string.temperature_m_units)));
            preferred_light.setText(String.format(preferredStr, m.group(6), getString(R.string.light_m_units)));
            preferred_humidity.setText(String.format(preferredStr, m.group(7), getString(R.string.humidity_m_units)));
            // For pressure convert to kPa
            pressureInkPA = (float)(Integer.parseInt(m.group(8))) / 1000;
            preferred_pressure.setText(String.format(preferredStr, String.format("%.2f", pressureInkPA), getString(R.string.pressure_m_units)));

            // Set images
            ImageView temperatureImage = (ImageView) view.findViewById(R.id.imageTemperature);
            ImageView lightImage = (ImageView) view.findViewById(R.id.imageLight);
            ImageView humidityImage = (ImageView) view.findViewById(R.id.imageHumidity);
            ImageView pressureImage = (ImageView) view.findViewById(R.id.imagePressure);

            if(m.group(9).equals("temperature")) temperatureImage.setImageResource(R.drawable.temperature_vector_small_clicked);
            else  temperatureImage.setImageResource(R.drawable.temperature_vector_small);

            if(m.group(9).equals("light")) lightImage.setImageResource(R.drawable.light_vector_small_clicked);
            else lightImage.setImageResource(R.drawable.light_vector_small);

            if(m.group(9).equals("humidity")) humidityImage.setImageResource(R.drawable.humidity_vector_small_clicked);
            else humidityImage.setImageResource(R.drawable.humidity_vector_small);

            if(m.group(9).equals("pressure")) pressureImage.setImageResource(R.drawable.pressure_vector_small_clicked);
            else pressureImage.setImageResource(R.drawable.pressure_vector_small);

            // If none match set nothing, priority unknown or unset
        }


    }
}

