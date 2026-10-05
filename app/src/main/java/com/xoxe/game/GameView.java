package com.xoxe.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements Runnable {
    static final long NS_UPDATE = 16_666_666L;

    Thread thread;
    volatile boolean running;
    SurfaceHolder holder;
    Paint paint;
    int W, H;

    int screen = 0;

    float joyCX, joyCY, joyR, joyX, joyY;
    int joyId = -1;

    final float WW = 2000f, WH = 2000f;
    float camX, camY;

    float px, py, pvx, pvy, php;
    final float PMAX = 100f;
    int pShootCD = 0;

    final int MAX_BOTS = 6;
    float[] bx = new float[MAX_BOTS];
    float[] by = new float[MAX_BOTS];
    float[] bhp = new float[MAX_BOTS];
    int bCount = 0;

    final int MAX_BUL = 200;
    float[] bulx = new float[MAX_BUL];
    float[] buly = new float[MAX_BUL];
    float[] bulvx = new float[MAX_BUL];
    float[] bulvy = new float[MAX_BUL];
    int[] bulFrom = new int[MAX_BUL];
    int bulCount = 0;

    float[][] walls = {
        {300, 300, 500, 340},
        {1500, 400, 1700, 440},
        {400, 1500, 600, 1540},
        {1400, 1500, 1700, 1540},
        {900, 900, 1100, 940}
    };

    int score = 0;

    public GameView(Context ctx) {
        super(ctx);
        holder = getHolder();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    void startGame() {
        px = WW / 2; py = WH / 2;
        pvx = 0; pvy = 0;
        php = PMAX;
        score = 0;
        bulCount = 0;
        spawnWave();
        screen = 1;
    }

    void spawnWave() {
        bCount = 4;
        for (int i = 0; i < bCount; i++) {
            float ang = (float)(i * 2 * Math.PI / bCount);
            bx[i] = WW / 2 + (float)Math.cos(ang) * 700;
            by[i] = WH / 2 + (float)Math.sin(ang) * 700;
            bhp[i] = 60;
        }
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        long acc = 0;
        while (running) {
            long now = System.nanoTime();
            long d = now - last;
            last = now;
            if (d > 250_000_000L) d = 250_000_000L;
            acc += d;
            int it = 0;
            while (acc >= NS_UPDATE && it < 5) {
                update();
                acc -= NS_UPDATE;
                it++;
            }
            if (!holder.getSurface().isValid()) continue;
            Canvas c = holder.lockCanvas();
            if (c != null) {
                try { render(c); } finally { holder.unlockCanvasAndPost(c); }
            }
        }
    }

    void update() {
        float dt = NS_UPDATE / 1e9f;
        if (screen != 1) return;

        float jx = joyX - joyCX;
        float jy = joyY - joyCY;
        float jd = (float)Math.sqrt(jx * jx + jy * jy);
        float sp = 350f;
        if (jd > joyR) { jx = jx / jd * joyR; jy = jy / jd * joyR; jd = joyR; }
        if (jd > 10) {
            pvx = jx / joyR * sp;
            pvy = jy / joyR * sp;
        } else {
            pvx *= 0.85f;
            pvy *= 0.85f;
        }
        px += pvx * dt;
        py += pvy * dt;

        for (float[] w : walls) {
            if (px > w[0] - 40 && px < w[2] + 40 && py > w[1] - 40 && py < w[3] + 40) {
                float dl = px - (w[0] - 40);
                float dr = (w[2] + 40) - px;
                float dtp = py - (w[1] - 40);
                float db = (w[3] + 40) - py;
                float m = Math.min(Math.min(dl, dr), Math.min(dtp, db));
                if (m == dl) { px = w[0] - 40; pvx = 0; }
                else if (m == dr) { px = w[2] + 40; pvx = 0; }
                else if (m == dtp) { py = w[1] - 40; pvy = 0; }
                else { py = w[3] + 40; pvy = 0; }
            }
        }
        if (px < 50) { px = 50; pvx = 0; }
        if (px > WW - 50) { px = WW - 50; pvx = 0; }
        if (py < 50) { py = 50; pvy = 0; }
        if (py > WH - 50) { py = WH - 50; pvy = 0; }

        camX = px; camY = py;
        if (pShootCD > 0) pShootCD--;

        for (int i = 0; i < bCount; i++) {
            float dx = px - bx[i];
            float dy = py - by[i];
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            if (dist > 200) {
                bx[i] += dx / dist * 130 * dt;
                by[i] += dy / dist * 130 * dt;
            } else if (dist < 150 && dist > 1) {
                bx[i] -= dx / dist * 100 * dt;
                by[i] -= dy / dist * 100 * dt;
            }
            if (Math.random() < 0.015 && dist < 700 && dist > 1) {
                addBullet(bx[i], by[i], dx / dist * 500, dy / dist * 500, 1);
            }
        }

        for (int i = 0; i < bulCount; i++) {
            bulx[i] += bulvx[i] * dt;
            buly[i] += bulvy[i] * dt;
            boolean dead = false;
            if (bulx[i] < 0 || bulx[i] > WW || buly[i] < 0 || buly[i] > WH) dead = true;
            if (!dead) for (float[] w : walls) {
                if (bulx[i] > w[0] && bulx[i] < w[2] && buly[i] > w[1] && buly[i] < w[3]) {
                    dead = true; break;
                }
            }
            if (!dead && bulFrom[i] == 0) {
                for (int j = 0; j < bCount; j++) {
                    float dx = bulx[i] - bx[j], dy = buly[i] - by[j];
                    if (dx * dx + dy * dy < 45 * 45) {
                        bhp[j] -= 20;
                        dead = true;
                        if (bhp[j] <= 0) {
                            bx[j] = bx[bCount - 1];
                            by[j] = by[bCount - 1];
                            bhp[j] = bhp[bCount - 1];
                            bCount--;
                            score++;
                            j--;
                        }
                        break;
                    }
                }
            }
            if (!dead && bulFrom[i] == 1) {
                float dx = bulx[i] - px, dy = buly[i] - py;
                if (dx * dx + dy * dy < 45 * 45) { php -= 10; dead = true; }
            }
            if (dead) {
                bulx[i] = bulx[bulCount - 1];
                buly[i] = buly[bulCount - 1];
                bulvx[i] = bulvx[bulCount - 1];
                bulvy[i] = bulvy[bulCount - 1];
                bulFrom[i] = bulFrom[bulCount - 1];
                bulCount--;
                i--;
            }
        }

        if (bCount == 0) spawnWave();
        if (php <= 0) screen = 2;
    }

    void addBullet(float x, float y, float vx, float vy, int from) {
        if (bulCount >= MAX_BUL) return;
        bulx[bulCount] = x; buly[bulCount] = y;
        bulvx[bulCount] = vx; bulvy[bulCount] = vy;
        bulFrom[bulCount] = from;
        bulCount++;
    }

    void render(Canvas c) {
        c.drawColor(0xFF0A0E1A);

        if (screen == 0) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0xFF00E5FF);
            paint.setTextSize(H * 0.20f);
            c.drawText("XoXe", W / 2f, H / 2f - 40, paint);
            paint.setColor(0xFFFF00AA);
            paint.setTextSize(H * 0.05f);
            c.drawText("TAP TO PLAY", W / 2f, H / 2f + 110, paint);
            return;
        }
        if (screen == 2) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0xFFFF3B6B);
            paint.setTextSize(H * 0.15f);
            c.drawText("GAME OVER", W / 2f, H / 2f - 20, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(H * 0.06f);
            c.drawText("Score: " + score, W / 2f, H / 2f + 80, paint);
            c.drawText("TAP TO RETRY", W / 2f, H / 2f + 180, paint);
            return;
        }

        c.save();
        c.translate(W / 2f - camX, H / 2f - camY);

        paint.setColor(0xFF141A2E);
        paint.setStrokeWidth(2);
        for (float x = 0; x <= WW; x += 100) c.drawLine(x, 0, x, WH, paint);
        for (float y = 0; y <= WH; y += 100) c.drawLine(0, y, WW, y, paint);

        paint.setColor(0xFF00E5FF);
        paint.setStrokeWidth(6);
        paint.setStyle(Paint.Style.STROKE);
        c.drawRect(0, 0, WW, WH, paint);
        paint.setStyle(Paint.Style.FILL);

        for (float[] w : walls) {
            paint.setColor(0xFF1E2A4A);
            c.drawRect(w[0], w[1], w[2], w[3], paint);
            paint.setColor(0xFF00E5FF);
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            c.drawRect(w[0], w[1], w[2], w[3], paint);
            paint.setStyle(Paint.Style.FILL);
        }

        for (int i = 0; i < bCount; i++) {
            paint.setColor(0x44FF3B6B);
            c.drawCircle(bx[i], by[i], 60, paint);
            paint.setColor(0xFFFF3B6B);
            c.drawCircle(bx[i], by[i], 38, paint);
            paint.setColor(0xFF2A2A2A);
            c.drawRect(bx[i] - 40, by[i] - 58, bx[i] + 40, by[i] - 48, paint);
            paint.setColor(0xFF00FF88);
            c.drawRect(bx[i] - 40, by[i] - 58, bx[i] - 40 + 80 * (bhp[i] / 60f), by[i] - 48, paint);
        }

        for (int i = 0; i < bulCount; i++) {
            if (bulFrom[i] == 0) {
                paint.setColor(0x8800E5FF);
                c.drawCircle(bulx[i], buly[i], 18, paint);
                paint.setColor(0xFFAAFFFF);
                c.drawCircle(bulx[i], buly[i], 9, paint);
            } else {
                paint.setColor(0x88FF3B6B);
                c.drawCircle(bulx[i], buly[i], 18, paint);
                paint.setColor(0xFFFFAAAA);
                c.drawCircle(bulx[i], buly[i], 9, paint);
            }
        }

        paint.setColor(0x4400E5FF);
        c.drawCircle(px, py, 70, paint);
        paint.setColor(0xFF00E5FF);
        c.drawCircle(px, py, 42, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(px, py, 18, paint);

        c.restore();

        if (joyId != -1) {
            paint.setColor(0x22FFFFFF);
            c.drawCircle(joyCX, joyCY, joyR, paint);
            paint.setColor(0x66FFFFFF);
            c.drawCircle(joyCX, joyCY, joyR * 0.92f, paint);
            paint.setColor(0xAAFFFFFF);
            c.drawCircle(joyX, joyY, joyR * 0.35f, paint);
        }

        paint.setColor(0x88000000);
        c.drawRect(30, 30, W - 30, 70, paint);
        paint.setColor(0xFF00FF88);
        c.drawRect(30, 30, 30 + (W - 60) * (php / PMAX), 70, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(60);
        c.drawText("Score: " + score, W - 40, 160, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int pid = e.getPointerId(idx);
        float x = e.getX(idx), y = e.getY(idx);

        if (screen == 0 && action == MotionEvent.ACTION_DOWN) { startGame(); return true; }
        if (screen == 2 && action == MotionEvent.ACTION_DOWN) { startGame(); return true; }
        if (screen != 1) return true;

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (x < W * 0.5f && joyId == -1) {
                joyId = pid;
                joyCX = x; joyCY = y;
                joyR = Math.min(W, H) * 0.16f;
                joyX = x; joyY = y;
            } else if (x >= W * 0.5f) {
                fire();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (e.getPointerId(i) == joyId) {
                    joyX = e.getX(i);
                    joyY = e.getY(i);
                }
            }
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            if (pid == joyId) {
                joyId = -1;
                joyX = joyCX; joyY = joyCY;
            }
        }
        return true;
    }

    void fire() {
        if (pShootCD > 0) return;
        float bestD = Float.MAX_VALUE;
        int bi = -1;
        for (int i = 0; i < bCount; i++) {
            float dx = bx[i] - px, dy = by[i] - py;
            float d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; bi = i; }
        }
        float dx, dy;
        if (bi >= 0) { dx = bx[bi] - px; dy = by[bi] - py; }
        else { dx = 1; dy = 0; }
        float d = (float)Math.sqrt(dx * dx + dy * dy);
        if (d < 1) d = 1;
        float sp = 900;
        addBullet(px + dx / d * 50, py + dy / d * 50, dx / d * sp, dy / d * sp, 0);
        pShootCD = 12;
    }

    public void resume() {
        if (running) return;
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
        try { if (thread != null) thread.join(); } catch (Exception ignored) {}
        thread = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w; H = h;
        joyR = Math.min(W, H) * 0.16f;
    }
}
