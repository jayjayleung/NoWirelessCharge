package com.wwm.nowirelesscharge;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class BYDWirelessCharger {
    private static final String TAG = "NoWirelessCharge";
    private static final Uri CAR_SETTINGS_USER_URI =
            Uri.parse("content://carsettings/settings");
    private static final Uri CAR_SETTINGS_SYSTEM_URI =
            Uri.parse("content://carsettings/system_settings");
    private static final String CAR_SETTINGS_KEY_WIRELESS_CHARGING = "wireless_charging";
    private static final String[] CAR_SETTINGS_SELECTION_ARGS =
            new String[]{CAR_SETTINGS_KEY_WIRELESS_CHARGING};

    /** Matches SystemUI WireChargeItem / framework BYDAutoChargingDevice. */
    private static final String CHARGING_DEVICE_CLASS =
            "android.hardware.bydauto.charging.BYDAutoChargingDevice";

    static public void turnOn(Context context)  { toggle(1, context); }
    static public void turnOff(Context context) { toggle(2, context); }

    static private void toggle(int state, Context context) {
        tryEnableHiddenApiExemption();

        Context ctx = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        StringBuilder routeFailures = new StringBuilder();

        // SystemUI uses the direct device API on this firmware, so try that first.
        if (invokeDirectSwitchSetter(state, ctx, routeFailures)) {
            return;
        }
        if (invokeLegacySet(state, ctx, routeFailures)) {
            return;
        }
        if (writeCarSettingsFallback(state, ctx, routeFailures)) {
            return;
        }

        String msg = "Failed to toggle wireless charging. state=" + state
                + " (1=on, 2=off - same as BYD CHARGE_WIRELESS_CHARGING_*)"
                + ". routes=" + routeFailures;
        Log.e(TAG, msg);
    }

    static private void appendRouteFailure(StringBuilder routeFailures, String route, Throwable t) {
        Throwable cause = unwrap(t);
        if (routeFailures.length() > 0) {
            routeFailures.append(" | ");
        }
        routeFailures.append(route)
                .append(": ")
                .append(cause.getClass().getSimpleName());
        if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
            routeFailures.append(" (").append(cause.getMessage()).append(")");
        }
        Log.w(TAG, "Wireless charging route failed: " + route, cause);
    }

    static private void appendRouteFailure(StringBuilder routeFailures, String route, String detail) {
        if (routeFailures.length() > 0) {
            routeFailures.append(" | ");
        }
        routeFailures.append(route).append(": ").append(detail);
        Log.w(TAG, "Wireless charging route failed: " + route + " (" + detail + ")");
    }

    static private boolean tryCarSettingsWrite(Uri uri, int value, String route, Context context,
                                               StringBuilder routeFailures) {
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put("value", value);

            int updated = resolver.update(uri, contentValues, "key=?", CAR_SETTINGS_SELECTION_ARGS);
            if (updated > 0) {
                Log.i(TAG, route + " updated rows=" + updated + ", value=" + value);
                return true;
            }
            appendRouteFailure(routeFailures, route, "rows_updated=" + updated);
        } catch (Throwable t) {
            appendRouteFailure(routeFailures, route, t);
        }
        return false;
    }

    static private boolean writeCarSettingsFallback(int state, Context context,
                                                    StringBuilder routeFailures) {
        int providerValue = state == 1 ? 1 : 0;
        if (tryCarSettingsWrite(CAR_SETTINGS_USER_URI, providerValue,
                "carsettings_user", context, routeFailures)) {
            return true;
        }
        return tryCarSettingsWrite(CAR_SETTINGS_SYSTEM_URI, providerValue,
                "carsettings_system", context, routeFailures);
    }

    static private void tryEnableHiddenApiExemption() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod(
                    "getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Object vmRuntime = getRuntime.invoke(null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(
                    vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            setHiddenApiExemptions.invoke(vmRuntime, new String[][]{new String[]{"L"}});
        } catch (Throwable ignored) {
            // Newer systems may block this; do not stop normal reflection flow.
        }
    }

    static private Method resolveGetInstance(Class<?> deviceClass) throws NoSuchMethodException {
        try {
            Method m = deviceClass.getMethod("getInstance", Context.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getDeclaredMethod("getInstance", Context.class);
            m.setAccessible(true);
            return m;
        }
    }

    static private Method resolveSetWirelessChargingSwitchState(Class<?> deviceClass)
            throws NoSuchMethodException {
        try {
            Method m = deviceClass.getDeclaredMethod("setWirelessChargingSwitchState", int.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getMethod("setWirelessChargingSwitchState", int.class);
            m.setAccessible(true);
            return m;
        }
    }

    static private boolean invokeDirectSwitchSetter(int state, Context context,
                                                    StringBuilder routeFailures) {
        try {
            Class<?> deviceClass = Class.forName(CHARGING_DEVICE_CLASS);
            Method getInstance = resolveGetInstance(deviceClass);
            Object device = getInstance.invoke(null, context);
            Method setWirelessChargingSwitchState =
                    resolveSetWirelessChargingSwitchState(deviceClass);
            Object ret = setWirelessChargingSwitchState.invoke(device, state);

            if (ret instanceof Integer) {
                int result = (Integer) ret;
                Log.i(TAG, "Direct setter result=" + result + ", state=" + state);
            } else {
                Log.i(TAG, "Direct setter invoked, state=" + state);
            }
            return true;
        } catch (Throwable t) {
            appendRouteFailure(routeFailures, "direct_setter", t);
            return false;
        }
    }

    static private Throwable unwrap(Throwable t) {
        if (t instanceof InvocationTargetException) {
            Throwable c = t.getCause();
            return c != null ? c : t;
        }
        return t;
    }

    static private Method resolveGetDevicetype(Class<?> deviceClass) throws NoSuchMethodException {
        try {
            return deviceClass.getMethod("getDevicetype");
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getDeclaredMethod("getDevicetype");
            m.setAccessible(true);
            return m;
        }
    }

    static private boolean invokeLegacySet(int state, Context context,
                                           StringBuilder routeFailures) {
        try {
            Class<?> deviceClass = Class.forName(CHARGING_DEVICE_CLASS);
            Method getInstance = resolveGetInstance(deviceClass);
            Object device = getInstance.invoke(null, context);

            Method getDeviceTypeMethod = resolveGetDevicetype(deviceClass);
            int mDeviceType = (int) getDeviceTypeMethod.invoke(device);

            Class<?> featureIdsClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");
            Field chargingSwitchSetField =
                    featureIdsClass.getField("CHARGING_CHARGE_WIRELESS_CHARGING_SWITCH_SET");
            int chargingSwitchSet = chargingSwitchSetField.getInt(null);

            Class<?> absAutoClass = Class.forName("android.hardware.bydauto.AbsBYDAutoDevice");
            Method setMethod = absAutoClass.getDeclaredMethod("set", int.class, int.class, int.class);
            setMethod.setAccessible(true);
            Object ret = setMethod.invoke(device, mDeviceType, chargingSwitchSet, state);

            Log.i(TAG, "Legacy set result=" + ret + ", state=" + state);
            return true;
        } catch (Throwable t) {
            appendRouteFailure(routeFailures, "legacy_set", t);
            return false;
        }
    }
}
