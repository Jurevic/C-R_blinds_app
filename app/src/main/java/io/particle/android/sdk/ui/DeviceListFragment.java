package io.particle.android.sdk.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.design.widget.Snackbar.Callback;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.getbase.floatingactionbutton.AddFloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.common.collect.Lists;
import com.tumblr.bookends.Bookends;

import org.apache.commons.collections4.comparators.BooleanComparator;
import org.apache.commons.collections4.comparators.ComparatorChain;
import org.apache.commons.collections4.comparators.NullComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import io.particle.android.sdk.DevicesLoader;
import io.particle.android.sdk.DevicesLoader.DevicesLoadResult;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.devicesetup.ParticleDeviceSetupLibrary;
import io.particle.android.sdk.devicesetup.ParticleDeviceSetupLibrary.DeviceSetupCompleteReceiver;
import io.particle.android.sdk.ui.ItemClickSupport.OnItemClickListener;
import io.particle.android.sdk.utils.EZ;
import io.particle.android.sdk.utils.TLog;
import io.particle.android.sdk.utils.ui.Toaster;
import io.particle.android.sdk.utils.ui.Ui;
import io.particle.sdk.app.R;

import static io.particle.android.sdk.utils.Py.list;
import static io.particle.android.sdk.utils.Py.truthy;


@ParametersAreNonnullByDefault
public class DeviceListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<DevicesLoadResult> {


    public interface Callbacks {
        void onDeviceSelected(ParticleDevice device, String deviceType);
    }


    private static final TLog log = TLog.get(DeviceListFragment.class);

    // A no-op impl of {@link Callbacks}. Used when this fragment is not attached to an activity.
    private static final Callbacks dummyCallbacks = new Callbacks() {
        @Override
        public void onDeviceSelected(ParticleDevice device, String deviceType) {
            // no-op
        }
    };

    private SwipeRefreshLayout refreshLayout;
    private FloatingActionsMenu fabMenu;
    private DeviceListAdapter adapter;
    private Bookends<DeviceListAdapter> bookends;
    // FIXME: naming, document better
    private ProgressBar partialContentBar;
    private boolean isLoadingSnackbarVisible;

    private final ReloadStateDelegate reloadStateDelegate = new ReloadStateDelegate();
    private final Comparator<ParticleDevice> comparator = new HelpfulOrderDeviceComparator();

    private Callbacks callbacks = dummyCallbacks;
    private DeviceSetupCompleteReceiver deviceSetupCompleteReceiver;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callbacks = EZ.getCallbacksOrThrow(this, Callbacks.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View top = inflater.inflate(R.layout.fragment_device_list2, container, false);

        RecyclerView rv = Ui.findView(top, R.id.device_list);
        rv.setHasFixedSize(true);  // perf. optimization
        LinearLayoutManager layoutManager = new LinearLayoutManager(inflater.getContext());
        rv.setLayoutManager(layoutManager);

        @SuppressLint("InflateParams")
        View myHeader = inflater.inflate(R.layout.device_list_header, null);
        myHeader.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        partialContentBar = (ProgressBar) inflater.inflate(R.layout.device_list_footer, null);
        partialContentBar.setVisibility(View.INVISIBLE);
        partialContentBar.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        adapter = new DeviceListAdapter(getActivity());
        // Add them as headers / footers
        bookends = new Bookends<>(adapter);
        bookends.addHeader(myHeader);
        bookends.addFooter(partialContentBar);

        rv.setAdapter(bookends);

        ItemClickSupport.addTo(rv).setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                // subtracting 1 from position because of header.  This is gross, but it's simple
                // and in this case adequate, so #SHIPIT.
                onDeviceRowClicked(recyclerView, position - 1, v);
            }
        });

        return top;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fabMenu = Ui.findView(view, R.id.add_device_fab);
        AddFloatingActionButton addDevice = Ui.findView(view, R.id.action_set_up_a_device);

        addDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addPhotonDevice();
                fabMenu.collapse();
            }
        });


        refreshLayout = Ui.findView(view, R.id.refresh_layout);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshDevices();
            }
        });

        deviceSetupCompleteReceiver = new ParticleDeviceSetupLibrary.DeviceSetupCompleteReceiver() {
            @Override
            public void onSetupSuccess(String id) {
                log.d("Successfully set up " + id);
            }

            @Override
            public void onSetupFailure() {
                log.w("Device not set up.");
            }
        };
        deviceSetupCompleteReceiver.register(getActivity());

        getLoaderManager().initLoader(R.id.device_list_devices_loader_id, null, this);
        refreshLayout.setRefreshing(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshDevices();
    }

    @Override
    public void onStop() {
        super.onStop();
        refreshLayout.setRefreshing(false);
        fabMenu.collapse();
        reloadStateDelegate.reset();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = dummyCallbacks;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        deviceSetupCompleteReceiver.unregister(getActivity());
    }

    @Override
    public Loader<DevicesLoadResult> onCreateLoader(int i, Bundle bundle) {
        return new DevicesLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<DevicesLoadResult> loader, DevicesLoadResult result) {
        refreshLayout.setRefreshing(false);

        ArrayList<ParticleDevice> devices = Lists.newArrayList(result.devices);
        Collections.sort(devices, comparator);

        reloadStateDelegate.onDeviceLoadFinished(loader, result);

        adapter.clear();
        adapter.addAll(devices);
        bookends.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<DevicesLoadResult> loader) {
        // no-op
    }

    private void onDeviceRowClicked(RecyclerView recyclerView, int position, View view) {
        log.i("Clicked on item at position: #" + position);
        if (position >= bookends.getItemCount() || position == -1) {
            // we're at the header or footer view, do nothing.
            return;
        }

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        final ParticleDevice device = adapter.getItem(position);

        if (device.isFlashing()) {
            Toaster.s(getActivity(),
                    "Device is being flashed, please wait for the flashing process to end first");

        } else if (!device.isConnected()) {
            new AlertDialog.Builder(getActivity())
                    .setTitle("Device offline")
                    .setMessage(R.string.err_msg_device_is_offline)
                    .setPositiveButton(R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();

        } else if (!isRunningCollectReflect(device)) {
            new AlertDialog.Builder(getActivity())
                    .setTitle("Device firmwire is corrupt")
                    .setMessage("This device is not running Collect & Reflect firmware.")
                    .setPositiveButton("Re-flash firmwire", new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DeviceActionsHelper.takeActionForDevice(
                                    R.id.action_device_reflash, getActivity(), device);
                        }
                    })
                    .setNegativeButton("Cancel", new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();

        } else {
            if(isRunningCollectReflectBlinds(device)) callbacks.onDeviceSelected(device, "blinds");
            else if(isRunningCollectReflectClimate(device)) callbacks.onDeviceSelected(device, "climate");
        }
    }

    public boolean onBackPressed() {
        if (fabMenu.isExpanded()) {
            fabMenu.collapse();
            return true;
        } else {
            return false;
        }
    }

    private void addPhotonDevice() {
        ParticleDeviceSetupLibrary.startDeviceSetup(getActivity());
    }

    static public boolean isRunningCollectReflectClimate(ParticleDevice device){
        return device.getVariables().containsKey("CRC_v1.0");
    }

    static public boolean isRunningCollectReflectBlinds(ParticleDevice device){
        return device.getVariables().containsKey("CRB_v1.0");
    }

    static public boolean isRunningCollectReflect(ParticleDevice device){
        return isRunningCollectReflectBlinds(device) || isRunningCollectReflectClimate(device);
    }

    private void refreshDevices() {
        Loader<Object> loader = getLoaderManager().getLoader(R.id.device_list_devices_loader_id);
        loader.forceLoad();
    }


    static class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {

        static class ViewHolder extends RecyclerView.ViewHolder {

            final View topLevel;
            final TextView modelName;
            final ImageView productImage;
            final TextView deviceName;
            final TextView statusTextWithIcon;
            final TextView productId;
            final ImageView overflowMenuIcon;

            public ViewHolder(View itemView) {
                super(itemView);
                topLevel = itemView;
                modelName = Ui.findView(itemView, R.id.product_model_name);
                productImage = Ui.findView(itemView, R.id.product_image);
                deviceName = Ui.findView(itemView, R.id.product_name);
                statusTextWithIcon = Ui.findView(itemView, R.id.online_status);
                productId = Ui.findView(itemView, R.id.product_id);
                overflowMenuIcon = Ui.findView(itemView, R.id.context_menu);
            }
        }


        private final List<ParticleDevice> devices = list();
        private final FragmentActivity activity;
        private Drawable defaultBackground;

        DeviceListAdapter(FragmentActivity activity) {
            this.activity = activity;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // create a new view
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.row_device_list, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final ParticleDevice device = devices.get(position);

            if (defaultBackground == null) {
                defaultBackground = holder.topLevel.getBackground();
            }

            if (position % 2 == 0) {
                holder.topLevel.setBackgroundResource(R.color.shaded_background);
            } else {
                if (VERSION.SDK_INT >= 16) {
                    holder.topLevel.setBackground(defaultBackground);
                } else {
                    holder.topLevel.setBackgroundDrawable(defaultBackground);
                }
            }

            // Determine Collect Reflect device type
            if(isRunningCollectReflectBlinds(device)){
                holder.modelName.setText(holder.modelName.getContext().getString(R.string.blinds_name));
                holder.productImage.setImageResource(R.drawable.blinds_vector_small);
            }
            else if(isRunningCollectReflectClimate(device)){
                holder.modelName.setText(holder.modelName.getContext().getString(R.string.climate_name));
                holder.productImage.setImageResource(R.drawable.microclimate_vector_small);
            }
            else{
                holder.modelName.setText(holder.modelName.getContext().getString(R.string.unknown_name));
                holder.productImage.setImageResource(R.drawable.not_recognised_vector_small);
            }

            Pair<String, Integer> statusTextAndColoredDot = getStatusTextAndColoredDot(device);
            holder.statusTextWithIcon.setText(statusTextAndColoredDot.first);
            holder.statusTextWithIcon.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, statusTextAndColoredDot.second, 0);

            holder.productId.setText(device.getID().toUpperCase());

            Context ctx = holder.topLevel.getContext();
            String name = truthy(device.getName())
                    ? device.getName()
                    : ctx.getString(R.string.unnamed_device);
            holder.deviceName.setText(name);

            holder.overflowMenuIcon.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            showMenu(view, device);
                        }
                    }
            );
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        void clear() {
            devices.clear();
            notifyDataSetChanged();
        }

        void addAll(List<ParticleDevice> toAdd) {
            devices.addAll(toAdd);
            notifyDataSetChanged();
        }

        ParticleDevice getItem(int position) {
            return devices.get(position);
        }

        private void showMenu(View v, final ParticleDevice device) {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.inflate(R.menu.context_device_row);
            popup.setOnMenuItemClickListener(DeviceActionsHelper.buildPopupMenuHelper(activity, device));
            popup.show();
        }

        private Pair<String, Integer> getStatusTextAndColoredDot(ParticleDevice device) {
            int dot;
            String msg;
            if (device.isFlashing()) {
                dot = R.drawable.device_flashing_dot;
                msg = "Flashing";

            } else if (device.isConnected()) {
                if (isRunningCollectReflect(device)) {
                    dot = R.drawable.online_dot;
                    msg = "Online";

                } else {
                    dot = R.drawable.online_non_collect_reflect_dot;
                    msg = "Online, corrupt";
                }

            } else {
                dot = R.drawable.offline_dot;
                msg = "Offline";
            }
            return Pair.create(msg, dot);
        }
    }


    static class DeviceOnlineStatusComparator implements Comparator<ParticleDevice> {

        @Override
        public int compare(ParticleDevice lhs, ParticleDevice rhs) {
            return BooleanComparator.getTrueFirstComparator().compare(
                    lhs.isConnected(), rhs.isConnected());
        }
    }


    static class UnnamedDevicesFirstComparator implements Comparator<ParticleDevice> {

        private final NullComparator<String> nullComparator = new NullComparator<>(false);

        @Override
        public int compare(ParticleDevice lhs, ParticleDevice rhs) {
            String lhname = lhs.getName();
            String rhname = rhs.getName();
            return nullComparator.compare(lhname, rhname);
        }
    }


    static class HelpfulOrderDeviceComparator extends ComparatorChain<ParticleDevice> {

        HelpfulOrderDeviceComparator() {
            super(new DeviceOnlineStatusComparator(), false);
            this.addComparator(new UnnamedDevicesFirstComparator(), false);
        }
    }


    class ReloadStateDelegate {

        static final int MAX_RETRIES = 10;

        int retryCount = 0;

        void onDeviceLoadFinished(final Loader<DevicesLoadResult> loader, DevicesLoadResult result) {
            if (!result.isPartialResult) {
                reset();
                return;
            }

            retryCount++;
            if (retryCount > MAX_RETRIES) {
                // tried too many times, giving up. :(
                partialContentBar.setVisibility(View.INVISIBLE);
                return;
            }

            if (!isLoadingSnackbarVisible) {
                isLoadingSnackbarVisible = true;
                Snackbar.make(getView(), "Unable to load all devices", Snackbar.LENGTH_SHORT)
                        .setCallback(new Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                super.onDismissed(snackbar, event);
                                isLoadingSnackbarVisible = false;
                            }
                        }).show();
            }

            partialContentBar.setVisibility(View.VISIBLE);
            ((DevicesLoader) loader).setUseLongTimeoutsOnNextLoad(true);
            // FIXME: is it OK to call forceLoad() in loader callbacks?  Test and be certain.
            EZ.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (isResumed()) {
                        loader.forceLoad();
                    }
                }
            });
        }

        void reset() {
            retryCount = 0;
            partialContentBar.setVisibility(View.INVISIBLE);
        }

    }

}
