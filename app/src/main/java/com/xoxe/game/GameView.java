package com.xoxe.game;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable {
    static final long NS_UPDATE = 16_666_666L;
    static final float Y_TILT = 0.72f;
    static final Random RNG = new Random();

    Thread thread;
    volatile boolean running;
    SurfaceHolder holder;
    Paint paint;
    int W, H;

    int screen = 0;

    float joyCX, joyCY, joyR, joyX, joyY;
    int joyId = -1;

    final float WW = 3200f, WH = 3200f;
    float camX, camY;
    float shakeX = 0, shakeY = 0;
    int shakeT = 0;

    float px, py, pvx, pvy, php;
    final float PMAX = 100f;
    int pShootCD = 0;
    int pFacing = 1;
    int pShootVisual = 0;
    int pWalkPhase = 0;
    int pDamageFlash = 0;

    final int MAX_BOTS = 10;
    float[] bx = new float[MAX_BOTS];
    float[] by = new float[MAX_BOTS];
    float[] bhp = new float[MAX_BOTS];
    int[] bFacing = new int[MAX_BOTS];
    int[] bShootVisual = new int[MAX_BOTS];
    int[] bWalkPhase = new int[MAX_BOTS];
    int[] bDamageFlash = new int[MAX_BOTS];
    int bCount = 0;

    final int MAX_BUL = 400;
    float[] bulx = new float[MAX_BUL];
    float[] buly = new float[MAX_BUL];
    float[] bulvx = new float[MAX_BUL];
    float[] bulvy = new float[MAX_BUL];
    int[] bulFrom = new int[MAX_BUL];
    int bulCount = 0;

    final int MAX_PARTS = 600;
    float[] partX = new float[MAX_PARTS];
    float[] partY = new float[MAX_PARTS];
    float[] partVX = new float[MAX_PARTS];
    float[] partVY = new float[MAX_PARTS];
    float[] partLife = new float[MAX_PARTS];
    float[] partSize = new float[MAX_PARTS];
    int[] partColor = new int[MAX_PARTS];
    int partCount = 0;

    Bitmap tileWall, tileFloor;
    int tileSize = 128;

    Bitmap pStand, pGun;
    Bitmap pStandF, pGunF;
    Bitmap bStand, bGun;
    Bitmap bStandF, bGunF;
    Bitmap bulletBmp;
    Bitmap muzzleFlash;
    int pW, pH, bW, bH;

    Bitmap bgLayer;

    float[][] walls = {
        {400, 400, 700, 460}, {2400, 500, 2800, 560},
        {500, 2400, 800, 2460}, {2200, 2400, 2700, 2460},
        {1400, 1400, 1800, 1460}, {1400, 1800, 1460, 2200},
        {1900, 900, 1960, 1300}, {900, 1900, 1300, 1960},
        {200, 1500, 500, 1560}, {2700, 1500, 3000, 1560},
        {1500, 200, 1560, 500}, {1500, 2700, 1560, 3000}
    };

    int score = 0;
    int killsNeeded = 30;

    public GameView(Context ctx) {
        super(ctx);
        holder = getHolder();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        loadSprites(ctx);
    }

    void loadSprites(Context ctx) {
        AssetManager am = ctx.getAssets();
        ArrayList<String> all = new ArrayList<>();
        collectPngs(am, "kenney", all);

        String pStandP = null, pGunP = null;
        String bStandP = null, bGunP = null;
        String bulletP = null, muzzleP = null;

        for (String p : all) {
            String low = p.toLowerCase();
            String[] parts = low.split("/");
            String file = parts[parts.length - 1];

            if (file.contains("soldier")) {
                if (file.contains("stand") && pStandP == null) pStandP = p;
                if (file.contains("gun") && !file.contains("machine") && pGunP == null) pGunP = p;
            }
            if (file.contains("zombie") || file.contains("robot") || file.contains("survivor")) {
                if (file.contains("stand") && bStandP == null) bStandP = p;
                if (file.contains("gun") && bGunP == null) bGunP = p;
            }
            if (file.contains("bullet") && bulletP == null) bulletP = p;
            if (file.contains("muzzle") && muzzleP == null) muzzleP = p;
        }

        pStand = scale(load(am, pStandP), 140);
        pGun = scale(load(am, pGunP != null ? pGunP : pStandP), 140);
        bStand = scale(load(am, bStandP != null ? bStandP : pStandP), 140);
        bGun = scale(load(am, bGunP != null ? bGunP : bStandP), 140);
        bulletBmp = scale(load(am, bulletP), 36);
        muzzleFlash = scale(load(am, muzzleP), 80);

        tileWall = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        Canvas wc = new Canvas(tileWall);
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setColor(0xFF1E2A4A); wc.drawRect(0, 0, tileSize, tileSize, wp);
        wp.setColor(0xFF2A3A5A); wc.drawRect(0, 0, tileSize, 6, wp);
        wp.setColor(0xFF00E5FF); wp.setStyle(Paint.Style.STROKE);
        wp.setStrokeWidth(4); wc.drawRect(2, 2, tileSize - 2, tileSize - 2, wp);
        wp.setStyle(Paint.Style.FILL);
        wp.setColor(0x3300E5FF); wc.drawCircle(tileSize / 2f, tileSize / 2f, 20, wp);

        tileFloor = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(tileFloor);
        Paint fp = new Paint();
        fp.setColor(0xFF0F1420); fc.drawRect(0, 0, tileSize, tileSize, fp);
        fp.setColor(0xFF141A2E); fc.drawRect(0, 0, tileSize, 4, fp);
        fc.drawRect(0, 0, 4, tileSize, fp);

        bgLayer = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(bgLayer);
        Paint bp = new Paint();
        bp.setColor(0xFF060A14); bc.drawRect(0, 0, 512, 512, bp);
        bp.setColor(0x33FFFFFF);
        for (int i = 0; i < 60; i++) {
            bc.drawCircle(RNG.nextInt(512), RNG.nextInt(512), RNG.nextInt(3) + 1, bp);
        }

        pStandF = flipH(pStand);
        pGunF = flipH(pGun);
        bStandF = flipH(bStand);
        bGunF = flipH(bGun);

        if (pStand != null) { pW = pStand.getWidth(); pH = pStand.getHeight(); }
        if (bStand != null) { bW = bStand.getWidth(); bH = bStand.getHeight(); }
    }

    void collectPngs(AssetManager am, String path, ArrayList<String> out) {
        try {
            String[] kids = am.list(path);
            if (kids == null || kids.length == 0) {
                if (path.toLowerCase().endsWith(".png")) out.add(path);
                return;
            }
            for (String k : kids) {
                String full = path + "/" + k;
                String[] sub = am.list(full);
                if (sub != null && sub.length > 0) collectPngs(am, full, out);
                else if (full.toLowerCase().endsWith(".png")) out.add(full);
            }
        } catch (Exception ignored) {}
    }

    Bitmap load(AssetManager am, String path) {
        if (path == null) return null;
        try (InputStream is = am.open(path)) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) { return null; }
    }

    Bitmap scale(Bitmap src, int targetW) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        if (w == targetW) return src;
        float r = (float) targetW / w;
        int nh = Math.max(1, (int) (h * r));
        Bitmap out = Bitmap.createScaledBitmap(src, targetW, nh, true);
        if (out != src) src.recycle();
        return out;
    }

    Bitmap flipH(Bitmap src) {
        if (src == null) return null;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.preScale(-1, 1);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);
    }

    void spawnParticles(float x, float y, int count, int color, float speed, float size) {
        for (int i = 0; i < count; i++) {
            if (partCount >= MAX_PARTS) break;
            partX[partCount] = x;
            partY[partCount] = y;
            float ang = RNG.nextFloat() * 6.28318f;
            float sp = speed * (0.4f + RNG.nextFloat() * 0.6f);
            partVX[partCount] = (float) Math.cos(ang) * sp;
            partVY[partCount] = (float) Math.sin(ang) * sp;
            partLife[partCount] = 1.0f;
            partSize[partCount] = size * (0.6f + RNG.nextFloat() * 0.6f);
            partColor[partCount] = color;
            partCount++;
        }
    }

    void updateParticles(float dt) {
        for (int i = 0; i < partCount; i++) {
            partX[i] += partVX[i] * dt;
            partY[i] += partVY[i] * dt;
            partVX[i] *= 0.94f;
            partVY[i] *= 0.94f;
            partLife[i] -= dt * 2.2f;
            if (partLife[i] <= 0) {
                partX[i] = partX[partCount - 1];
                partY[i] = partY[partCount - 1];
                partVX[i] = partVX[partCount - 1];
                partVY[i] = partVY[partCount - 1];
                partLife[i] = partLife[partCount - 1];
                partSize[i] = partSize[partCount - 1];
                partColor[i] = partColor[partCount - 1];
                partCount--;
                i--;
            }
        }
    }
    void startGame() {
        px = WW / 2; py = WH / 2;
        pvx = 0; pvy = 0;
        php = PMAX;
        score = 0;
        bulCount = 0;
        partCount = 0;
        pFacing = 1;
        pShootVisual = 0;
        pWalkPhase = 0;
        pDamageFlash = 0;
        camX = px; camY = py;
        spawnWave();
        screen = 1;
    }

    void spawnWave() {
        int n = 3 + Math.min(5, score / 6);
        bCount = Math.min(n, MAX_BOTS);
        for (int i = 0; i < bCount; i++) {
            float ang = (float) (i * 2 * Math.PI / bCount);
            float rad = 900 + RNG.nextInt(300);
            bx[i] = WW / 2 + (float) Math.cos(ang) * rad;
            by[i] = WH / 2 + (float) Math.sin(ang) * rad;
            if (bx[i] < 100) bx[i] = 100;
            if (bx[i] > WW - 100) bx[i] = WW - 100;
            if (by[i] < 100) by[i] = 100;
            if (by[i] > WH - 100) by[i] = WH - 100;
            bhp[i] = 60 + score * 2;
            bFacing[i] = 1;
            bShootVisual[i] = 0;
            bWalkPhase[i] = 0;
            bDamageFlash[i] = 0;
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

        if (shakeT > 0) {
            shakeT--;
            shakeX = (RNG.nextFloat() - 0.5f) * 14 * (shakeT / 12f);
            shakeY = (RNG.nextFloat() - 0.5f) * 14 * (shakeT / 12f);
        } else {
            shakeX = 0; shakeY = 0;
        }

        if (screen != 1) { updateParticles(dt); return; }

        if (pShootVisual > 0) pShootVisual--;
        if (pDamageFlash > 0) pDamageFlash--;

        float jx = joyX - joyCX;
        float jy = joyY - joyCY;
        float jd = (float) Math.sqrt(jx * jx + jy * jy);
        float sp = 380f;
        boolean moving = false;
        if (jd > joyR) { jx = jx / jd * joyR; jy = jy / jd * joyR; jd = joyR; }
        if (jd > 12) {
            pvx = jx / joyR * sp;
            pvy = jy / joyR * sp;
            moving = true;
            if (Math.abs(pvx) > 5) pFacing = pvx > 0 ? 1 : -1;
        } else {
            pvx *= 0.82f;
            pvy *= 0.82f;
        }
        if (moving) pWalkPhase++; else pWalkPhase = 0;

        px += pvx * dt;
        py += pvy * dt;

        for (float[] w : walls) {
            if (px > w[0] - 45 && px < w[2] + 45 && py > w[1] - 45 && py < w[3] + 45) {
                float dl = px - (w[0] - 45);
                float dr = (w[2] + 45) - px;
                float dtp = py - (w[1] - 45);
                float db = (w[3] + 45) - py;
                float m = Math.min(Math.min(dl, dr), Math.min(dtp, db));
                if (m == dl) { px = w[0] - 45; pvx = 0; }
                else if (m == dr) { px = w[2] + 45; pvx = 0; }
                else if (m == dtp) { py = w[1] - 45; pvy = 0; }
                else { py = w[3] + 45; pvy = 0; }
            }
        }
        if (px < 60) { px = 60; pvx = 0; }
        if (px > WW - 60) { px = WW - 60; pvx = 0; }
        if (py < 60) { py = 60; pvy = 0; }
        if (py > WH - 60) { py = WH - 60; pvy = 0; }

        // Плавная камера с запаздыванием
        camX += (px - camX) * 0.12f;
        camY += (py - camY) * 0.12f;

        if (pShootCD > 0) pShootCD--;

        for (int i = 0; i < bCount; i++) {
            if (bShootVisual[i] > 0) bShootVisual[i]--;
            if (bDamageFlash[i] > 0) bDamageFlash[i]--;
            float dx = px - bx[i];
            float dy = py - by[i];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 1) dist = 1;
            boolean bMoving = false;
            float bSpeed = 130 + score * 1.5f;
            if (dist > 240) {
                bx[i] += dx / dist * bSpeed * dt;
                by[i] += dy / dist * bSpeed * dt;
                bMoving = true;
            } else if (dist < 180) {
                bx[i] -= dx / dist * 110 * dt;
                by[i] -= dy / dist * 110 * dt;
                bMoving = true;
            }
            if (Math.abs(dx) > 5) bFacing[i] = dx < 0 ? 1 : -1;
            if (bMoving) bWalkPhase[i]++; else bWalkPhase[i] = 0;

            // Боты не выходят за карту
            if (bx[i] < 70) bx[i] = 70;
            if (bx[i] > WW - 70) bx[i] = WW - 70;
            if (by[i] < 70) by[i] = 70;
            if (by[i] > WH - 70) by[i] = WH - 70;

            float fireChance = 0.014f + score * 0.0002f;
            if (Math.random() < fireChance && dist < 800 && dist > 1) {
                addBullet(bx[i], by[i], dx / dist * 520, dy / dist * 520, 1);
                bShootVisual[i] = 8;
            }
        }

        for (int i = 0; i < bulCount; i++) {
            bulx[i] += bulvx[i] * dt;
            buly[i] += bulvy[i] * dt;
            boolean dead = false;
            if (bulx[i] < 0 || bulx[i] > WW || buly[i] < 0 || buly[i] > WH) dead = true;
            if (!dead) for (float[] w : walls) {
                if (bulx[i] > w[0] && bulx[i] < w[2] && buly[i] > w[1] && buly[i] < w[3]) {
                    spawnParticles(bulx[i], buly[i], 6, 0xAA00E5FF, 220, 3);
                    dead = true; break;
                }
            }
            if (!dead && bulFrom[i] == 0) {
                for (int j = 0; j < bCount; j++) {
                    float dx = bulx[i] - bx[j], dy = buly[i] - by[j];
                    if (dx * dx + dy * dy < 50 * 50) {
                        bhp[j] -= 25;
                        bDamageFlash[j] = 5;
                        spawnParticles(bulx[i], buly[i], 10, 0xFFFFAAAA, 280, 4);
                        dead = true;
                        if (bhp[j] <= 0) {
                            spawnParticles(bx[j], by[j], 26, 0xFFFF3B6B, 380, 6);
                            shakeT = 10;
                            bx[j] = bx[bCount - 1];
                            by[j] = by[bCount - 1];
                            bhp[j] = bhp[bCount - 1];
                            bFacing[j] = bFacing[bCount - 1];
                            bShootVisual[j] = bShootVisual[bCount - 1];
                            bWalkPhase[j] = bWalkPhase[bCount - 1];
                            bDamageFlash[j] = bDamageFlash[bCount - 1];
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
                if (dx * dx + dy * dy < 50 * 50) {
                    php -= 10;
                    pDamageFlash = 8;
                    shakeT = 6;
                    spawnParticles(bulx[i], buly[i], 8, 0xFF00E5FF, 260, 4);
                    dead = true;
                }
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
        if (php <= 0) { php = 0; screen = 2; shakeT = 20; }

        updateParticles(dt);
    }

    void addBullet(float x, float y, float vx, float vy, int from) {
        if (bulCount >= MAX_BUL) return;
        bulx[bulCount] = x; buly[bulCount] = y;
        bulvx[bulCount] = vx; bulvy[bulCount] = vy;
        bulFrom[bulCount] = from;
        bulCount++;
    }

    float sx(float wx) { return W / 2f + (wx - camX) + shakeX; }
    float sy(float wy) { return H / 2f + (wy - camY) * Y_TILT + shakeY; }

    void render(Canvas c) {
        c.drawColor(0xFF060A14);

        if (screen == 0) {
            // Параллакс-фон меню
            long t = System.currentTimeMillis();
            float bob = (float) Math.sin(t * 0.001) * 8;
            paint.setAlpha(60);
            for (int gx = 0; gx < W; gx += 512) {
                for (int gy = 0; gy < H; gy += 512) {
                    c.drawBitmap(bgLayer, gx, gy + bob, paint);
                }
            }
            paint.setAlpha(255);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0x6600E5FF);
            paint.setTextSize(H * 0.21f);
            c.drawText("XoXe", W / 2f + 4, H / 2f - 36, paint);
            paint.setColor(0xFF00E5FF);
            c.drawText("XoXe", W / 2f, H / 2f - 40, paint);

            paint.setColor(0xFFFF00AA);
            paint.setTextSize(H * 0.05f);
            float pulse = 0.9f + (float) Math.sin(t * 0.004) * 0.1f;
            c.save();
            c.scale(pulse, pulse, W / 2f, H / 2f + 110);
            c.drawText("TAP TO PLAY", W / 2f, H / 2f + 110, paint);
            c.restore();
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
            paint.setColor(0xFF00E5FF);
            c.drawText("TAP TO RETRY", W / 2f, H / 2f + 180, paint);
            return;
        }

        // Параллакс дальний фон
        paint.setAlpha(80);
        float bgOffX = -camX * 0.15f;
        float bgOffY = -camY * 0.15f * Y_TILT;
        for (int gx = -512; gx < W + 512; gx += 512) {
            for (int gy = -512; gy < H + 512; gy += 512) {
                c.drawBitmap(bgLayer, gx + (bgOffX % 512), gy + (bgOffY % 512), paint);
            }
        }
        paint.setAlpha(255);

        c.save();
        c.translate(W / 2f - camX + shakeX, H / 2f - camY + shakeY);
        c.scale(1f, Y_TILT);

        // Пол из тайлов
        int startTX = (int) ((camX - W) / tileSize) - 1;
        int endTX = (int) ((camX + W) / tileSize) + 1;
        int startTY = (int) ((camY - H) / tileSize) - 1;
        int endTY = (int) ((camY + H) / tileSize) + 1;
        if (startTX < 0) startTX = 0;
        if (startTY < 0) startTY = 0;
        if (endTX > WW / tileSize) endTX = (int) (WW / tileSize);
        if (endTY > WH / tileSize) endTY = (int) (WH / tileSize);
        for (int tx = startTX; tx <= endTX; tx++) {
            for (int ty = startTY; ty <= endTY; ty++) {
                c.drawBitmap(tileFloor, tx * tileSize, ty * tileSize, paint);
            }
        }

        // Неоновая рамка арены
        paint.setColor(0xFF00E5FF);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        c.drawRect(0, 0, WW, WH, paint);
        paint.setStyle(Paint.Style.FILL);

        // Стены из тайлов
        for (float[] w : walls) {
            int tx0 = (int) (w[0] / tileSize);
            int ty0 = (int) (w[1] / tileSize);
            int tx1 = (int) ((w[2] - 1) / tileSize);
            int ty1 = (int) ((w[3] - 1) / tileSize);
            for (int tx = tx0; tx <= tx1; tx++) {
                for (int ty = ty0; ty <= ty1; ty++) {
                    c.drawBitmap(tileWall, tx * tileSize, ty * tileSize, paint);
                }
            }
            paint.setColor(0xFF00E5FF);
            paint.setStrokeWidth(4);
            paint.setStyle(Paint.Style.STROKE);
            c.drawRect(w[0], w[1], w[2], w[3], paint);
            paint.setStyle(Paint.Style.FILL);
        }

        c.restore();

        // Пули
        for (int i = 0; i < bulCount; i++) {
            float bxp = sx(bulx[i]);
            float byp = sy(buly[i]);
            if (bulletBmp != null) {
                c.save();
                c.rotate((float) Math.toDegrees(Math.atan2(bulvy[i] * Y_TILT, bulvx[i])), bxp, byp);
                c.drawBitmap(bulletBmp, bxp - bulletBmp.getWidth() / 2f,
                        byp - bulletBmp.getHeight() / 2f, paint);
                c.restore();
            } else {
                int col = bulFrom[i] == 0 ? 0xFF00E5FF : 0xFFFF3B6B;
                paint.setColor(col & 0x00FFFFFF | 0x88000000);
                c.drawCircle(bxp, byp, 20, paint);
                paint.setColor(col);
                c.drawCircle(bxp, byp, 10, paint);
            }
        }

        // Тени
        for (int i = 0; i < bCount; i++) drawShadow(c, sx(bx[i]), sy(by[i]), 45, 20);
        drawShadow(c, sx(px), sy(py), 50, 22);

        // Частицы (поверх теней, но под персонажами)
        for (int i = 0; i < partCount; i++) {
            int a = (int) (255 * partLife[i]);
            if (a < 0) a = 0;
            if (a > 255) a = 255;
            int col = (partColor[i] & 0x00FFFFFF) | (a << 24);
            paint.setColor(col);
            float ps = partSize[i] * (0.4f + partLife[i] * 0.6f);
            c.drawCircle(sx(partX[i]), sy(partY[i]), ps, paint);
        }

        // Боты
        for (int i = 0; i < bCount; i++) {
            float bx2 = sx(bx[i]);
            float by2 = sy(by[i]);
            float bob = bWalkPhase[i] > 0 ? (float) Math.sin(bWalkPhase[i] * 0.28) * 5f : 0;
            if (bStand != null) {
                Bitmap bm;
                if (bShootVisual[i] > 0) bm = bFacing[i] == 1 ? bGunF : bGun;
                else bm = bFacing[i] == 1 ? bStandF : bStand;
                if (bm == null) bm = bStand;
                if (bm != null) {
                    if (bDamageFlash[i] > 0) {
                        paint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                                0xFFFFAAAA, android.graphics.PorterDuff.Mode.SRC_ATOP));
                    }
                    c.drawBitmap(bm, bx2 - bm.getWidth() / 2f,
                            by2 - bm.getHeight() + 14 + bob, paint);
                    paint.setColorFilter(null);
                }
            } else {
                paint.setColor(0x44FF3B6B);
                c.drawCircle(bx2, by2, 40, paint);
                paint.setColor(0xFFFF3B6B);
                c.drawCircle(bx2, by2, 26, paint);
            }
            // HP бар с градиентом
            float hbw = 55;
            float hby = by2 - (bStand != null ? bStand.getHeight() : 80) - 10;
            paint.setColor(0xFF1A0F1A);
            c.drawRect(bx2 - hbw - 2, hby - 2, bx2 + hbw + 2, hby + 12, paint);
            paint.setColor(0xFF2A2A2A);
            c.drawRect(bx2 - hbw, hby, bx2 + hbw, hby + 10, paint);
            float ratio = Math.max(0, bhp[i] / (60f + score * 2f));
            if (ratio > 1f) ratio = 1f;
            paint.setShader(new LinearGradient(bx2 - hbw, 0, bx2 + hbw, 0,
                    0xFF00FF88, 0xFFFFAA00, Shader.TileMode.CLAMP));
            c.drawRect(bx2 - hbw, hby, bx2 - hbw + 110 * ratio, hby + 10, paint);
            paint.setShader(null);
        }

        // Игрок
        float pxs = sx(px), pys = sy(py);
        float pBob = pWalkPhase > 0 ? (float) Math.sin(pWalkPhase * 0.28) * 5f : 0;

        // Вспышка выстрела
        if (pShootVisual > 0 && muzzleFlash != null) {
            float fx = pxs + (pFacing == 1 ? 55 : -55);
            c.drawBitmap(muzzleFlash, fx - muzzleFlash.getWidth() / 2f,
                    pys - 80, paint);
        }

        if (pStand != null) {
            Bitmap bm;
            if (pShootVisual > 0) bm = pFacing == 1 ? pGunF : pGun;
            else bm = pFacing == 1 ? pStandF : pStand;
            if (bm == null) bm = pStand;
            if (bm != null) {
                if (pDamageFlash > 0) {
                    paint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                            0xFFFFAAAA, android.graphics.PorterDuff.Mode.SRC_ATOP));
                }
                c.drawBitmap(bm, pxs - bm.getWidth() / 2f,
                        pys - bm.getHeight() + 16 + pBob, paint);
                paint.setColorFilter(null);
            }
        } else {
            paint.setColor(0x4400E5FF);
            c.drawCircle(pxs, pys, 40, paint);
            paint.setColor(0xFF00E5FF);
            c.drawCircle(pxs, pys, 26, paint);
        }

        // Джойстик
        if (joyId != -1) {
            paint.setColor(0x22FFFFFF);
            c.drawCircle(joyCX, joyCY, joyR, paint);
            paint.setColor(0x66FFFFFF);
            c.drawCircle(joyCX, joyCY, joyR * 0.92f, paint);
            paint.setColor(0xAAFFFFFF);
            c.drawCircle(joyX, joyY, joyR * 0.35f, paint);
            paint.setColor(0x4400E5FF);
            c.drawCircle(joyCX, joyCY, joyR * 0.35f, paint);
        }

        // HUD: HP бар с градиентом
        paint.setColor(0x88000000);
        c.drawRect(28, 28, W - 28, 74, paint);
        paint.setColor(0xFF2A2A2A);
        c.drawRect(30, 30, W - 30, 72, paint);
        paint.setShader(new LinearGradient(30, 0, W - 30, 0,
                0xFF00FF88, 0xFF00E5FF, Shader.TileMode.CLAMP));
        c.drawRect(30, 30, 30 + (W - 60) * (php / PMAX), 72, paint);
        paint.setShader(null);
        paint.setColor(0xFF00E5FF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        c.drawRect(30, 30, W - 30, 72, paint);
        paint.setStyle(Paint.Style.FILL);

        // Score с тенью
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(64);
        paint.setColor(0x88000000);
        c.drawText("Score: " + score, W - 36, 172, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawText("Score: " + score, W - 40, 168, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    void drawShadow(Canvas c, float x, float y, float rx, float ry) {
        paint.setColor(0x66000000);
        RectF r = new RectF(x - rx, y - ry, x + rx, y + ry);
        c.drawOval(r, paint);
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
        else { dx = pFacing; dy = 0; }
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1) d = 1;
        float sp = 950;
        addBullet(px + dx / d * 55, py + dy / d * 55, dx / d * sp, dy / d * sp, 0);
        spawnParticles(px + dx / d * 55, py + dy / d * 55, 5, 0xAAFFFFFF, 180, 3);
        pShootCD = 11;
        pShootVisual = 8;
        // лёгкий откат
        pvx -= dx / d * 30;
        pvy -= dy / d * 30;
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
