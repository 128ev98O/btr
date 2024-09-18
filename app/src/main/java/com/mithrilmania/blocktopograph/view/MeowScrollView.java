package com.mithrilmania.blocktopograph.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.EdgeEffect;
import android.widget.ScrollView;

import java.lang.reflect.Field;

public class MeowScrollView extends ScrollView {

    /**
     * Top glowing effect of the underlying scrollView.
     *
     * <p>It was retrieved via reflection and used later.</p>
     */
    private EdgeEffect mEdgeEffect;

    public MeowScrollView(Context context) {
        super(context);
        init();
    }

    public MeowScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MeowScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MeowScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        Class<ScrollView> clazz = ScrollView.class;
        try {
            @SuppressLint("PrivateApi")
            Field edgeGlowField = clazz.getDeclaredField("mEdgeGlowTop");
            edgeGlowField.setAccessible(true);
            mEdgeEffect = (EdgeEffect) edgeGlowField.get(this);
            edgeGlowField.setAccessible(false);
            mEdgeEffect.setSize(20, 20);
            post(this::doOverScroll);
        } catch (NoSuchFieldException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doOverScroll() {
        if (mEdgeEffect != null) {
            mEdgeEffect.onPull(200);
            invalidate();
        }
    }
}
