package me.aap.fermata.auto;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.utils.log.Log;

public final class OverlayToggler {
	private static final String TAG = "FermataOverlay";
	private static final long AWAIT_TIMEOUT_MS = 500L;
	private static volatile View overlay;
	private static volatile WindowManager wm;

	private OverlayToggler() {
	}

	public static void init(WindowManager windowManager, View overlayView) {
		wm = windowManager;
		overlay = overlayView;
		if (overlayView != null) {
			Log.d(TAG, "Initialized overlay toggler");
			setVisible(true);
		} else {
			Log.d(TAG, "Cleared overlay toggler");
		}
	}

	public static void setVisible(boolean visible) {
		var view = overlay;
		if (view == null) return;
		if (Looper.myLooper() != Looper.getMainLooper()) {
			new Handler(Looper.getMainLooper()).post(() -> setVisible(visible));
			return;
		}
		int visibility = visible ? View.VISIBLE : View.GONE;
		if (view.getVisibility() == visibility) return;
		Log.d(TAG, visible ? "Showing overlay" : "Hiding overlay");
		view.setVisibility(visibility);
	}

	public static boolean hideForGesture(long durationMs, BooleanSupplier gesture) {
		if (gesture == null) return false;
		var handler = new Handler(Looper.getMainLooper());
		var result = new AtomicBoolean(false);
		Runnable task = () -> {
			boolean hasOverlay = overlay != null;
			if (hasOverlay) setVisible(false);
			try {
				result.set(gesture.getAsBoolean());
			} finally {
				if (hasOverlay) {
					handler.postDelayed(() -> setVisible(true), Math.max(0L, durationMs));
				}
			}
		};

		if (Looper.myLooper() == Looper.getMainLooper()) {
			task.run();
		} else {
			var latch = new CountDownLatch(1);
			handler.post(() -> {
				try {
					task.run();
				} finally {
					latch.countDown();
				}
			});
			try {
				latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return result.get();
	}

	public static void hideForGesture(long durationMs, Runnable gesture) {
		if (gesture == null) return;
		hideForGesture(durationMs, () -> {
			gesture.run();
			return true;
		});
	}

	@FunctionalInterface
	public interface BooleanSupplier {
		boolean getAsBoolean();
	}
}
