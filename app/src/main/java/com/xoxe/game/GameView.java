package com.xoxe.game;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
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
    static final float Y_TILT = 0.7f;
    static final Random RNG = new Random();

    Thread thread;
    volatile boolean running;
    SurfaceHolder holder;
    Paint paint;
    int W, H;

    int screen = 0;

    float joyCX, joyCY, joyR, joyX, joyY;
    int joyId = -1;

    final float WW = 3000f, WH = 3000f;
    float camX, camY;

    float px, py, pvx, pvy, php;
    final float PMAX = 100f;
    int pShootCD = 0;
    int pFacing = 1;
    int pShootVisual = 0;
    int pWalkPhase = 0;

    final int MAX_BOTS = 8;
    float[] bx = new float[MAX_BOTS];
    float[] by = new float[MAX_BOTS];
    float[] bhp = new float[MAX_BOTS];
    int[] bFacing = new int[MAX_BOTS];
    int[] bShootVisual = new int[MAX_BOTS];
    int[] bWalkPhase = new int[MAX_BOTS];
    int bCount = 0;

    final int MAX_BUL = 300;
    float[] bulx = new float[MAX_BUL];
    float[] buly = new float[MAX_BUL];
    float[] bulvx = new float[MAX_BUL];
    float[] bulvy = new float[MAX_BUL];
    int[] bulFrom = new int[MAX_BUL];
    int bulCount = 0;

    final int MAX_PARTS = 500;
    float[] partX = new float[MAX_PARTS];
    float[] partY = new float[MAX_PARTS];
    float[] partVX = new float[MAX_PARTS];
    float[] partVY = new float[MAX_PARTS];
    float[] partLife = new float[MAX_PARTS];
    int[] partColor = new int[MAX_PARTS];
    int partCount = 0;

    // Тайлы арены (заполняются в loadSprites)
    Bitmap tileWall, tileFloor;
    int tileSize = 128;

    // Спрайты
    Bitmap pStand, pGun;
    Bitmap pStandF, pGunF;
    Bitmap bStand, bGun;
    Bitmap bStandF, bGunF;
    Bitmap bulletBmp;
    Bitmap muzzleFlash;
    int pW, pH, bW, bH;

    // Параллакс-фон
    Bitmap bgLayer;

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
        String tileWallP = null, tileFloorP = null;

        for (String p : all) {
            String low = p.toLowerCase();
            String[] parts = low.split("/");
            String file = parts[parts.length - 1];

            // Персонажи
            if (file.contains("soldier")) {
                if (file.contains("stand") && pStandP == null) pStandP = p;
                if (file.contains("gun") && !file.contains("machine") && pGunP == null) pGunP = p;
            }
            if (file.contains("zombie") || file.contains("robot")) {
                if (file.contains("stand") && bStandP == null) bStandP = p;
                if (file.contains("gun") && bGunP == null) bGunP = p;
            }
            // Пули и эффекты
            if (file.contains("bullet") && bulletP == null) bulletP = p;
            if (file.contains("muzzle") && muzzleP == null) muzzleP = p;
            // Тайлы (ищем в Tilesheet)
            if (low.contains("tilesheet")) {
                if (file.contains("tile_") && tileWallP == null) tileWallP = p;
                if (file.contains("floor") && tileFloorP == null) tileFloorP = p;
            }
        }

        pStand = scale(load(am, pStandP), 128);
        pGun = scale(load(am, pGunP != null ? pGunP : pStandP), 128);
        bStand = scale(load(am, bStandP != null ? bStandP : pStandP), 128);
        bGun = scale(load(am, bGunP != null ? bGunP : bStandP), 128);
        bulletBmp = scale(load(am, bulletP), 32);
        muzzleFlash = scale(load(am, muzzleP), 64);

        // Пытаемся загрузить тайлы
        if (tileWallP != null) tileWall = load(am, tileWallP);
        if (tileFloorP != null) tileFloor = load(am, tileFloorP);

        // Если не нашли — генерируем примитивные тайлы программно
        if (tileWall == null) {
            tileWall = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tileWall);
            Paint p = new Paint();
            p.setColor(0xFF1E2A4A); c.drawRect(0,0,tileSize,tileSize,p);
            p.setColor(0xFF00E5FF); p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4); c.drawRect(0,0,tileSize,tileSize,p);
        }
        if (tileFloor == null) {
            tileFloor = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tileFloor);
            Paint p = new Paint();
            p.setColor(0xFF0F1420); c.drawRect(0,0,tileSize,tileSize,p);
        }

        // Фон (для параллакса) — генерируем программно
        bgLayer = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(bgLayer);
        Paint bp = new Paint();
        bp.setColor(0xFF0A0E1A); bc.drawRect(0,0,512,512,bp);
        bp.setColor(0x22FFFFFF);
        for (int i = 0; i < 40; i++) {
            bc.drawCircle(RNG.nextInt(512), RNG.nextInt(512), RNG.nextInt(4) + 1, bp);
        }

        pStandF = flipH(pStand);
        pGunF = flipH(pGun);
        bStandF = flipH(bStand);
        bGunF = flipH(bGun);

        if (pStand != null) { pW = pStand.getWidth(); pH = pStand.getHeight(); }
        if (bStand != null) { bW = bStand.getWidth(); bH = bStand.getHeight(); }
    }

    // ... (методы collectPngs, load, scale, flipH остаются без изменений) ...

    void spawnParticles(float x, float y, int count, int color, float speed) {
        for (int i = 0; i < count; i++) {
            if (partCount >= MAX_PARTS) break;
            partX[partCount] = x;
            partY[partCount] = y;
            float ang = RNG.nextFloat() * 6.28f;
            float sp = speed * (0.5f + RNG.nextFloat() * 0.5f);
            partVX[partCount] = (float)Math.cos(ang) * sp;
            partVY[partCount] = (float)Math.sin(ang) * sp;
            partLife[partCount] = 1.0f;
            partColor[partCount] = color;
            partCount++;
        }
    }

    void updateParticles(float dt) {
        for (int i = 0; i < partCount; i++) {
            partX[i] += partVX[i] * dt;
            partY[i] += partVY[i] * dt;
            partLife[i] -= dt * 2.0f;
            if (partLife[i] <= 0) {
                partX[i] = partX[partCount-1];
                partY[i] = partY[partCount-1];
                partVX[i] = partVX[partCount-1];
                partVY[i] = partVY[partCount-1];
                partLife[i] = partLife[partCount-1];
                partColor[i] = partColor[partCount-1];
                partCount--;
                i--;
            }
        }
    }

    // ... (остальные методы update, render и т.д. нужно адаптировать) ...
}
