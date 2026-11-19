package dev.minemod.test;

import java.util.List;

import dev.minemod.component.ModComponents;
import dev.minemod.entity.ModEntities;
import dev.minemod.entity.ShellbackEntity;
import dev.minemod.entity.ShellbackPhase;
import dev.minemod.item.ModItems;
import dev.minemod.test.support.ParryTestSupport;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scope 8 cross-scope GameTests. Both tests use only the public gameplay paths: real melee damage,
 * the real loot table, the server {@code RecipeManager} and the shared timed-parry recipe from
 * {@link ParryTestSupport}. No test calls {@code onParried} or {@code ParryRules} directly and no
 * stack is fabricated where the game can drop or craft it.
 */
public class IntegrationGameTest {
	private static final int ARENA_RADIUS = 7;
	private static final float LETHAL_DAMAGE = 100.0F;
	private static final float FRONTAL_HIT_DAMAGE = 5.0F;
	private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationGameTest.class);

	/**
	 * Trophy to parry: kill from behind, take the plate the loot table really dropped, craft the
	 * upgrade through the recipe manager, then parry a real zombie hit with the crafted shield.
	 */
	@GameTest(maxTicks = 100)
	public void full_loop(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = helper.spawn(ModEntities.SHELLBACK, new Vec3(4.0, 1.0, 3.0));
		shellback.setYRot(0.0F);
		shellback.setYBodyRot(0.0F);
		shellback.setYHeadRot(0.0F);
		// The plated front deflects melee, so the hunter stands behind the shellback.
		Player hunter = spawnPlayer(helper, new Vec3(4.0, 1.0, 1.4), 0.0F);
		ServerLevel level = helper.getLevel();
		float shellbackHealthBefore = shellback.getHealth();

		boolean hurt = shellback.hurtServer(level, level.damageSources().playerAttack(hunter), LETHAL_DAMAGE);
		helper.assertTrue(hurt, "a player hit from behind must land");
		helper.assertTrue(shellback.getHealth() < shellbackHealthBefore, "the hit must reduce shellback health");
		helper.assertTrue(shellback.isDeadOrDying(), "lethal player damage must kill the shellback");
		helper.assertItemEntityCountIs(ModItems.SHELLBACK_PLATE, new BlockPos(4, 1, 3), 2.0, 1);

		ItemStack plate = droppedPlate(helper, new BlockPos(4, 1, 3));
		helper.assertTrue(!plate.isEmpty(), "the kill must leave a real shellback plate on the ground");
		LOGGER.info("full_loop: dropped plate stack={}", plate);

		ItemStack upgraded = craftUpgrade(level, plate);
		helper.assertTrue(!upgraded.isEmpty(), "a vanilla shield plus the dropped plate must match minemod:plated_shield");
		helper.assertTrue(upgraded.is(Items.SHIELD), "the upgrade must stay minecraft:shield");
		helper.assertValueEqual(upgraded.getCount(), 1, "crafted shield count");
		helper.assertTrue(upgraded.has(ModComponents.PLATED), "the crafted shield must carry minemod:plated");
		helper.assertTrue(Boolean.TRUE.equals(upgraded.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"the crafted shield must force the enchantment glint on");

		Player defender = ParryTestSupport.spawnDefender(helper, upgraded.copy());
		ParryTestSupport.beginBlocking(defender, ParryTestSupport.D);
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, defender);
		float defenderHealthBefore = defender.getHealth();
		boolean blocked = ParryTestSupport.meleeHit(helper, zombie, defender);

		helper.assertFalse(blocked, "the crafted plated shield must block the zombie hit");
		helper.assertValueEqual(defender.getHealth(), defenderHealthBefore, "a parried zombie hit must deal no damage");
		ParryTestSupport.assertStaggered(helper, zombie);
		LOGGER.info("full_loop: kill -> drop -> craft -> parry zombie completed at tick {}", helper.getTick());
		helper.succeed();
	}

	/**
	 * Real lunge, real shield. The mock player raises the plated shield as the lunge starts; the
	 * lunge hit is the vanilla MOB_ATTACK, so the parry is decided by the Scope 4 handler.
	 */
	@GameTest(maxTicks = 160)
	public void parry_shellback_lunge_exposes(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = helper.spawn(ModEntities.SHELLBACK, new Vec3(4.0, 1.0, 1.5));
		shellback.setYRot(0.0F);
		shellback.setYBodyRot(0.0F);
		shellback.setYHeadRot(0.0F);

		Player player = spawnPlayer(helper, new Vec3(4.0, 1.0, 6.5), 180.0F);
		player.setItemInHand(InteractionHand.OFF_HAND, ParryTestSupport.platedShield());
		player.lookAt(EntityAnchorArgument.Anchor.EYES, shellback.getEyePosition());
		shellback.setTarget(player);

		new LungeParryWatcher(helper, shellback, player).tick();
	}

	private static void placeFloor(final GameTestHelper helper) {
		BlockState stone = Blocks.STONE.defaultBlockState();

		for (int x = 0; x <= ARENA_RADIUS; x++)
			for (int z = 0; z <= ARENA_RADIUS; z++)
				helper.setBlock(x, 0, z, stone);
	}

	private static Player spawnPlayer(final GameTestHelper helper, final Vec3 pos, final float yaw) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setNoGravity(true);
		Vec3 absolute = helper.absoluteVec(pos);
		player.setPos(absolute.x, absolute.y, absolute.z);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		return player;
	}

	/** The stack that really dropped, read from the vanilla item entity; never built by the test. */
	private static ItemStack droppedPlate(final GameTestHelper helper, final BlockPos near) {
		List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, near, 2.0);

		for (ItemEntity drop : drops) {
			if (drop.getItem().is(ModItems.SHELLBACK_PLATE)) {
				return drop.getItem();
			}
		}

		return ItemStack.EMPTY;
	}

	private static ItemStack craftUpgrade(final ServerLevel level, final ItemStack plate) {
		CraftingInput input = CraftingInput.of(3, 1, List.of(new ItemStack(Items.SHIELD), plate, ItemStack.EMPTY));
		return level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}

	/**
	 * Raises the shield on the first LUNGE sample and advances the mock player by hand (Q12): the
	 * starting ticks plus one tick per later world tick put the raised shield inside
	 * {@code [D, D + 6]} when the lunge hit resolves.
	 */
	private static final class LungeParryWatcher {
		/** Ticks applied at the moment the lunge starts; the remaining window ticks follow per sample. */
		private static final int HOLD_TICKS = 3;
		private final GameTestHelper helper;
		private final ShellbackEntity shellback;
		private final Player player;
		private int telegraphSamples;
		private int lungeSamples;
		private boolean blocking;
		private float healthBefore;

		LungeParryWatcher(final GameTestHelper helper, final ShellbackEntity shellback, final Player player) {
			this.helper = helper;
			this.shellback = shellback;
			this.player = player;
		}

		void tick() {
			ShellbackPhase phase = this.shellback.getPhase();

			if (phase == ShellbackPhase.TELEGRAPH) {
				this.telegraphSamples++;
			}

			if (phase == ShellbackPhase.LUNGE) {
				this.lungeSamples++;

				if (!this.blocking) {
					this.healthBefore = this.player.getHealth();
					ParryTestSupport.beginBlocking(this.player, HOLD_TICKS);
					this.blocking = true;
				} else {
					// Every later world tick advances the use action exactly once, in lock step with
					// the lunge, so the hit lands inside the parry window.
					this.player.tick();
				}
			}

			if (phase == ShellbackPhase.EXPOSED) {
				assertParried();
				return;
			}

			this.helper.runAfterDelay(1, this::tick);
		}

		private void assertParried() {
			int ticksUsing = this.player.getTicksUsingItem();
			this.helper.assertTrue(this.blocking, "the player must have raised the shield during the lunge");
			this.helper.assertTrue(this.telegraphSamples >= ShellbackEntity.TELEGRAPH_TICKS,
					"the wind-up must last at least 16 ticks, observed " + this.telegraphSamples);
			this.helper.assertTrue(this.lungeSamples >= 4,
					"the lunge must really travel before the hit, lunge samples " + this.lungeSamples);
			this.helper.assertTrue(ticksUsing >= ParryTestSupport.D && ticksUsing <= ParryTestSupport.D + 6,
					"the block must be inside the parry window at the hit, ticksUsing " + ticksUsing);
			this.helper.assertValueEqual(this.player.getHealth(), this.healthBefore,
					"a parried lunge must deal no damage");
			this.helper.assertTrue(this.player.getUseItem().getDamageValue() > 0,
					"the shield must absorb the lunge and spend durability");
			this.helper.assertTrue(this.shellback.getPhase() == ShellbackPhase.EXPOSED,
					"the parried shellback must be exposed");
			ParryTestSupport.assertStaggered(this.helper, this.shellback);

			float before = this.shellback.getHealth();
			boolean hurt = this.shellback.hurtServer(this.helper.getLevel(),
					this.helper.getLevel().damageSources().playerAttack(this.player), FRONTAL_HIT_DAMAGE);
			this.helper.assertTrue(hurt, "an exposed shellback must take the frontal melee hit");
			this.helper.assertTrue(this.shellback.getHealth() < before,
					"the frontal hit must reduce exposed shellback health");
			LOGGER.info("parry_shellback_lunge_exposes: parried at tick {} after {} lunge samples, ticksUsing {}, health {}",
					this.helper.getTick(), this.lungeSamples, ticksUsing, this.player.getHealth());
			this.helper.succeed();
		}
	}
}
