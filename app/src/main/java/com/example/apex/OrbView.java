package com.example.apex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/** The glowing particle orb. Reacts to idle / listening / thinking / speaking. */
public class OrbView extends View {

    public static final int IDLE = 0;
    public static final int LISTENING = 1;
    public static final int THINKING = 2;
    public static final int SPEAKING = 3;

    private static final int PARTICLES = 160;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] angle = new float[PARTICLES];
    private final float[] radius = new float[PARTICLES];
    private final float[] speed = new float[PARTICLES];
    private final float[] phase = new float[PARTICLES];

    private volatile int mode = IDLE;
    private volatile float level = 0f;
    private float t = 0f;

    public OrbView(Context context) {
        super(context);
        Random rnd = new Random(7);
        for (int i = 0; i < PARTICLES; i++) {
            angle[i] = rnd.nextFloat() * (float) (Math.PI * 2);
            radius[i] = 0.55f + rnd.nextFloat() * 0.45f;
            speed[i] = 0.2f + rnd.nextFloat() * 0.6f;
            phase[i] = rnd.nextFloat() * (float) (Math.PI * 2);
        }
    }

    public void setMode(int m) {
        mode = m;
    }

    /** Microphone level, 0..1. */
    public void setLevel(float l) {
        level = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        t += 1f;

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float base = Math.min(w, h) * 0.3f;

        int m = mode;
        float sp = m == THINKING ? 2.2f : m == LISTENING ? 1.4f : 0.6f;
        float amp;
        if (m == SPEAKING) {
            amp = 0.12f + 0.12f * Math.abs((float) Math.sin(t * 0.15f));
        } else if (m == LISTENING) {
            amp = 0.06f + level * 0.12f;
        } else {
            amp = 0.03f;
        }
        float pulse = (float) Math.sin(t * 0.05f * sp) * amp;

        // Outer glow
        float glowR = base * 1.6f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, glowR,
                new int[]{0x99FFAA3C, 0x4022D3EE, 0x001E6BFF},
                new float[]{0f, 0.3f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, glowR, paint);
        paint.setShader(null);

        // Particle shell
        paint.setColor(0xFF8CF0FF);
        for (int i = 0; i < PARTICLES; i++) {
            float ang = angle[i] + t / 250f * speed[i];
            float rad = base * (radius[i] + pulse);
            float x = cx + (float) Math.cos(ang) * rad;
            float y = cy + (float) Math.sin(ang) * rad * 0.92f;
            float a = 0.5f + 0.4f * (float) Math.sin(t * 0.06f + phase[i]);
            paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, a))));
            canvas.drawCircle(x, y, 2.5f, paint);
        }
        paint.setAlpha(255);

        // Orange core
        float coreR = Math.max(1f, base * (0.45f + pulse * 1.2f));
        paint.setShader(new RadialGradient(cx, cy, coreR,
                new int[]{0xFFFFE68C, 0xB3FF9628, 0x00FF5A14},
                new float[]{0f, 0.4f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, coreR, paint);
        paint.setShader(null);

        postInvalidateOnAnimation();
    }
}
