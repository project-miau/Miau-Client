package idle.miau.utility;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.Map;

public final class ExplosionDamage {
    private static final DamageSource SOURCE = DamageSource.setExplosionSource(null);

    private ExplosionDamage() {
    }

    public static float estimate(World world, Vec3 center, float power, EntityPlayer player) {
        return estimate(world, center, power, player, true);
    }

    public static float estimate(EntityTNTPrimed tnt, float power, EntityPlayer player) {
        Vec3 center = new Vec3(tnt.posX, tnt.posY + tnt.height / 16.0F, tnt.posZ);
        return estimate(tnt.worldObj, center, power, player, !(tnt.getTntPlacedBy() instanceof EntityPlayer));
    }

    private static float estimate(World world, Vec3 center, float power, EntityPlayer player, boolean difficultyScaled) {
        if (world == null || player == null || power <= 0.0F || player.capabilities.disableDamage
                || player.isSpectator() || player.isImmuneToExplosions() || player.isEntityInvulnerable(SOURCE)) {
            return 0.0F;
        }

        double distance = player.getDistance(center.xCoord, center.yCoord, center.zCoord) / (power * 2.0D);
        if (distance > 1.0D) {
            return 0.0F;
        }
        double exposure = world.getBlockDensity(center, player.getEntityBoundingBox());
        double impact = (1.0D - distance) * exposure;
        float damage = (int) ((impact * impact + impact) * 0.5D * 8.0D * (power * 2.0D) + 1.0D);

        if (difficultyScaled) {
            switch (world.getDifficulty()) {
                case PEACEFUL:
                    return 0.0F;
                case EASY:
                    damage = damage * 0.5F + 1.0F;
                    break;
                case HARD:
                    damage *= 1.5F;
                    break;
                default:
                    break;
            }
        }

        if (player.isBlocking()) {
            damage = (damage + 1.0F) * 0.5F;
        }
        damage *= (25.0F - player.getTotalArmorValue()) / 25.0F;
        if (player.isPotionActive(Potion.resistance)) {
            int level = player.getActivePotionEffect(Potion.resistance).getAmplifier() + 1;
            damage *= Math.max(0.0F, 1.0F - level * 0.2F);
        }
        damage *= 1.0F - averageProtection(player.inventory.armorInventory) / 25.0F;
        return Math.max(0.0F, damage - player.getAbsorptionAmount());
    }

    private static float averageProtection(ItemStack[] armor) {
        int modifier = 0;
        for (ItemStack stack : armor) {
            if (stack == null) {
                continue;
            }
            for (Map.Entry<Integer, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                Enchantment enchantment = Enchantment.getEnchantmentById(entry.getKey());
                if (enchantment != null) {
                    modifier += enchantment.calcModifierDamage(entry.getValue(), SOURCE);
                }
            }
        }
        modifier = Math.max(0, Math.min(25, modifier));
        int base = (modifier + 1) / 2;
        int rolls = modifier / 2 + 1;
        float total = 0.0F;
        for (int roll = 0; roll < rolls; roll++) {
            total += Math.min(20, base + roll);
        }
        return total / rolls;
    }
}
