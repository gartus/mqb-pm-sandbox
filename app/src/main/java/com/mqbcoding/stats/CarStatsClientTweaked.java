package com.mqbcoding.stats;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.github.martoreto.aauto.vex.FieldSchema;
import com.github.martoreto.aauto.vex.ICarStats;
import com.github.martoreto.aauto.vex.ICarStatsListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CarStatsClientTweaked {
    private static final String TAG = "CarStatsClient";
    private static final String ACTION_CAR_STATS_PROVIDER = "com.github.martoreto.aauto.vex.CAR_STATS_PROVIDER";
    private Context mContext;
    private Map<String, ServiceConnection> mServiceConnections = new HashMap();
    private Map<String, ICarStats> mProviders = new HashMap();
    private List<String> mProvidersByPriority = new ArrayList();
    private Map<String, String> mProvidersByKey = new HashMap();
    private Map<String, ICarStatsListener> mRemoteListeners = new HashMap();
    private List<com.github.martoreto.aauto.vex.CarStatsClient.Listener> mListeners = new ArrayList();
    private Map<String, FieldSchema> mSchema = Collections.emptyMap();

    public CarStatsClientTweaked(Context context) {
        this.mContext = context;
    }

    public void start() {
        Iterator var1 = getProviderIntents(this.mContext).iterator();

        while(var1.hasNext()) {
            Intent i = (Intent)var1.next();
            String provider = i.getComponent().flattenToShortString();
            ServiceConnection sc = this.createServiceConnection(provider);
            this.mServiceConnections.put(provider, sc);
            this.mProvidersByPriority.add(provider);
            Log.d("CarStatsClient", "Binding to " + provider);
            this.mContext.bindService(i, sc, Context.BIND_AUTO_CREATE);
        }

    }

    private ServiceConnection createServiceConnection(final String provider) {
        return new ServiceConnection() {
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Log.v("CarStatsClient", "Connected to " + provider);
                ICarStats stats = ICarStats.Stub.asInterface(iBinder);
                mProviders.put(provider, stats);
                ICarStatsListener listener = createListener(provider);
                mRemoteListeners.put(provider, listener);

                try {
                    stats.registerListener(listener);
                } catch (RemoteException var6) {
                    Log.w("CarStatsClient", provider + ": Error registering listener", var6);
                }

                updateSchema();
            }

            public void onServiceDisconnected(ComponentName componentName) {
                Log.v("CarStatsClient", "Disconnected from " + provider);
                mProviders.remove(provider);
            }
        };
    }

    private ICarStatsListener createListener(final String provider) {
        return new com.github.martoreto.aauto.vex.ICarStatsListener.Stub() {
            public void onNewMeasurements(long timestamp, Map values) throws RemoteException {
                Iterator var4 = mListeners.iterator();

                while(var4.hasNext()) {
                    com.github.martoreto.aauto.vex.CarStatsClient.Listener listener = (com.github.martoreto.aauto.vex.CarStatsClient.Listener)var4.next();

                    try {
                        listener.onNewMeasurements(provider, new Date(timestamp), filterValues(provider, values));
                    } catch (Exception var7) {
                        Log.e("CarStatsClient", "Error calling listener", var7);
                    }
                }

            }

            public void onSchemaChanged() throws RemoteException {
                updateSchema();
            }
        };
    }

    private Map<String, Object> filterValues(String provider, Map<String, Object> values) {
        Map<String, String> providersByKey = this.mProvidersByKey;
        Iterator iter = values.keySet().iterator();

        while(iter.hasNext()) {
            if (!provider.equals(providersByKey.get(iter.next()))) {
                iter.remove();
            }
        }

        return values;
    }

    private synchronized void updateSchema() {
        Map<String, FieldSchema> schema = new HashMap();
        Map<String, String> providersByKey = new HashMap();
        Iterator var3 = this.mProvidersByPriority.iterator();

        while(var3.hasNext()) {
            String provider = (String)var3.next();

            try {
                ICarStats providerInterface = (ICarStats)this.mProviders.get(provider);
                if (providerInterface != null) {
                    Map<String, FieldSchema> providerSchema = providerInterface.getSchema();
                    Iterator var7 = providerSchema.keySet().iterator();

                    while(var7.hasNext()) {
                        String key = (String)var7.next();
                        if (!providersByKey.containsKey(key)) {
                            providersByKey.put(key, provider);
                        }
                    }

                    schema.putAll(providerSchema);
                }
            } catch (RemoteException var9) {
                Log.w("CarStatsClient", provider + ": Error getting schema", var9);
            }
        }

        this.mProvidersByKey = providersByKey;
        this.mSchema = schema;
        this.dispatchSchemaChanged();
    }

    private void dispatchSchemaChanged() {
        Iterator var1 = this.mListeners.iterator();

        while(var1.hasNext()) {
            com.github.martoreto.aauto.vex.CarStatsClient.Listener listener = (com.github.martoreto.aauto.vex.CarStatsClient.Listener)var1.next();

            try {
                listener.onSchemaChanged();
            } catch (Exception var4) {
                Log.e("CarStatsClient", "Error calling listener", var4);
            }
        }

    }

    public void forceUpdateMeasurements() {

        Iterator var4 = this.mListeners.iterator();

        while(var4.hasNext()) {
            CarStatsClientTweaked.Listener listener = (CarStatsClientTweaked.Listener)var4.next();
            try {
                listener.onNewMeasurements("Tweaked", new Date(), getMergedMeasurements());
            } catch (Exception var7) {
                Log.e("CarStatsClient", "Error calling listener", var7);
            }
        }
    }

    public void stop() {
        Iterator var1 = this.mProviders.entrySet().iterator();

        while(var1.hasNext()) {
            Map.Entry e = (Map.Entry)var1.next();

            try {
                ((ICarStats)e.getValue()).unregisterListener((ICarStatsListener)this.mRemoteListeners.get(e.getKey()));
            } catch (RemoteException var4) {
                Log.w("CarStatsClient", (String)e.getKey() + ": Error unregistering listener", var4);
            }
        }

        var1 = this.mServiceConnections.values().iterator();

        while(var1.hasNext()) {
            ServiceConnection sc = (ServiceConnection)var1.next();
            this.mContext.unbindService(sc);
        }

        this.mProviders.clear();
        this.mProvidersByPriority.clear();
        this.mProvidersByKey.clear();
        this.mRemoteListeners.clear();
        this.mServiceConnections.clear();
    }

    public Map<String, Object> getMergedMeasurements() {
        Map<String, Object> measurements = new HashMap();
        Iterator var2 = this.mProviders.entrySet().iterator();

        while(var2.hasNext()) {
            Map.Entry<String, ICarStats> e = (Map.Entry)var2.next();
            String provider = (String)e.getKey();

            try {
                measurements.putAll(this.filterValues(provider, ((ICarStats)e.getValue()).getMergedMeasurements()));
            } catch (RemoteException var6) {
                Log.w("CarStatsClient", provider + ": Error getting measurements", var6);
            }
        }

        return measurements;
    }

    public synchronized Map<String, FieldSchema> getSchema() {
        return Collections.unmodifiableMap(this.mSchema);
    }

    public void registerListener(com.github.martoreto.aauto.vex.CarStatsClient.Listener listener) {
        this.mListeners.add(listener);
    }

    public void unregisterListener(com.github.martoreto.aauto.vex.CarStatsClient.Listener listener) {
        this.mListeners.remove(listener);
    }

    public static Collection<ResolveInfo> getProviderInfos(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent implicitIntent = new Intent("com.github.martoreto.aauto.vex.CAR_STATS_PROVIDER");
        return pm.queryIntentServices(implicitIntent, 0);
    }

    public static Collection<Intent> getProviderIntents(Context context) {
        Collection<ResolveInfo> resolveInfos = getProviderInfos(context);
        List<Intent> intents = new ArrayList(resolveInfos.size());
        Iterator var3 = resolveInfos.iterator();

        while(var3.hasNext()) {
            ResolveInfo ri = (ResolveInfo)var3.next();
            ComponentName cn = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
            Intent explicitIntent = new Intent("com.github.martoreto.aauto.vex.CAR_STATS_PROVIDER");
            explicitIntent.setComponent(cn);
            intents.add(explicitIntent);
        }

        return intents;
    }

    public static void requestPermissions(final Context context) {
        Iterator var1 = getProviderIntents(context).iterator();

        while(var1.hasNext()) {
            final Intent i = (Intent)var1.next();
            ServiceConnection sc = new ServiceConnection() {
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    ICarStats stats = ICarStats.Stub.asInterface(iBinder);

                    try {
                        if (stats.needsPermissions()) {
                            stats.requestPermissions();
                        }
                    } catch (RemoteException var6) {
                        String provider = i.getComponent().flattenToShortString();
                        Log.w("CarStatsClient", provider + ": Error requesting permissions", var6);
                    }

                    context.unbindService(this);
                }

                public void onServiceDisconnected(ComponentName componentName) {
                }
            };
            context.bindService(i, sc, Context.BIND_AUTO_CREATE);
        }

    }

    public interface Listener {
        void onNewMeasurements(String var1, Date var2, Map<String, Object> var3);

        void onSchemaChanged();
    }
}
