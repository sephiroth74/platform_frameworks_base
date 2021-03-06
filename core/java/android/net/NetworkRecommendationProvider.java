package android.net;

import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

/**
 * The base class for implementing a network recommendation provider.
 * @hide
 */
@SystemApi
public abstract class NetworkRecommendationProvider {
    private static final String TAG = "NetworkRecProvider";
    /** The key into the callback Bundle where the RecommendationResult will be found. */
    public static final String EXTRA_RECOMMENDATION_RESULT =
            "android.net.extra.RECOMMENDATION_RESULT";
    /** The key into the callback Bundle where the sequence will be found. */
    public static final String EXTRA_SEQUENCE = "android.net.extra.SEQUENCE";
    private static final String EXTRA_RECOMMENDATION_REQUEST =
            "android.net.extra.RECOMMENDATION_REQUEST";
    private final IBinder mService;

    /**
     * Constructs a new instance.
     * @param handler indicates which thread to use when handling requests. Cannot be {@code null}.
     */
    public NetworkRecommendationProvider(Handler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("The provided handler cannot be null.");
        }
        mService = new ServiceWrapper(new ServiceHandler(handler.getLooper()));
    }

    /**
     * Invoked when a recommendation has been requested.
     *
     * @param request a {@link RecommendationRequest} instance containing additional
     *                request details
     * @return a {@link RecommendationResult} instance containing the recommended
     *         network to connect to
     */
    public abstract RecommendationResult onRequestRecommendation(RecommendationRequest request);


    /**
     * Services that can handle {@link NetworkScoreManager#ACTION_RECOMMEND_NETWORKS} should
     * return this Binder from their <code>onBind()</code> method.
     */
    public final IBinder getBinder() {
        return mService;
    }

    private final class ServiceHandler extends Handler {
        static final int MSG_GET_RECOMMENDATION = 1;

        ServiceHandler(Looper looper) {
            super(looper, null /*callback*/, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            final int what = msg.what;
            switch (what) {
                case MSG_GET_RECOMMENDATION:
                    final IRemoteCallback callback = (IRemoteCallback) msg.obj;
                    final int seq = msg.arg1;
                    final RecommendationRequest request =
                            msg.getData().getParcelable(EXTRA_RECOMMENDATION_REQUEST);
                    final RecommendationResult result = onRequestRecommendation(request);
                    final Bundle data = new Bundle();
                    data.putInt(EXTRA_SEQUENCE, seq);
                    data.putParcelable(EXTRA_RECOMMENDATION_RESULT, result);
                    try {
                        callback.sendResult(data);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Callback failed for seq: " + seq, e);
                    }

                    break;

                default:
                    throw new IllegalArgumentException("Unknown message: " + what);
            }
        }
    }

    /**
     * A wrapper around INetworkRecommendationProvider that sends calls to the internal Handler.
     */
    private static final class ServiceWrapper extends INetworkRecommendationProvider.Stub {
        private final Handler mHandler;

        ServiceWrapper(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void requestRecommendation(RecommendationRequest request, IRemoteCallback callback,
                int sequence) throws RemoteException {
            final Message msg = mHandler.obtainMessage(
                    ServiceHandler.MSG_GET_RECOMMENDATION, sequence, 0 /*arg2*/, callback);
            final Bundle data = new Bundle();
            data.putParcelable(EXTRA_RECOMMENDATION_REQUEST, request);
            msg.setData(data);
            msg.sendToTarget();
        }
    }
}
