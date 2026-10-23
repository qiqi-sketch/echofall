package com.echofall.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameState {
    public final MapGrid map = new MapGrid();
    public final Player player = new Player();
    public final Input input = new Input();
    public final ArrayList<Bot> bots = new ArrayList<>();
    private final Random rnd = new Random();

    public float time = 0f;
    public String toast = "";
    public float toastT = 0f;
    public float damageFlash = 0f;

    private static final float[][] SPAWNS = {
            {3.5f, 3.5f}, {MapGrid.W - 3.5f, 3.5f},
            {3.5f, MapGrid.H - 3.5f}, {MapGrid.W - 3.5f, MapGrid.H - 3.5f}
    };

    public GameState() {
        reset();
    }

    public void reset() {
        player.x = 3.5f;
        player.z = 3.5f;
        player.y = 0f;
        player.vy = 0f;
        player.yaw = (float) (Math.PI * 0.25);
        player.pitch = 0f;
        player.hp = 100;
        player.armor = 0;
        player.money = 800;
        player.kills = 0;
        player.deaths = 0;
        player.current = WeaponType.PISTOL;
        player.owned.clear();
        player.owned.add(WeaponType.KNIFE);
        player.owned.add(WeaponType.PISTOL);
        for (WeaponType w : WeaponType.values()) {
            player.mag[w.ordinal()] = w.magSize;
            player.reserve[w.ordinal()] = w.reserveSize;
        }
        player.fireCd = 0f;
        player.reloadT = 0f;
        player.switchT = 0f;
        player.knifeT = 0f;
        player.muzzleT = 0f;
        player.respawnT = 0f;

        bots.clear();
        for (int i = 0; i < 4; i++) {
            Bot b = new Bot();
            float[] s = SPAWNS[(i + 1) % SPAWNS.length];
            b.x = s[0] + (i % 2) * 0.8f;
            b.z = s[1] + (i / 2) * 0.8f;
            b.yaw = 0f;
            b.hp = 100;
            b.alive = true;
            bots.add(b);
        }
        toast = "购买武器，击杀 BOT";
        toastT = 3f;
    }

    public void update(float dt) {
        dt = Math.min(dt, 0.05f);
        time += dt;
        if (toastT > 0) toastT -= dt;
        if (damageFlash > 0) damageFlash -= dt * 1.8f;

        updatePlayer(dt);
        updateBots(dt);
    }

    private void updatePlayer(float dt) {
        if (player.hp <= 0) {
            player.respawnT -= dt;
            if (player.respawnT <= 0) respawnPlayer();
            return;
        }

        float lx = input.lookDX;
        float ly = input.lookDY;
        input.lookDX = 0f;
        input.lookDY = 0f;
        player.yaw += lx * 0.0035f;
        player.pitch -= ly * 0.0035f;
        player.pitch = Math.max(-1.35f, Math.min(1.35f, player.pitch));

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
        float speed = 3.6f;
        float sin = (float) Math.sin(player.yaw);
        float cos = (float) Math.cos(player.yaw);
        float dx = (cos * mx + sin * mz) * speed * dt;
        float dz = (sin * mx - cos * mz) * speed * dt;

        if (!map.blocked(player.x + dx, player.z, 0.28f)) player.x += dx;
        if (!map.blocked(player.x, player.z + dz, 0.28f)) player.z += dz;

        if (input.jumpPressed) {
            input.jumpPressed = false;
            if (player.y <= 0.001f) player.vy = 5.6f;
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

        if (player.fireCd > 0) player.fireCd -= dt;
        if (player.switchT > 0) player.switchT -= dt;
        if (player.knifeT > 0) player.knifeT -= dt * 2.2f;
        if (player.muzzleT > 0) player.muzzleT -= dt;

        if (player.reloadT > 0) {
            player.reloadT -= dt;
            if (player.reloadT <= 0) finishReload();
        }

        boolean wantFire = input.firePressed || (input.firing && player.current.auto);
        input.firePressed = false;
        if (wantFire) tryFire();
    }

    private void tryFire() {
        if (player.hp <= 0 || player.switchT > 0 || player.reloadT > 0 || player.fireCd > 0) return;
        WeaponType w = player.current;

        if (w == WeaponType.KNIFE) {
            player.fireCd = w.interval;
            player.knifeT = 1f;
            float cp = (float) Math.cos(player.pitch);
            float dx = (float) (Math.sin(player.yaw) * cp);
            float dy = (float) Math.sin(player.pitch);
            float dz = (float) (-Math.cos(player.yaw) * cp);
            for (Bot b : bots) {
                if (!b.alive) continue;
                float tx = b.x - player.x;
                float ty = 1.0f - (player.y + 1.5f);
                float tz = b.z - player.z;
                float d = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (d > w.range) continue;
                float dot = (tx * dx + ty * dy + tz * dz) / Math.max(d, 0.001f);
                if (dot > 0.55f) {
                    b.hp -= w.damage;
                    b.hitFlash = 0.25f;
                    if (b.hp <= 0) killBot(b);
                }
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
        player.pitch = Math.min(1.35f, player.pitch + 0.012f);

        float cp = (float) Math.cos(player.pitch);
        float dx = (float) (Math.sin(player.yaw) * cp);
        float dy = (float) Math.sin(player.pitch);
        float dz = (float) (-Math.cos(player.yaw) * cp);
        float ox = player.x;
        float oy = player.y + 1.55f;
        float oz = player.z;

        Bot best = null;
        float bestT = w.range;
        for (Bot b : bots) {
            if (!b.alive) continue;
            float t = raySphere(ox, oy, oz, dx, dy, dz, b.x, 1.0f, b.z, 0.6f);
            if (t > 0 && t < bestT && map.lineOfSight(ox, oz, b.x, b.z)) {
                bestT = t;
                best = b;
            }
        }
        if (best != null) {
            best.hp -= w.damage;
            best.hitFlash = 0.2f;
            if (best.hp <= 0) killBot(best);
        }
    }

    private float raySphere(float ox, float oy, float oz, float dx, float dy, float dz,
                            float cx, float cy, float cz, float r) {
        float lx = cx - ox, ly = cy - oy, lz = cz - oz;
        float t = lx * dx + ly * dy + lz * dz;
        if (t < 0) return -1f;
        float d2 = lx * lx + ly * ly + lz * lz - t * t;
        if (d2 > r * r) return -1f;
        return t - (float) Math.sqrt(r * r - d2);
    }

    private void killBot(Bot b) {
        b.alive = false;
        b.respawnT = 4f;
        b.path.clear();
        b.pathIndex = 0;
        player.money += 300;
        player.kills++;
        msg("击杀 +$300");
    }

    private void updateBots(float dt) {
        for (Bot b : bots) {
            if (!b.alive) {
                b.respawnT -= dt;
                if (b.respawnT <= 0) respawnBot(b);
                continue;
            }
            if (b.hitFlash > 0) b.hitFlash -= dt;

            float dx = player.x - b.x;
            float dz = player.z - b.z;
            float dist = (float) Math.hypot(dx, dz);
            boolean los = player.hp > 0 && map.lineOfSight(b.x, b.z, player.x, player.z);

            if (los && dist < 22f) {
                b.state = dist < 12f ? 2 : 1;
            } else {
                b.state = 0;
            }

            b.thinkT -= dt;

            if (b.state == 0) {
                if (b.pathIndex >= b.path.size()) {
                    if (b.thinkT <= 0) {
                        float[] goal = map.randomFree();
                        b.path.clear();
                        b.path.addAll(map.findPath(b.x, b.z, goal[0], goal[1]));
                        b.pathIndex = 0;
                        b.thinkT = 2.2f;
                    }
                }
            } else if (b.thinkT <= 0) {
                List<float[]> p = map.findPath(b.x, b.z, player.x, player.z);
                b.path.clear();
                b.path.addAll(p);
                b.pathIndex = 0;
                b.thinkT = 0.7f;
            }

            boolean holdStill = b.state == 2 && dist < 8f;
            if (!holdStill) {
                followPath(b, 2.7f * dt);
            }

            if (los) {
                b.yaw = (float) Math.atan2(dx, -dz);
            }

            if (los && dist < 18f && player.hp > 0) {
                b.shootT -= dt;
                if (b.shootT <= 0) {
                    b.shootT = 0.9f + rnd.nextFloat() * 0.7f;
                    float chance = Math.max(0.15f, 0.62f - dist * 0.02f);
                    if (rnd.nextFloat() < chance) {
                        damagePlayer(7 + rnd.nextInt(11));
                    }
                }
            } else {
                b.shootT = Math.max(b.shootT, 0.35f);
            }
        }
    }

    private void followPath(Bot b, float step) {
        if (b.path.isEmpty() || b.pathIndex >= b.path.size()) return;
        float[] target = b.path.get(b.pathIndex);
        float dx = target[0] - b.x;
        float dz = target[1] - b.z;
        float d = (float) Math.hypot(dx, dz);
        if (d < 0.25f) {
            b.pathIndex++;
            return;
        }
        float nx = dx / d;
        float nz = dz / d;
        float mx = nx * step;
        float mz = nz * step;
        if (!map.blocked(b.x + mx, b.z, 0.3f)) b.x += mx;
        if (!map.blocked(b.x, b.z + mz, 0.3f)) b.z += mz;
        b.yaw = (float) Math.atan2(nx, -nz);
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
            player.respawnT = 3f;
            player.deaths++;
            msg("你被击倒，3 秒后重生");
        }
    }

    private void respawnPlayer() {
        float[] s = SPAWNS[rnd.nextInt(SPAWNS.length)];
        player.x = s[0];
        player.z = s[1];
        player.y = 0f;
        player.vy = 0f;
        player.hp = 100;
        player.armor = 0;
        player.respawnT = 0f;
        msg("已重生");
    }

    private void respawnBot(Bot b) {
        float[] s = SPAWNS[rnd.nextInt(SPAWNS.length)];
        b.x = s[0];
        b.z = s[1];
        b.hp = 100;
        b.alive = true;
        b.hitFlash = 0f;
        b.path.clear();
        b.pathIndex = 0;
        b.shootT = 1f;
    }

    private void cycleWeapon() {
        if (player.owned.size() < 2) return;
        int idx = player.owned.indexOf(player.current);
        if (idx < 0) idx = 0;
        player.current = player.owned.get((idx + 1) % player.owned.size());
        player.switchT = 0.35f;
        player.reloadT = 0f;
        msg("切换：" + player.current.label);
    }

    private void startReload() {
        WeaponType w = player.current;
        if (w == WeaponType.KNIFE || player.switchT > 0) return;
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
            case "ammo": {
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
            }
            case "sell":
                return sellCurrent();
            default:
                return false;
        }
    }

    private boolean buyWeapon(WeaponType w) {
        if (player.owned.contains(w)) {
            msg("已经拥有 " + w.label);
            return false;
        }
        if (player.money < w.price) {
            msg("资金不足");
            return false;
        }
        player.money -= w.price;
        player.owned.add(w);
        player.current = w;
        player.mag[w.ordinal()] = w.magSize;
        player.reserve[w.ordinal()] = w.reserveSize;
        player.switchT = 0.35f;
        player.reloadT = 0f;
        msg("已购买 " + w.label);
        return true;
    }

    private boolean sellCurrent() {
        if (player.current == WeaponType.KNIFE) {
            msg("刀不能出售");
            return false;
        }
        int refund = player.current.price / 2;
        player.money += refund;
        player.owned.remove(player.current);
        player.current = player.owned.isEmpty() ? WeaponType.KNIFE
                : player.owned.get(player.owned.size() - 1);
        player.switchT = 0.35f;
        msg("已出售，获得 $" + refund);
        return true;
    }

    private void msg(String s) {
        toast = s;
        toastT = 1.6f;
    }
}
