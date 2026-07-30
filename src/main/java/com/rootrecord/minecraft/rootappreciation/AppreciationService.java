package com.rootrecord.minecraft.rootappreciation;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AppreciationService {

    private final RootAppreciationPlugin plugin;

    public AppreciationService(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    public void giveTokens(Player target, int amount, String reason, boolean notifyTarget) {
        if (amount <= 0) {
            return;
        }
        String batch = UUID.randomUUID().toString();
        // Keep ledger batches unique, but make the in-item PDC issue-id stable so multiple purchase/issue batches merge into fewer stacks.
        String stackIssueId = "rootmc-appreciation";
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            ItemStack stack = plugin.tokens().create(plugin.config(), n, stackIssueId);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
                target.sendMessage(plugin.msg("inventory-full"));
            }
            left -= n;
        }
        plugin.store().recordIssue(target.getUniqueId(), target.getName(), amount, reason, batch);
        if (notifyTarget) {
            target.sendMessage(plugin.msg("give-received").replace("{amount}", String.valueOf(amount)));
        }
    }

    public boolean redeemTokens(Player player, int amount) {
        if (amount <= 0) {
            player.sendMessage(plugin.msg("invalid-amount"));
            return false;
        }
        // Redeem destinations not live yet — still remove + ledger so supply tracking works when enabled.
        if (!plugin.yaml().config().getBoolean("redeem.enabled", false)) {
            player.sendMessage(plugin.msg("redeem-none"));
            return false;
        }
        int have = plugin.tokens().countInInventory(player);
        if (have < amount) {
            player.sendMessage(plugin.msg("redeem-need").replace("{amount}", String.valueOf(amount)));
            return false;
        }
        int removed = plugin.tokens().removeFromInventory(player, amount);
        if (removed < amount) {
            player.sendMessage(plugin.msg("redeem-need").replace("{amount}", String.valueOf(amount)));
            return false;
        }
        plugin.store().recordRedeem(player.getUniqueId(), player.getName(), removed, "manual");
        player.sendMessage(plugin.msg("redeem-success").replace("{amount}", String.valueOf(removed)));
        return true;
    }

    public void claimBonus(Player player) {
        AppreciationConfig cfg = plugin.config();
        if (!cfg.bonusEnabled()) {
            player.sendMessage(plugin.msg("bonus-disabled"));
            return;
        }
        long now = System.currentTimeMillis();
        var streakOpt = plugin.store().playerStreak(player.getUniqueId());
        int nextDay = 1;
        if (streakOpt.isPresent()) {
            var streak = streakOpt.get();
            long last = streak.lastBonusAtMs();
            if (last > 0) {
                long since = now - last;
                if (since < cfg.cooldownMs()) {
                    long remain = cfg.cooldownMs() - since;
                    double hours = remain / 3_600_000.0;
                    player.sendMessage(plugin.msg("bonus-too-soon")
                            .replace("{hours}", String.format(Locale.US, "%.1f", hours)));
                    return;
                }
                if (since <= cfg.missAfterMs()) {
                    nextDay = Math.min(cfg.maxStreakDay(), streak.streakDay() + 1);
                    if (streak.streakDay() <= 0) {
                        nextDay = 1;
                    }
                } else {
                    nextDay = 1;
                }
            }
        }

        AppreciationConfig.DayReward reward = cfg.rewardForDay(nextDay);
        if (!plugin.store().updateStreak(player.getUniqueId(), player.getName(), nextDay, now)) {
            player.sendMessage(plugin.msg("bonus-db"));
            return;
        }
        giveTokens(player, reward.tokens(), "bonus-day-" + nextDay, false);
        if (reward.gold() > 0) {
            RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
            if (eco != null) {
                try {
                    eco.deposit(player.getUniqueId(), reward.gold());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Bonus gold deposit failed: " + ex.getMessage());
                }
            }
        }
        player.sendMessage(plugin.msg("bonus-success")
                .replace("{day}", String.valueOf(nextDay))
                .replace("{tokens}", String.valueOf(reward.tokens()))
                .replace("{gold}", GoldMoney.format(reward.gold())));
    }

    public boolean bonusAvailable(UUID uuid) {
        AppreciationConfig cfg = plugin.config();
        if (!cfg.bonusEnabled()) {
            return false;
        }
        var streak = plugin.store().playerStreak(uuid);
        if (streak.isEmpty() || streak.get().lastBonusAtMs() <= 0) {
            return true;
        }
        return System.currentTimeMillis() - streak.get().lastBonusAtMs() >= cfg.cooldownMs();
    }

    public String nextBonusHint(UUID uuid) {
        AppreciationConfig cfg = plugin.config();
        var streak = plugin.store().playerStreak(uuid);
        int nextDay = 1;
        if (streak.isPresent() && streak.get().lastBonusAtMs() > 0) {
            long since = System.currentTimeMillis() - streak.get().lastBonusAtMs();
            if (since < cfg.cooldownMs()) {
                double hours = (cfg.cooldownMs() - since) / 3_600_000.0;
                return String.format(Locale.US, "in %.1fh", hours);
            }
            if (since <= cfg.missAfterMs()) {
                nextDay = Math.min(cfg.maxStreakDay(), Math.max(1, streak.get().streakDay()) + 1);
            }
        }
        AppreciationConfig.DayReward r = cfg.rewardForDay(nextDay);
        return "Day " + nextDay + " (" + r.tokens() + " tokens + " + GoldMoney.format(r.gold()) + " G)";
    }
}
