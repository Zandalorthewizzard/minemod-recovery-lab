package dev.minemod.test;

import java.util.List;

import dev.minemod.component.ModComponents;
import dev.minemod.item.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * Scope 3 acceptance tests. Every lookup goes through the real server
 * {@code RecipeManager}, so both the data recipe and the custom ingredient are exercised.
 */
public class ShieldUpgradeGameTest {
	private static final Identifier VANILLA_SHIELD_MODEL = Identifier.withDefaultNamespace("shield");

	private static CraftingInput input(ItemStack first, ItemStack second) {
		return CraftingInput.of(3, 1, List.of(first, second, ItemStack.EMPTY));
	}

	private static ItemStack craft(ServerLevel level, ItemStack first, ItemStack second) {
		CraftingInput input = input(first, second);
		return level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}

	private static boolean matches(ServerLevel level, ItemStack first, ItemStack second) {
		CraftingInput input = input(first, second);
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).isPresent();
	}

	private static ItemStack decoratedShield(ServerLevel level) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.setDamageValue(10);

		Holder<Enchantment> unbreaking = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.UNBREAKING);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(unbreaking, 2);
		shield.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());

		shield.set(DataComponents.CUSTOM_NAME, Component.literal("Test"));
		shield.set(DataComponents.BASE_COLOR, DyeColor.RED);

		Holder<BannerPattern> pattern = level.registryAccess()
				.lookupOrThrow(Registries.BANNER_PATTERN)
				.getOrThrow(BannerPatterns.STRIPE_TOP);
		shield.set(DataComponents.BANNER_PATTERNS,
				new BannerPatternLayers(List.of(new BannerPatternLayers.Layer(pattern, DyeColor.WHITE))));
		return shield;
	}

	private static ItemStack plate() {
		return new ItemStack(ModItems.SHELLBACK_PLATE);
	}

	@GameTest
	public void upgrade_preserves_components(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack shield = decoratedShield(level);
		ItemStack result = craft(level, shield, plate());

		helper.assertTrue(!result.isEmpty(), "a shield plus a plate must match minemod:plated_shield");
		helper.assertTrue(result.is(Items.SHIELD), "the upgrade must stay minecraft:shield");
		helper.assertValueEqual(result.getCount(), 1, "result count");
		helper.assertTrue(result.has(ModComponents.PLATED), "the result must carry minemod:plated");
		helper.assertValueEqual(result.get(DataComponents.ITEM_MODEL), shield.get(DataComponents.ITEM_MODEL),
				"the item model must be preserved from the input shield");
		helper.assertValueEqual(result.getDamageValue(), 10, "damage must be preserved");
		helper.assertValueEqual(result.get(DataComponents.ENCHANTMENTS), shield.get(DataComponents.ENCHANTMENTS),
				"enchantments must be preserved");
		helper.assertValueEqual(result.get(DataComponents.CUSTOM_NAME), shield.get(DataComponents.CUSTOM_NAME),
				"custom name must be preserved");
		helper.assertValueEqual(result.get(DataComponents.BASE_COLOR), shield.get(DataComponents.BASE_COLOR),
				"base color must be preserved");
		helper.assertValueEqual(result.get(DataComponents.BANNER_PATTERNS), shield.get(DataComponents.BANNER_PATTERNS),
				"banner patterns must be preserved");
		helper.assertValueEqual(result.get(DataComponents.BLOCKS_ATTACKS),
				new ItemStack(Items.SHIELD).get(DataComponents.BLOCKS_ATTACKS), "blocks_attacks must stay vanilla");
		helper.assertTrue(Boolean.TRUE.equals(result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"the upgrade must force the enchantment glint on");

		// The result must be exactly the input stack plus the two patched components, nothing else.
		ItemStack expected = shield.copy();
		expected.set(ModComponents.PLATED, Unit.INSTANCE);
		expected.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		helper.assertValueEqual(result.getComponents(), expected.getComponents(),
				"the upgraded stack must equal the input copy plus minemod:plated and the glint override");

		// An upgrade of a plain shield keeps the vanilla model and still carries the forced glint.
		ItemStack plainUpgrade = craft(level, new ItemStack(Items.SHIELD), plate());
		helper.assertTrue(plainUpgrade.has(ModComponents.PLATED), "a plain shield must upgrade too");
		helper.assertValueEqual(plainUpgrade.get(DataComponents.ITEM_MODEL), VANILLA_SHIELD_MODEL,
				"a plain upgrade must keep the vanilla shield model");
		helper.assertTrue(Boolean.TRUE.equals(plainUpgrade.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"a plain upgrade must force the enchantment glint on");
		helper.succeed();
	}

	@GameTest
	public void no_plate_no_match(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		helper.assertTrue(!matches(level, new ItemStack(Items.SHIELD), ItemStack.EMPTY),
				"a lone shield must not match");
		helper.assertTrue(!matches(level, new ItemStack(Items.SHIELD), new ItemStack(Items.STICK)),
				"a shield with another item must not match");
		helper.assertTrue(!matches(level, plate(), ItemStack.EMPTY), "a lone plate must not match");
		helper.assertTrue(!matches(level, ItemStack.EMPTY, ItemStack.EMPTY), "an empty grid must not match");
		helper.assertTrue(craft(level, new ItemStack(Items.SHIELD), ItemStack.EMPTY).isEmpty(),
				"a lone shield must not be crafted into anything");

		// The regular vanilla shield stays untouched: not plated, vanilla model, no forced glint.
		ItemStack plain = new ItemStack(Items.SHIELD);
		helper.assertTrue(!plain.has(ModComponents.PLATED), "a regular shield must not be plated");
		helper.assertValueEqual(plain.get(DataComponents.ITEM_MODEL), VANILLA_SHIELD_MODEL,
				"a regular shield keeps its vanilla model");
		helper.assertTrue(plain.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null,
				"a regular shield must not force an enchantment glint override");
		helper.succeed();
	}

	@GameTest
	public void already_plated(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// The negated component in the ingredient rejects any plated shield outright, while the
		// vanilla no-op check independently rejects a result identical to its input.
		ItemStack platedWithoutModel = new ItemStack(Items.SHIELD);
		platedWithoutModel.set(ModComponents.PLATED, Unit.INSTANCE);
		helper.assertTrue(!matches(level, platedWithoutModel, plate()),
				"a plated shield without an item model must not accept a second plate");

		ItemStack platedWithDamage = platedWithoutModel.copy();
		platedWithDamage.setDamageValue(5);
		helper.assertTrue(!matches(level, platedWithDamage, plate()),
				"a plated shield with damage must not accept a second plate");

		ItemStack fullyUpgraded = platedWithoutModel.copy();
		fullyUpgraded.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		helper.assertTrue(!matches(level, fullyUpgraded, plate()),
				"a fully upgraded shield must not accept a second plate");

		ItemStack upgraded = craft(level, decoratedShield(level), plate());
		helper.assertTrue(!upgraded.isEmpty(), "the first upgrade must succeed");
		helper.assertTrue(!matches(level, upgraded, plate()),
				"an upgraded shield must not accept a second plate");
		helper.assertTrue(craft(level, upgraded, plate()).isEmpty(),
				"an upgraded shield must not be crafted again");
		helper.succeed();
	}
}
