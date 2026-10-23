package com.echofall.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameState {
    public static final int MODE_TRAINING = 0;
    public static final int MODE_DEFUSE = 1;

    public static final int TEAM_CT = 0;
    public static final int TEAM_T = 1;

    public static final float ROUND_TIME = 115f;
    public static final float BOMB_TIME = 40f;
    public static final float PLANT_TIME = 3.2f;
    public static final float DEFUSE_TIME = 5f;
    private static final float ROUND_END_PAUSE = 5f;

    // 96x96 地图上的出生点（T 在南边，CT 在北边，相距 80 米）
    public static final float[][] T_SPAWNS = {
            {48.5f, 88.5f}, {42.5f, 87.5f}, {54.5f, 87.5f}, {48.5f, 82.5f}
    };
    public static final float[][] CT_SPAWNS = {
            {48.5f, 6.5f}, {42.5f, 7.5f}, {54.5f, 7.5f}, {48.5f, 12.5f}
    };

    /** 地上掉落的枪 */
    public static class Drop {
        public final float x, z;
        public final WeaponType w;

        Drop(float x, float z, WeaponType w) {
            this.x = x;
            this.z = z;
            this.w = w;
        }
    }

    public final MapGrid map = new MapGrid();
    public final Player player = new Player();
    public final Input input = new Input();
    public final ArrayList<Bot> bots = new ArrayList<>();
    public final ArrayList<Tracer> tracers = new ArrayList<>();
    public final ArrayList<Drop> drops = new ArrayList<>();

    private final Random rnd = new Random();

    public int mode = MODE_TRAINING;
    public int bombSite = 0; // 0=A 1=B

    public float time = 0f;
    public String toast = "";
    public float toastT = 0f;
    public float damageFlash = 0f;

    public float sensitivity = 1.0f;
    public float gyroSens = 1.0f;
    public boolean gyroOn = false;

    public float roundTime = 0f;
    public float roundEndT = 0f;
    public int round = 1;
    public int scoreCT = 0, scoreT = 0;
    public String roundTitle = "";

    public boolean bombPlanted = false;
    public float bombX, bombZ;
    public float bombTimer = 0f;
    public float plantProgress = 0f;
    public float defuseProgress = 0f;

    // 渲染诊断信息（由渲染线程写入，UI 线程读取）
    public volatile String glError = null;
    public volatile String glInfo = "";
    public volatile int glFrames = 0;

    public GameState() {
        reset();
    }

    public void reset() {
        player.team = TEAM_CT;
        startNewMatch();
    }

    public void setMode(int m) {
        if (mode == m) return;
        mode = m;
        startNewMatch();
    }

    public void setTeam(int t) {
        if (player.team == t) return;
        player.team = t;
        startNewMatch();
    }

    public void startNewMatch() {
        scoreCT = 0;
        scoreT = 0;
        round = 1;
        player.kills = 0;
        player.deaths = 0;
        player.money = mode == MODE_TRAINING ? 16000 : 800;
        player.primary = null;
        player.secondary = WeaponType.PISTOL;
        player.current = WeaponType.PISTOL;
        player.armor = 0;
        for (WeaponType w : WeaponType.values()) {
            player.mag[w.ordinal()] = w.magSize;
            player.reserve[w.ordinal()] = w.reserveSize;
        }
        for (Bot b : bots) {
            b.money = 4200;
            b.weapon = WeaponType.PISTOL;
        }
        startRound();
    }

    public void startRound() {
        float[] sp = spawnFor(player.team, 0);
        player.x = sp[0];
        player.z = sp[1];
        player.y = 0f;
        player.vy = 0f;
        player.hp = 100;
        player.stance = Player.STANCE_STAND;
        player.ads = false;
        player.adsT = 0f;
        player.respawnT = 0f;
        player.switchT = 0f;
        player.reloadT = 0f;
        player.fireCd = 0f;
        player.knifeT = 0f;
        player.muzzleT = 0f;
        player.planting = false;
        player.defusing = false;
        player.hasBomb = false;
        player.yaw = player.team == TEAM_T ? 0f : (float) Math.PI;
        player.pitch = 0f;

        // 每回合补满弹匣
        for (WeaponType w : WeaponType.values()) {
            player.mag[w.ordinal()] = w.magSize;
        }

        bombPlanted = false;
        bombTimer = 0f;
        bombX = 0f;
        bombZ = 0f;
        plantProgress = 0f;
        defuseProgress = 0f;
        bombSite = rnd.nextBoolean() ? 0 : 1;
        roundTime = ROUND_TIME;
        roundEndT = 0f;
        roundTitle = "";
        tracers.clear();
        drops.clear();

        spawnBots();
        assignBombCarrier();
        for (Bot b : bots) botBuy(b);

        msg(mode == MODE_DEFUSE ? "回合 " + round + " 开始" : "训练场");
    }

    private void spawnBots() {
        int enemyTeam = 1 - player.team;
        // 列表只在第一场创建，之后复用对象，避免 UI 线程遍历时被并发修改
        if (bots.isEmpty()) {
            for (int i = 0; i < 3; i++) bots.add(makeBot(player.team, i + 1));
            for (int i = 0; i < 4; i++) bots.add(makeBot(enemyTeam, i + 1));
        }
        for (int i = 0; i < bots.size(); i++) {
            Bot b = bots.get(i);
            int team = i < 3 ? player.team : enemyTeam;
            float[] sp = spawnFor(team, i < 3 ? i + 1 : i - 2);
            b.team = team;
            b.x = sp[0];
            b.z = sp[1];
            b.hp = 100;
            b.alive = true;
            b.hitFlash = 0f;
            b.planting = false;
            b.defusing = false;
            b.animT = 0f;
            b.hasBomb = false;
            b.path.clear();
            b.pathIndex = 0;
            b.pickupX = -1f;
            b.yaw = team == TEAM_T ? 0f : (float) Math.PI;
            b.aimYaw = b.yaw;
            b.shootT = 0.6f + rnd.nextFloat();
            b.thinkT = rnd.nextFloat() * 0.5f;
            b.reactionT = 0.3f + rnd.nextFloat() * 0.4f;
            b.state = 0;
            boolean a = rnd.nextBoolean();
            b.holdX = a ? MapGrid.A_CENTER_X : MapGrid.B_CENTER_X;
            b.holdZ = a ? MapGrid.A_CENTER_Z : MapGrid.B_CENTER_Z;
            if (b.weapon == null) b.weapon = WeaponType.PISTOL;
        }
    }

    private Bot makeBot(int team, int index) {
        Bot b = new Bot();
        float[] sp = spawnFor(team, index);
        b.x = sp[0];
        b.z = sp[1];
        b.team = team;
        b.hp = 100;
        b.alive = true;
        b.skill = 0.35f + rnd.nextFloat() * 0.5f;
        b.money = 4200;
        b.weapon = WeaponType.PISTOL;
        b.yaw = team == TEAM_T ? 0f : (float) Math.PI;
        b.aimYaw = b.yaw;
        b.shootT = 0.5f + rnd.nextFloat();
        b.thinkT = rnd.nextFloat() * 0.5f;
        b.reactionT = 0.3f + rnd.nextFloat() * 0.4f;
        return b;
    }

    private float[] spawnFor(int team, int index) {
        float[][] arr = team == TEAM_T ? T_SPAWNS : CT_SPAWNS;
        return arr[index % arr.length];
    }

    public void update(float dt) {
        dt = Math.min(Math.max(dt, 0f), 0.05f);
        time += dt;
        if (toastT > 0f) toastT -= dt;
        if (damageFlash > 0f) damageFlash = Math.max(0f, damageFlash - dt * 1.8f);

        if (mode == MODE_DEFUSE) updateRound(dt);
        updatePlayer(dt);
        updateBots(dt);
        updateBomb(dt);
        updateTracers(dt);
    }

    // ---------------------------------------------------------------- 玩家

    private void updatePlayer(float dt) {
        if (player.hp <= 0) {
            if (mode == MODE_TRAINING) {
                player.respawnT -= dt;
                if (player.respawnT <= 0f) respawnPlayer();
            }
            return;
        }

        float sens = 0.0032f * sensitivity;
        player.yaw += input.lookDX * sens;
        player.pitch -= input.lookDY * sens;
        if (gyroOn) {
            player.yaw += input.gyroDX * 1.15f * gyroSens * dt;
            player.pitch += input.gyroDY * 1.15f * gyroSens * dt;
        }
        input.lookDX = 0f;
        input.lookDY = 0f;
        input.gyroDX = 0f;
        input.gyroDY = 0f;
        player.pitch = Math.max(-1.45f, Math.min(1.45f, player.pitch));

        if (input.adsPressed) {
            input.adsPressed = false;
            toggleAds();
        }
        if (input.crouchPressed) {
            input.crouchPressed = false;
            player.stance = player.stance == Player.STANCE_CROUCH
                    ? Player.STANCE_STAND : Player.STANCE_CROUCH;
            if (player.stance != Player.STANCE_STAND) player.ads = false;
        }
        if (input.pronePressed) {
            input.pronePressed = false;
            player.stance = player.stance == Player.STANCE_PRONE
                    ? Player.STANCE_STAND : Player.STANCE_PRONE;
            if (player.stance != Player.STANCE_STAND) player.ads = false;
        }
        if (input.switchPressed) {
            input.switchPressed = false;
            cycleWeapon();
        }
        if (input.reloadPressed) {
            input.reloadPressed = false;
            startReload();
        }

        float mx = input.moveX;
        float mz = input.moveY;
        float len = (float) Math.hypot(mx, mz);
        if (len > 1f) {
            mx /= len;
            mz /= len;
        }
        float speed = 3.7f * player.current.moveMult * player.stanceSpeed()
                * (1f - 0.45f * player.adsT);
        float sin = (float) Math.sin(player.yaw);
        float cos = (float) Math.cos(player.yaw);
        float dx = (cos * mx + sin * mz) * speed * dt;
        float dz = (sin * mx - cos * mz) * speed * dt;

        boolean stationary = (Math.abs(mx) + Math.abs(mz)) < 0.12f;
        if (player.planting || player.defusing) {
            // 下包/拆包时不能走动
            dx = 0f;
            dz = 0f;
        }

        if (!map.blocked(player.x + dx, player.z, 0.28f)) player.x += dx;
        if (!map.blocked(player.x, player.z + dz, 0.28f)) player.z += dz;

        if (input.jumpPressed) {
            input.jumpPressed = false;
            if (player.y <= 0.001f && player.stance == Player.STANCE_STAND) player.vy = 5.4f;
        }
        player.vy -= 18f * dt;
        player.y += player.vy * dt;
        if (player.y <= 0f) {
            player.y = 0f;
            player.vy = 0f;
        }

        float moveAmount = (float) Math.hypot(dx, dz) / Math.max(dt, 0.0001f);
        if (moveAmount > 0.2f && player.y <= 0.001f) {
            player.bob += dt * moveAmount * 1.6f;
        } else {
            player.bob += dt * 1.2f;
        }

        float adsTarget = player.ads ? 1f : 0f;
        player.adsT += (adsTarget - player.adsT) * Math.min(1f, dt * 9f);

        if (player.fireCd > 0f) player.fireCd -= dt;
        if (player.switchT > 0f) player.switchT -= dt;
        if (player.knifeT > 0f) player.knifeT -= dt * 2.2f;
        if (player.muzzleT > 0f) player.muzzleT -= dt;
        if (player.recoilT > 0f) player.recoilT -= dt;

        if (player.reloadT > 0f) {
            player.reloadT -= dt;
            if (player.reloadT <= 0f) finishReload();
        }

        tryPickup();

        // 全自动武器按住连发，单发武器需要每次按下
        boolean wantFire;
        if (player.current.auto || player.current == WeaponType.KNIFE) {
            wantFire = input.firing || input.firePressed;
        } else {
            wantFire = input.firePressed;
        }
        input.firePressed = false;
        if (wantFire && !player.planting && !player.defusing) playerFire();

        // 站着不动才能下包/拆包，由 updateBomb 判定
        player.planting = false;
        player.defusing = false;
        if (mode == MODE_DEFUSE) {
            if (player.team == TEAM_T && player.hasBomb && !bombPlanted
                    && map.inAnySite(player.x, player.z) && stationary) {
                player.planting = true;
            }
            if (player.team == TEAM_CT && bombPlanted && stationary
                    && Math.hypot(player.x - bombX, player.z - bombZ) < 1.8f) {
                player.defusing = true;
            }
        }
    }

    private void toggleAds() {
        if (player.current == WeaponType.KNIFE) {
            msg("刀不能开镜");
            return;
        }
        player.ads = !player.ads;
    }

    public float fov() {
        if (player.current.scoped) return 70f - player.adsT * 48f;
        return 70f - player.adsT * 20f;
    }

    private void playerFire() {
        if (player.hp <= 0 || player.switchT > 0f || player.reloadT > 0f || player.fireCd > 0f) return;
        WeaponType w = player.current;

        if (w == WeaponType.KNIFE) {
            player.fireCd = w.interval;
            player.knifeT = 1f;
            float cp = (float) Math.cos(player.pitch);
            float dx = (float) (Math.sin(player.yaw) * cp);
            float dy = (float) Math.sin(player.pitch);
            float dz = (float) (-Math.cos(player.yaw) * cp);
            for (Bot b : bots) {
                if (!b.alive || b.team == player.team) continue;
                float tx = b.x - player.x;
                float ty = 1.0f - (player.y + player.eyeHeight());
                float tz = b.z - player.z;
                float d = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (d > w.range) continue;
                float dot = (tx * dx + ty * dy + tz * dz) / Math.max(d, 0.001f);
                if (dot > 0.5f) damageBot(b, w.damage, true);
            }
            return;
        }

        int oi = w.ordinal();
        if (player.mag[oi] <= 0) {
            player.fireCd = 0.35f;
            msg("弹匣已空，按换弹");
            return;
        }
        player.mag[oi]--;
        player.fireCd = w.interval;
        player.muzzleT = 0.06f;

        float eyeY = player.y + player.eyeHeight();
        float spread = w.spread * (1f - 0.75f * player.adsT) * player.stanceSpeed();
        for (int i = 0; i < w.pellets; i++) {
            float yaw = player.yaw + (float) Math.toRadians((rnd.nextFloat() - 0.5f) * spread);
            float pitch = player.pitch + (float) Math.toRadians((rnd.nextFloat() - 0.5f) * spread);
            float cp = (float) Math.cos(pitch);
            float dx = (float) (Math.sin(yaw) * cp);
            float dy = (float) Math.sin(pitch);
            float dz = (float) (-Math.cos(yaw) * cp);
            playerShoot(w, player.x, eyeY, player.z, dx, dy, dz);
        }

        player.pitch = Math.min(1.45f, player.pitch + w.recoil * 0.004f);
        player.recoilT = 1f;
    }

    private void playerShoot(WeaponType w, float ox, float oy, float oz,
                             float dx, float dy, float dz) {
        float wallT = rayWallT(ox, oy, oz, dx, dy, dz, w.range);
        float maxT = wallT;

        Bot best = null;
        float bestT = maxT;
        boolean head = false;

        for (Bot b : bots) {
            if (!b.alive || b.team == player.team) continue;
            float ht = raySphere(ox, oy, oz, dx, dy, dz, b.x, 1.70f, b.z, 0.24f);
            float bt = raySphere(ox, oy, oz, dx, dy, dz, b.x, 0.98f, b.z, 0.52f);
            if (ht > 0f && ht < bestT) {
                bestT = ht;
                best = b;
                head = true;
            }
            if (bt > 0f && bt < bestT) {
                bestT = bt;
                best = b;
                head = false;
            }
        }

        float endT = best != null ? bestT : maxT;
        addTracer(ox, oy - 0.10f, oz, ox + dx * endT, oy + dy * endT, oz + dz * endT);

        if (best != null) {
            int dmg = head ? Math.round(w.damage * 3.4f) : w.damage;
            if (w == WeaponType.AWP && head) dmg = 500;
            damageBot(best, dmg, true);
            if (head) msg("爆头！");
        }
    }

    private float rayWallT(float ox, float oy, float oz, float dx, float dy, float dz, float maxT) {
        float t = 0.25f;
        while (t < maxT) {
            float x = ox + dx * t;
            float y = oy + dy * t;
            float z = oz + dz * t;
            if (y <= 0.02f || y >= 3.0f) return t;
            int cx = (int) Math.floor(x);
            int cz = (int) Math.floor(z);
            if (map.isWall(cx, cz)) return t;
            // 矮掩体只有 1.2 米，低于掩体高度才挡子弹
            if (map.kindAt(cx, cz) == MapGrid.COVER && y <= MapGrid.COVER_H) return t;
            t += 0.25f;
        }
        return maxT;
    }

    private float raySphere(float ox, float oy, float oz, float dx, float dy, float dz,
                            float cx, float cy, float cz, float r) {
        float lx = cx - ox, ly = cy - oy, lz = cz - oz;
        float t = lx * dx + ly * dy + lz * dz;
        if (t < 0f) return -1f;
        float d2 = lx * lx + ly * ly + lz * lz - t * t;
        if (d2 > r * r) return -1f;
        return t - (float) Math.sqrt(r * r - d2);
    }

    private void addTracer(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (tracers.size() > 72) tracers.remove(0);
        tracers.add(new Tracer(x0, y0, z0, x1, y1, z1));
    }

    private void updateTracers(float dt) {
        for (int i = tracers.size() - 1; i >= 0; i--) {
            Tracer t = tracers.get(i);
            t.life -= dt;
            if (t.life <= 0f) tracers.remove(i);
        }
    }

    // ---------------------------------------------------------------- 掉落物

    private void dropWeapon(float x, float z, WeaponType w) {
        if (w == null || w == WeaponType.KNIFE || w == WeaponType.PISTOL) return;
        if (drops.size() > 16) drops.remove(0);
        drops.add(new Drop(x, z, w));
    }

    private void tryPickup() {
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            if (Math.hypot(d.x - player.x, d.z - player.z) > 1.5f) continue;
            WeaponType w = d.w;
            boolean better = w.price > player.current.price + 150;
            boolean needPrimary = w.primary && player.primary == null;
            if (!better && !needPrimary) continue;
            drops.remove(i);
            if (w.primary) player.primary = w;
            else player.secondary = w;
            player.current = w;
            player.mag[w.ordinal()] = w.magSize;
            player.reserve[w.ordinal()] = Math.max(player.reserve[w.ordinal()], w.reserveSize);
            player.switchT = 0.35f;
            player.reloadT = 0f;
            msg("拾取 " + w.label);
        }
    }

    // ---------------------------------------------------------------- 机器人

    private void updateBots(float dt) {
        for (Bot b : bots) {
            if (!b.alive) {
                if (mode == MODE_TRAINING) {
                    b.respawnT -= dt;
                    if (b.respawnT <= 0f) respawnBot(b);
                }
                continue;
            }
            if (b.hitFlash > 0f) b.hitFlash = Math.max(0f, b.hitFlash - dt);
            if (b.reactionT > 0f) b.reactionT -= dt;
            b.thinkT -= dt;
            b.shootT -= dt;
            b.strafeT -= dt;
            b.scanT -= dt;

            boolean los = acquireTarget(b);
            b.planting = false;
            b.defusing = false;

            // ---- 下包 / 拆包 ----
            boolean busy = false;
            if (mode == MODE_DEFUSE) {
                if (!bombPlanted && b.team == TEAM_T && b.hasBomb
                        && map.inAnySite(b.x, b.z)) {
                    b.planting = true;
                    b.animT += dt;
                    busy = true;
                    float sx = map.inSiteA(b.x, b.z) ? MapGrid.A_CENTER_X : MapGrid.B_CENTER_X;
                    float sz = map.inSiteA(b.x, b.z) ? MapGrid.A_CENTER_Z : MapGrid.B_CENTER_Z;
                    b.aimYaw = turnToward(b.aimYaw,
                            (float) Math.atan2(sx - b.x, -(sz - b.z)), 3f * dt);
                } else if (bombPlanted && b.team == TEAM_CT
                        && Math.hypot(b.x - bombX, b.z - bombZ) < 1.6f) {
                    b.defusing = true;
                    b.animT += dt;
                    busy = true;
                    b.aimYaw = turnToward(b.aimYaw,
                            (float) Math.atan2(bombX - b.x, -(bombZ - b.z)), 3f * dt);
                }
            }
            if (!busy) b.animT = 0f;

            // 正在下包/拆包时不会还手，除非敌人已经贴脸
            boolean panicked = busy && los && b.targetDist < 6f;

            if (los && b.targetDist < 26f && !busy || los && b.targetDist < 6f) {
                b.state = 2;
                float want = (float) Math.atan2(b.targetX - b.x, -(b.targetZ - b.z));
                b.aimYaw = turnToward(b.aimYaw, want, (3.0f + b.skill * 3.5f) * dt);
                if (!busy) b.yaw = b.aimYaw;

                if (!busy) {
                    if (b.targetDist > 12f) {
                        moveToward(b, b.targetX, b.targetZ, dt, 3.0f);
                    } else if (b.targetDist < 4.5f) {
                        moveToward(b, b.x - (b.targetX - b.x), b.z - (b.targetZ - b.z), dt, 2.0f);
                    } else {
                        strafe(b, dt);
                    }
                    if (b.reactionT <= 0f && b.shootT <= 0f) botShoot(b);
                } else if (panicked) {
                    b.planting = false;
                    b.defusing = false;
                    if (b.reactionT <= 0f && b.shootT <= 0f) botShoot(b);
                }
            } else if (!busy) {
                b.state = 1;
                b.reactionT = Math.max(b.reactionT, 0.35f + (1f - b.skill) * 0.4f);

                scanForWeapon(b);
                float[] obj = objectiveFor(b);
                if (b.thinkT <= 0f || b.pathIndex >= b.path.size()) {
                    List<float[]> p = map.findPath(b.x, b.z, obj[0], obj[1]);
                    b.path.clear();
                    b.path.addAll(p);
                    b.pathIndex = 0;
                    b.thinkT = 1.0f + rnd.nextFloat() * 0.8f;
                }
                followPath(b, 3.0f * dt);
            }
        }
    }

    private float[] objectiveFor(Bot b) {
        if (mode == MODE_DEFUSE) {
            if (bombPlanted) return new float[]{bombX, bombZ};
            if (b.hasBomb) {
                return bombSite == 0
                        ? new float[]{MapGrid.A_CENTER_X, MapGrid.A_CENTER_Z}
                        : new float[]{MapGrid.B_CENTER_X, MapGrid.B_CENTER_Z};
            }
            if (player.team == TEAM_T && player.hp > 0 && player.hasBomb) {
                // 跟着拿包的玩家一起推进
                return new float[]{player.x + (b.team == TEAM_T ? 2f : -2f), player.z + 1.5f};
            }
            if (b.team == TEAM_T) {
                // 没拿包的土匪跟着包点推进，保持散开
                float cx = bombSite == 0 ? MapGrid.A_CENTER_X : MapGrid.B_CENTER_X;
                float cz = bombSite == 0 ? MapGrid.A_CENTER_Z : MapGrid.B_CENTER_Z;
                float ang = (b.hashCode() % 628) / 100f;
                return new float[]{cx + (float) Math.cos(ang) * 3.5f,
                        cz + (float) Math.sin(ang) * 3.5f};
            }
        }
        // 人少的时候抱团行动
        if (aliveCount(b.team) < 3) {
            Bot leader = teamLeader(b.team, b);
            if (leader != null) {
                float ang = (b.hashCode() % 628) / 100f;
                return new float[]{leader.x + (float) Math.cos(ang) * 2.0f,
                        leader.z + (float) Math.sin(ang) * 2.0f};
            }
        }
        // 想捡的枪优先
        if (b.pickupX >= 0f) return new float[]{b.pickupX, b.pickupZ};
        return new float[]{b.holdX, b.holdZ};
    }

    private Bot teamLeader(int team, Bot self) {
        for (Bot o : bots) {
            if (o != self && o.alive && o.team == team) return o;
        }
        return null;
    }

    private void scanForWeapon(Bot b) {
        if (b.scanT > 0f) return;
        b.scanT = 0.9f + rnd.nextFloat() * 0.7f;
        if (b.pickupX >= 0f) {
            float d = (float) Math.hypot(b.pickupX - b.x, b.pickupZ - b.z);
            if (d < 0.9f) {
                pickupForBot(b);
                return;
            }
            if (d > 14f) b.pickupX = -1f;
        }
        Drop best = null;
        float bestScore = 0f;
        for (Drop d : drops) {
            float dist = (float) Math.hypot(d.x - b.x, d.z - b.z);
            if (dist > 12f) continue;
            if (d.w.price <= b.weapon.price + 200) continue;
            float score = d.w.price - dist * 60f;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best != null) {
            b.pickupX = best.x;
            b.pickupZ = best.z;
        }
    }

    private void pickupForBot(Bot b) {
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            if (Math.hypot(d.x - b.x, d.z - b.z) > 1.2f) continue;
            if (d.w.price <= b.weapon.price) continue;
            b.weapon = d.w;
            drops.remove(i);
            break;
        }
        b.pickupX = -1f;
    }

    private boolean acquireTarget(Bot b) {
        float best = 1e9f;
        boolean found = false;
        int bestBot = -1;

        float viewLimit = Math.min(40f, b.weapon.range * 0.55f);
        for (int i = 0; i < bots.size(); i++) {
            Bot o = bots.get(i);
            if (!o.alive || o.team == b.team) continue;
            float d = (float) Math.hypot(o.x - b.x, o.z - b.z);
            if (d < best && d < viewLimit && map.lineOfSight(b.x, b.z, o.x, o.z)) {
                best = d;
                found = true;
                bestBot = i;
            }
        }
        if (player.hp > 0 && player.team != b.team) {
            float d = (float) Math.hypot(player.x - b.x, player.z - b.z);
            if (d < best && d < viewLimit && map.lineOfSight(b.x, b.z, player.x, player.z)) {
                best = d;
                found = true;
                bestBot = -1;
            }
        }
        if (found) {
            if (bestBot >= 0) {
                Bot o = bots.get(bestBot);
                b.targetX = o.x;
                b.targetZ = o.z;
            } else {
                b.targetX = player.x;
                b.targetZ = player.z;
            }
            b.targetDist = best;
            b.targetBot = bestBot;
            b.targetIsPlayer = bestBot < 0;
        }
        return found;
    }

    private void botShoot(Bot b) {
        WeaponType w = b.weapon;
        float rate = Math.max(w.interval, 0.11f);
        b.shootT = rate * (1.75f - b.skill * 0.6f) + rnd.nextFloat() * 0.35f;

        float gunX = b.x + (float) Math.sin(b.yaw) * 0.35f;
        float gunZ = b.z - (float) Math.cos(b.yaw) * 0.35f;

        float rangeFactor = Math.min(1f, b.targetDist / Math.max(w.range, 1f));
        float acc = Math.max(0.10f, 0.34f + b.skill * 0.48f - b.targetDist * 0.010f);
        acc *= 1.05f - 0.45f * rangeFactor;
        if (w.scoped) acc += 0.06f;
        boolean hit = rnd.nextFloat() < acc;

        float ex = b.targetX;
        float ez = b.targetZ;
        float ey = 1.15f;

        if (hit) {
            boolean head = rnd.nextFloat() < 0.06f + b.skill * 0.11f;
            int base = Math.round(w.damage * 0.60f * (0.8f + rnd.nextFloat() * 0.4f));
            if (b.targetIsPlayer) {
                damagePlayer(head ? Math.round(base * 1.9f) : base);
            } else {
                Bot o = botByIndex(b.targetBot);
                if (o != null && o.alive) {
                    damageBot(o, head ? Math.round(base * 2.0f) : base, false);
                    if (o.hp <= 0) b.money += 300;
                }
            }
            ey = 1.35f;
        } else {
            ex += (rnd.nextFloat() - 0.5f) * 2.6f;
            ez += (rnd.nextFloat() - 0.5f) * 2.6f;
        }
        addTracer(gunX, 1.16f, gunZ, ex, ey, ez);
    }

    private Bot botByIndex(int i) {
        if (i < 0 || i >= bots.size()) return null;
        return bots.get(i);
    }

    private void strafe(Bot b, float dt) {
        if (b.strafeT <= 0f) {
            b.strafeT = 0.6f + rnd.nextFloat() * 0.8f;
            b.strafeDir = rnd.nextBoolean() ? 1 : -1;
        }
        float perpX = (float) Math.cos(b.yaw) * b.strafeDir;
        float perpZ = (float) Math.sin(b.yaw) * b.strafeDir;
        float step = 1.9f * dt;
        if (!map.blocked(b.x + perpX * step, b.z, 0.3f)) b.x += perpX * step;
        if (!map.blocked(b.x, b.z + perpZ * step, 0.3f)) b.z += perpZ * step;
    }

    private void moveToward(Bot b, float tx, float tz, float dt, float speed) {
        float dx = tx - b.x;
        float dz = tz - b.z;
        float d = (float) Math.hypot(dx, dz);
        if (d < 0.6f) return;
        float mx = dx / d * speed * dt;
        float mz = dz / d * speed * dt;
        if (!map.blocked(b.x + mx, b.z, 0.3f)) b.x += mx;
        if (!map.blocked(b.x, b.z + mz, 0.3f)) b.z += mz;
    }

    private void followPath(Bot b, float step) {
        if (b.path.isEmpty() || b.pathIndex >= b.path.size()) return;
        float[] target = b.path.get(b.pathIndex);
        float dx = target[0] - b.x;
        float dz = target[1] - b.z;
        float d = (float) Math.hypot(dx, dz);
        if (d < 0.4f) {
            b.pathIndex++;
            return;
        }
        // 路径平滑：能直接看到下一个点就跳过当前点
        if (b.pathIndex + 1 < b.path.size()) {
            float[] nxt = b.path.get(b.pathIndex + 1);
            if (map.lineOfSight(b.x, b.z, nxt[0], nxt[1])) {
                b.pathIndex++;
                return;
            }
        }
        float nx = dx / d;
        float nz = dz / d;
        float mx = nx * step;
        float mz = nz * step;
        if (!map.blocked(b.x + mx, b.z, 0.3f)) b.x += mx;
        if (!map.blocked(b.x, b.z + mz, 0.3f)) b.z += mz;
        b.yaw = (float) Math.atan2(nx, -nz);
        b.aimYaw = b.yaw;
    }

    private float turnToward(float cur, float target, float maxStep) {
        float diff = target - cur;
        while (diff > Math.PI) diff -= (float) (Math.PI * 2);
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);
        if (Math.abs(diff) <= maxStep) return target;
        return cur + Math.signum(diff) * maxStep;
    }

    private void respawnBot(Bot b) {
        float[] sp = spawnFor(b.team, rnd.nextInt(4));
        b.x = sp[0];
        b.z = sp[1];
        b.hp = 100;
        b.alive = true;
        b.hitFlash = 0f;
        b.path.clear();
        b.pathIndex = 0;
        b.pickupX = -1f;
        b.shootT = 1f;
        b.reactionT = 0.4f;
        b.state = 0;
        b.planting = false;
        b.defusing = false;
    }

    /** 回合开始时给 AI 买枪 */
    private void botBuy(Bot b) {
        b.money = Math.min(b.money, 16000);
        WeaponType want = null;
        if (b.money >= WeaponType.AWP.price && rnd.nextFloat() < 0.18f) {
            want = WeaponType.AWP;
        } else if (b.money >= WeaponType.RIFLE.price) {
            float r = rnd.nextFloat();
            want = r < 0.55f ? WeaponType.RIFLE
                    : (r < 0.80f ? WeaponType.SMG : WeaponType.SHOTGUN);
        } else if (b.money >= WeaponType.SMG.price) {
            want = rnd.nextBoolean() ? WeaponType.SMG : WeaponType.SHOTGUN;
        } else if (b.money >= WeaponType.DEAGLE.price) {
            want = WeaponType.DEAGLE;
        }
        if (want == null || want.price <= b.weapon.price) return;
        b.money -= want.price;
        b.weapon = want;
    }

    private void assignBombCarrier() {
        for (Bot b : bots) b.hasBomb = false;
        player.hasBomb = false;
        if (player.team == TEAM_T) {
            player.hasBomb = true;
            return;
        }
        for (Bot b : bots) {
            if (b.alive && b.team == TEAM_T) {
                b.hasBomb = true;
                return;
            }
        }
    }

    private void transferBomb(Bot dead) {
        if (!dead.hasBomb) return;
        dead.hasBomb = false;
        for (Bot b : bots) {
            if (b.alive && b.team == TEAM_T) {
                b.hasBomb = true;
                return;
            }
        }
        if (player.team == TEAM_T && player.hp > 0) player.hasBomb = true;
    }

    // ---------------------------------------------------------------- 伤害

    private void damageBot(Bot b, int dmg, boolean byPlayer) {
        if (!b.alive) return;
        b.hp -= dmg;
        b.hitFlash = 0.12f;
        if (b.hp <= 0) {
            b.alive = false;
            b.path.clear();
            b.pathIndex = 0;
            b.planting = false;
            b.defusing = false;
            dropWeapon(b.x, b.z, b.weapon);
            transferBomb(b);
            if (mode == MODE_TRAINING) b.respawnT = 4f;
            if (byPlayer) {
                player.money += 300;
                player.kills++;
                msg("击杀 +$300");
            }
            if (mode == MODE_DEFUSE && !bombPlanted) updateRoundWinCheck();
        }
    }

    private void updateRoundWinCheck() {
        if (roundEndT > 0f) return;
        if (aliveCount(TEAM_T) == 0) endRound("T 被全部消灭", TEAM_CT);
    }

    private void damagePlayer(int dmg) {
        if (player.hp <= 0) return;
        int left = dmg;
        if (player.armor > 0) {
            int absorbed = Math.min(player.armor, left / 2);
            player.armor -= absorbed;
            left -= absorbed;
        }
        player.hp -= left;
        damageFlash = 0.7f;
        if (player.hp <= 0) {
            player.hp = 0;
            player.deaths++;
            dropWeapon(player.x, player.z, player.current);
            if (player.hasBomb) {
                player.hasBomb = false;
                for (Bot b : bots) {
                    if (b.alive && b.team == TEAM_T) {
                        b.hasBomb = true;
                        break;
                    }
                }
            }
            if (mode == MODE_TRAINING) {
                player.respawnT = 3f;
                msg("你被击倒，3 秒后重生");
            } else {
                msg("你已阵亡，等待回合结束");
            }
        }
    }

    private void respawnPlayer() {
        float[] sp = spawnFor(player.team, rnd.nextInt(4));
        player.x = sp[0];
        player.z = sp[1];
        player.y = 0f;
        player.vy = 0f;
        player.hp = 100;
        player.stance = Player.STANCE_STAND;
        player.ads = false;
        player.respawnT = 0f;
        player.mag[player.current.ordinal()] = player.current.magSize;
        msg("已重生");
    }

    // ---------------------------------------------------------------- 炸弹 / 回合

    private void updateRound(float dt) {
        if (roundEndT > 0f) {
            roundEndT -= dt;
            if (roundEndT <= 0f) {
                round++;
                startRound();
            }
            return;
        }
        if (roundTime > 0f) roundTime -= dt;
        if (roundTime <= 0f && !bombPlanted) {
            endRound("时间到 · CT 获胜", TEAM_CT);
            return;
        }
        int ct = aliveCount(TEAM_CT);
        int t = aliveCount(TEAM_T);
        if (t == 0 && !bombPlanted) {
            endRound("T 被全部消灭", TEAM_CT);
        } else if (ct == 0) {
            endRound("CT 被全部消灭", TEAM_T);
        }
    }

    private void updateBomb(float dt) {
        if (mode != MODE_DEFUSE || roundEndT > 0f) return;

        if (!bombPlanted) {
            boolean planter = player.planting;
            Bot planterBot = null;
            for (Bot b : bots) {
                if (b.alive && b.planting) {
                    planter = true;
                    planterBot = b;
                    break;
                }
            }
            if (planter) {
                plantProgress += dt;
                if (planterBot != null) {
                    bombX = planterBot.x;
                    bombZ = planterBot.z;
                } else {
                    bombX = player.x;
                    bombZ = player.z;
                }
                if (plantProgress >= PLANT_TIME) plantBomb();
            } else {
                plantProgress = Math.max(0f, plantProgress - dt * 0.6f);
            }
            return;
        }

        bombTimer -= dt;
        if (bombTimer <= 0f) {
            endRound("炸弹爆炸 · T 获胜", TEAM_T);
            return;
        }

        boolean near = player.defusing;
        for (Bot b : bots) {
            if (b.alive && b.defusing) {
                near = true;
                break;
            }
        }
        if (near) {
            defuseProgress += dt;
            if (defuseProgress >= DEFUSE_TIME) {
                endRound("炸弹已拆除 · CT 获胜", TEAM_CT);
            }
        } else {
            defuseProgress = Math.max(0f, defuseProgress - dt * 0.6f);
        }
    }

    private void plantBomb() {
        bombPlanted = true;
        bombTimer = BOMB_TIME;
        plantProgress = PLANT_TIME;
        defuseProgress = 0f;
        player.hasBomb = false;
        for (Bot b : bots) b.hasBomb = false;
        msg("炸弹已安放！");
    }

    private void endRound(String title, int winner) {
        if (roundEndT > 0f) return;
        roundTitle = title;
        roundEndT = ROUND_END_PAUSE;
        if (winner == TEAM_CT) scoreCT++;
        else scoreT++;
        boolean playerWon = winner == player.team;
        player.money = Math.min(16000, player.money + (playerWon ? 3250 : 1400));
        for (Bot b : bots) {
            b.money = Math.min(16000, b.money + (b.team == winner ? 3250 : 1400));
        }
        msg(title);
    }

    public int aliveCount(int team) {
        int n = 0;
        if (player.team == team && player.hp > 0) n++;
        for (Bot b : bots) {
            if (b.team == team && b.alive) n++;
        }
        return n;
    }

    public String teamName(int team) {
        return team == TEAM_CT ? "CT" : "T";
    }

    // ---------------------------------------------------------------- 武器 / 商店

    private void cycleWeapon() {
        ArrayList<WeaponType> list = new ArrayList<>();
        if (player.primary != null) list.add(player.primary);
        if (player.secondary != null) list.add(player.secondary);
        list.add(WeaponType.KNIFE);
        if (list.size() < 2) return;
        int idx = list.indexOf(player.current);
        player.current = list.get((idx + 1) % list.size());
        player.switchT = 0.35f;
        player.reloadT = 0f;
        player.ads = false;
        msg("切换：" + player.current.label);
    }

    private void startReload() {
        WeaponType w = player.current;
        if (w == WeaponType.KNIFE || player.switchT > 0f) return;
        int oi = w.ordinal();
        if (player.mag[oi] >= w.magSize) return;
        if (player.reserve[oi] <= 0) {
            msg("没有备用弹药");
            return;
        }
        player.reloadT = 1.1f;
        msg("换弹中...");
    }

    private void finishReload() {
        WeaponType w = player.current;
        int oi = w.ordinal();
        int need = w.magSize - player.mag[oi];
        int take = Math.min(need, player.reserve[oi]);
        player.mag[oi] += take;
        player.reserve[oi] -= take;
        player.reloadT = 0f;
    }

    public boolean buy(String id) {
        switch (id) {
            case "rifle":
                return buyWeapon(WeaponType.RIFLE);
            case "smg":
                return buyWeapon(WeaponType.SMG);
            case "shotgun":
                return buyWeapon(WeaponType.SHOTGUN);
            case "awp":
                return buyWeapon(WeaponType.AWP);
            case "deagle":
                return buyWeapon(WeaponType.DEAGLE);
            case "pistol":
                return buyWeapon(WeaponType.PISTOL);
            case "armor":
                if (player.armor >= 100) {
                    msg("护甲已满");
                    return false;
                }
                if (player.money < 650) {
                    msg("资金不足");
                    return false;
                }
                player.money -= 650;
                player.armor = 100;
                msg("已购买护甲");
                return true;
            case "ammo":
                if (player.current == WeaponType.KNIFE) {
                    msg("刀不需要弹药");
                    return false;
                }
                if (player.money < 50) {
                    msg("资金不足");
                    return false;
                }
                player.money -= 50;
                int oi = player.current.ordinal();
                player.reserve[oi] += player.current.magSize * 2;
                msg("已购买弹药");
                return true;
            case "sell":
                return sellCurrent();
            default:
                return false;
        }
    }

    private boolean buyWeapon(WeaponType w) {
        if (player.owns(w)) {
            msg("已经拥有 " + w.label);
            return false;
        }
        if (player.money < w.price) {
            msg("资金不足");
            return false;
        }
        player.money -= w.price;
        if (w.primary) player.primary = w;
        else player.secondary = w;
        player.current = w;
        player.mag[w.ordinal()] = w.magSize;
        player.reserve[w.ordinal()] = w.reserveSize;
        player.switchT = 0.35f;
        player.reloadT = 0f;
        player.ads = false;
        msg("已购买 " + w.label);
        return true;
    }

    private boolean sellCurrent() {
        if (player.current == WeaponType.KNIFE) {
            msg("刀不能出售");
            return false;
        }
        WeaponType w = player.current;
        int refund = w.price / 2;
        player.money += refund;
        if (w == player.primary) player.primary = null;
        if (w == player.secondary) player.secondary = null;
        if (player.primary != null) player.current = player.primary;
        else if (player.secondary != null) player.current = player.secondary;
        else player.current = WeaponType.KNIFE;
        player.switchT = 0.35f;
        player.ads = false;
        msg("已出售，获得 $" + refund);
        return true;
    }

    private void msg(String s) {
        toast = s;
        toastT = 1.6f;
    }
}
