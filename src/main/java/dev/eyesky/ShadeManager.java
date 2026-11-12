package dev.eyesky;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Форма "тень": игрок для остальных превращается в чёрную сущность из частиц ростом 3 блока с белыми глазами.
 * Настоящая модель игрока скрывается (hideEntity), вместо неё рисуются частицы. Сам игрок свою тень не видит.
 */
public final class ShadeManager {

    private final EyeSkyPlugin plugin;
    private final Set<UUID> shades = new HashSet<>();
    private final Random rnd = new Random();
    private BukkitTask task;
    private long tick;

    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(4, 4, 8), 1.7f);
    private static final Particle.DustOptions DARK = new Particle.DustOptions(Color.fromRGB(18, 14, 26), 1.3f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.9f);

    public ShadeManager(EyeSkyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new HashSet<>(shades)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) show(p);
        }
        shades.clear();
    }

    public boolean isShade(Player p) {
        return p != null && shades.contains(p.getUniqueId());
    }

    public int count() {
        return shades.size();
    }

    /** Включает/выключает форму. Возвращает true, если теперь включена. */
    public boolean toggle(Player p) {
        if (isShade(p)) {
            disable(p);
            return false;
        }
        enable(p);
        return true;
    }

    public void enable(Player p) {
        shades.add(p.getUniqueId());
        hideFromOthers(p);
        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 40, 0.5, 0.9, 0.5, 0.05);
        p.getWorld().playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    public void disable(Player p) {
        if (!shades.remove(p.getUniqueId())) return;
        show(p);
        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 40, 0.5, 0.9, 0.5, 0.05);
        p.getWorld().playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    /** Глаз изгнал тень: форма снимается, игрока отбрасывает и слепит. */
    public void dispel(Player p) {
        if (!shades.remove(p.getUniqueId())) return;
        show(p);
        Location l = p.getLocation().add(0, 1.2, 0);
        p.getWorld().spawnParticle(Particle.END_ROD, l, 80, 0.6, 1.0, 0.6, 0.25);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l, 50, 0.5, 0.9, 0.5, 0.1);
        p.getWorld().spawnParticle(Particle.EXPLOSION, l, 1, 0, 0, 0, 0);
        p.getWorld().playSound(l, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.AMBIENT, 2.0f, 1.0f);
        p.getWorld().playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 2.0f, 0.8f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
        p.setVelocity(p.getVelocity().add(new Vector(0, 0.9, 0)));
        p.sendMessage(Component.text("Глаз изгнал тебя из тени!", NamedTextColor.GOLD));
    }

    /** Новому игроку на сервере нужно скрыть всех, кто сейчас в тени. */
    public void onJoin(Player joiner) {
        for (UUID id : shades) {
            Player s = Bukkit.getPlayer(id);
            if (s != null && !s.equals(joiner)) joiner.hideEntity(plugin, s);
        }
    }

    // =====================================================================================

    private void hideFromOthers(Player s) {
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (!v.equals(s)) v.hideEntity(plugin, s);
        }
    }

    private void show(Player s) {
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (!v.equals(s)) v.showEntity(plugin, s);
        }
    }

    private void tick() {
        try {
            tick++;
            if (shades.isEmpty()) return;
            for (UUID id : new HashSet<>(shades)) {
                Player s = Bukkit.getPlayer(id);
                if (s == null || !s.isOnline()) { shades.remove(id); continue; }
                if (tick % 10 == 0) hideFromOthers(s);          // на случай смены мира/респавна
                if (s.isDead()) continue;
                render(s);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ошибка в форме тени", t);
        }
    }

    /** Рисует фигуру 3 блока высотой для всех, кроме самого игрока. */
    private void render(Player s) {
        Location base = s.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // куда смотрит
        double rx = Math.cos(yaw), rz = Math.sin(yaw);        // вправо
        double sway = Math.sin(tick * 0.15) * 0.06;

        Set<Player> viewers = new HashSet<>();
        for (Player v : base.getWorld().getPlayers()) {
            if (v.equals(s)) continue;
            Location vl = v.getLocation();
            double dx = vl.getX() - base.getX(), dz = vl.getZ() - base.getZ();
            if (dx * dx + dz * dz < 40 * 40) viewers.add(v);
        }
        if (viewers.isEmpty()) return;

        // голова
        for (int i = 0; i < 12; i++) {
            Vector u = unit();
            put(viewers, base, rx, rz, fx, fz, u.getX() * 0.36, 2.65 + u.getY() * 0.38, u.getZ() * 0.36, BLACK);
        }
        // туловище
        for (int i = 0; i < 16; i++) {
            Vector u = unit();
            put(viewers, base, rx, rz, fx, fz, u.getX() * 0.42, 1.75 + u.getY() * 0.55, u.getZ() * 0.24, BLACK);
        }
        // руки
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) {
                double k = i / 5.0;
                put(viewers, base, rx, rz, fx, fz, side * (0.5 + 0.12 * k) + sway * side, 2.15 - 1.1 * k, 0.0, DARK);
            }
        }
        // ноги
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) {
                double k = i / 5.0;
                put(viewers, base, rx, rz, fx, fz, side * 0.18, 1.2 * (1 - k), 0.0, DARK);
            }
        }
        // клочья дыма у земли и вокруг
        for (int i = 0; i < 4; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 0.3 + rnd.nextDouble() * 0.4;
            double x = base.getX() + Math.cos(a) * d, y = base.getY() + rnd.nextDouble() * 2.8, z = base.getZ() + Math.sin(a) * d;
            for (Player v : viewers) v.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 0, 0, 0.02, 0, 1.0);
        }
        // белые глаза-точки
        for (int side = -1; side <= 1; side += 2) {
            put(viewers, base, rx, rz, fx, fz, side * 0.15, 2.72, 0.36, WHITE);
        }
    }

    private void put(Set<Player> viewers, Location base, double rx, double rz, double fx, double fz,
                     double x, double y, double z, Particle.DustOptions dust) {
        double wx = base.getX() + rx * x + fx * z;
        double wy = base.getY() + y;
        double wz = base.getZ() + rz * x + fz * z;
        for (Player v : viewers) v.spawnParticle(Particle.DUST, wx, wy, wz, 1, 0, 0, 0, 0, dust);
    }

    private Vector unit() {
        Vector v;
        do {
            v = new Vector(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
        } while (v.lengthSquared() < 1e-6);
        return v.normalize();
    }
}
