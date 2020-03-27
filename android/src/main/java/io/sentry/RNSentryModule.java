package io.sentry;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.lang.reflect.Field;
import java.lang.Number;
import android.util.Log;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.sentry.android.core.AnrIntegration;
import io.sentry.android.core.NdkIntegration;
import io.sentry.android.core.SentryAndroid;
import io.sentry.core.Integration;
import io.sentry.core.SentryOptions;
import io.sentry.core.UncaughtExceptionHandlerIntegration;
import io.sentry.core.protocol.SentryException;


@ReactModule(name = RNSentryModule.NAME)
public class RNSentryModule extends ReactContextBaseJavaModule {

    public static final String NAME = "RNSentry";

    final static Logger logger = Logger.getLogger("react-native-sentry");

    private static PackageInfo packageInfo;
    private SentryOptions sentryOptions;
    private static final String ON_NATIVE_BEFORE_SEND_SENTRY_EVENT = "OnNativeBeforeSendSentryEvent";
    private static final String CRASH_KEY = "CRASH_KEY";
    private static final String CAUSE_KEY = "CAUSE_KEY";
    public RNSentryModule(ReactApplicationContext reactContext) {
        super(reactContext);
        RNSentryModule.packageInfo = getPackageInfo(reactContext);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("nativeClientAvailable", true);
        constants.put("nativeTransport", true);
        return constants;
    }

    public void setBeforeSendDelegate() {

    }

    private static final Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();
    public static boolean isWrapperType(Class<?> clazz)
    {
        return WRAPPER_TYPES.contains(clazz);
    }
    private static Set<Class<?>> getWrapperTypes()
    {
        Set<Class<?>> ret = new HashSet<>();
        ret.add(Boolean.class);
        ret.add(Character.class);
        ret.add(Byte.class);
        ret.add(Short.class);
        ret.add(Integer.class);
        ret.add(Long.class);
        ret.add(Float.class);
        ret.add(Double.class);
        ret.add(Void.class);
        return ret;
    }

    @ReactMethod
    public void startWithDsnString(String dsnString, final ReadableMap rnOptions, Promise promise) {
        SentryAndroid.init(this.getReactApplicationContext(), options -> {
            options.setDsn(dsnString);

            logger.info(String.format("NATENATE: initialized rn sentry module"));

           if (rnOptions.hasKey("debug") && rnOptions.getBoolean("debug")) {
               options.setDebug(true);
           }
           if (rnOptions.hasKey("environment") && rnOptions.getString("environment") != null) {
               options.setEnvironment(rnOptions.getString("environment"));
           }
           if (rnOptions.hasKey("release") && rnOptions.getString("release") != null) {
               options.setRelease(rnOptions.getString("release"));
           }
           if (rnOptions.hasKey("dist") && rnOptions.getString("dist") != null) {
               options.setDist(rnOptions.getString("dist"));
           }
            options.setBeforeSend((event, hint) -> {
                Log.e("NATENATE"," SENTRY SDK NATIVE INSTANCE: ");
                Log.e("NATENATE"," eventId: "+event.getEventId());
                Log.e("NATENATE"," timestamp: "+event.getTimestamp());
//                Log.e("NATENATE"," throwable: "+event.getThrowable());
                Log.e("NATENATE"," message: "+event.getMessage());
                Log.e("NATENATE"," release: "+event.getRelease());
                Log.e("NATENATE"," level: "+event.getLevel());
//                Log.e("NATENATE"," extras: "+event.getExtras());
                Log.e("NateNate"," debugMeta: "+event.getDebugMeta());
                // React native internally throws a JavascriptException
                // Since we catch it before that, we don't want to send this one
                // because we would send it twice
                try {
                    SentryException ex = event.getExceptions().get(0);
                    if (null != ex && ex.getType().contains("JavascriptException")) {
                        return null;
                    }

                } catch (Exception e) {
                    // We do nothing
                }
                //ClasWithStuff myStuff = new ClassWithStuff();
                /**
                 * push this up to JS land so we can process it with our metrics counting code
                 */
                logger.info(String.format("NATENATE: got event in library on before send"));

                ReactContext rnac = RNSentryModule.this.getReactApplicationContext();
                if (rnac != null) {
                    DeviceEventManagerModule.RCTDeviceEventEmitter emitter = rnac.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
                    if (emitter != null) {
                        String message = event.getMessage() != null ? event.getMessage().getFormatted(): "no message on crash";
                        Log.e("NATENATE",String.format("Sentry Native Library pushing event into JS land"));
                        Log.e("NATENATE",String.format(message));

                        HashMap map = new HashMap();
                        map.put(CRASH_KEY, event.getEventId().toString());
                        map.put(CAUSE_KEY, message);
                        emitter.emit(ON_NATIVE_BEFORE_SEND_SENTRY_EVENT, Arguments.makeNativeMap(map));
                    }
                }
                return event;
            });

            for (Iterator<Integration> iterator = options.getIntegrations().iterator(); iterator.hasNext(); ) {
            Integration integration = iterator.next();
                if (rnOptions.hasKey("enableNativeCrashHandling") &&
                        !rnOptions.getBoolean("enableNativeCrashHandling")) {
                    if (integration instanceof UncaughtExceptionHandlerIntegration ||
                            integration instanceof AnrIntegration ||
                            integration instanceof NdkIntegration) {
                        iterator.remove();
                    }
                }
            }

            logger.info(String.format("Native Integrations '%s'", options.getIntegrations().toString()));
            sentryOptions = options;
        });

        logger.info(String.format("startWithDsnString '%s'", dsnString));
        promise.resolve(true);
    }

    @ReactMethod
    public void setLogLevel(int level) {
        logger.setLevel(this.logLevel(level));
    }

    @ReactMethod
    public void crash() {
        throw new RuntimeException("TEST - Sentry Client Crash (only works in release mode)");
    }

    @ReactMethod
    public void fetchRelease(Promise promise) {
        WritableMap release = Arguments.createMap();
        release.putString("id", packageInfo.packageName);
        release.putString("version", packageInfo.versionName);
        release.putString("build", String.valueOf(packageInfo.versionCode));
        promise.resolve(release);
    }

    @ReactMethod
    public void captureEnvelope(String envelope, Promise promise) {
        try {
            File installation = new File(sentryOptions.getOutboxPath(), UUID.randomUUID().toString());
            try(FileOutputStream out = new FileOutputStream(installation)) {
                out.write(envelope.getBytes(Charset.forName("UTF-8")));
            }
        } catch (Exception e) {
            logger.info("Error reading envelope");
        }
        promise.resolve(true);
    }

    @ReactMethod
    public void getStringBytesLength(String payload, Promise promise) {
        try {
            promise.resolve(payload.getBytes("UTF-8").length);
        } catch (UnsupportedEncodingException e) {
            promise.reject(e);
        }
    }

    private static PackageInfo getPackageInfo(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            logger.info("Error getting package info.");
            return null;
        }
    }

    private Level logLevel(int level) {
        switch (level) {
            case 1:
                return Level.SEVERE;
            case 2:
                return Level.INFO;
            case 3:
                return Level.ALL;
            default:
                return Level.OFF;
        }
    }

}
