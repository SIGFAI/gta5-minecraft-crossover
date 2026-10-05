package sigf.xo.mod;

import dev.rehan.passthrough.MobWar;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import sigf.xo.Xo;

/**
 * Minecraft Crossover, Minecraft side.
 * - A Minecraft town square rises in front of the player: a giant creeper-face wall, an oak house, a pig pen.
 * - Zombies and creepers hunt GTA's pedestrians (rehan's MobWar); a creeper blast is a real GTA explosion.
 * - GTA -> Minecraft: a dead GTA person rises as a zombie; a destroyed GTA car sets off Minecraft TNT.
 * - Quest "Emerald Rush": five emerald blocks (GTA money bags); the last one calls a mob wave and the LSPD.
 */
public final class XoMod implements ModInitializer {
	private static final int EMERALDS = 5;
	private static final List<BlockPos> emeralds = new ArrayList<>();
	private static final List<BlockPos> built = new ArrayList<>();
	private static int collected;
	private static int rushes;
	private static long lastWreck;
	private static boolean townBuilt;

	@Override
	public void onInitialize() {
		Xo.onLink(() -> {
			Xo.text("~g~Minecraft~s~ has landed in ~y~Los Santos", 5);
			Xo.after(1, () -> Xo.gta("relevel"));
			Xo.after(4, () -> until(XoMod::buildTown, 40));
			Xo.after(5, () -> until(XoMod::newRound, 40));
			Xo.after(4, () -> MobWar.spawn("zombie", 3, 14, 22, 70, 0, null));
		});

		// GTA's dead rise as zombies
		Xo.on("death", m -> {
			var p = m.getAsJsonArray("mc");
			Vec3 at = new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble() + 0.2, p.get(2).getAsDouble());
			if (Xo.near(at, 40, e -> e.getType() == EntityTypes.ZOMBIE).size() > 8) {
				return;
			}

			Xo.spawn(EntityTypes.ZOMBIE, at);
			Xo.particles(ParticleTypes.SOUL, at.add(0, 1, 0), 30, 0.6);
			Xo.sound(SoundEvents.ZOMBIE_AMBIENT, at, 1f, 0.7f);
			Xo.log("a GTA death rose as a zombie");
		});

		// a destroyed GTA car: Minecraft answers with TNT
		Xo.on("wreck", m -> {
			long now = System.currentTimeMillis();
			if (now - lastWreck < 5000) {
				return;
			}

			lastWreck = now;
			var p = m.getAsJsonArray("mc");
			Vec3 at = new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble() + 1.5, p.get(2).getAsDouble());
			Xo.command(String.format(java.util.Locale.ROOT, "summon tnt %.2f %.2f %.2f {fuse:25}", at.x, at.y, at.z));
			Xo.particles(ParticleTypes.LAVA, at, 25, 0.8);
			Xo.sound(SoundEvents.TNT_PRIMED, at, 1.2f, 1f);
			Xo.text("~r~Wrecked!~s~ Minecraft TNT answers", 2.5);
			Xo.log("a GTA wreck lit Minecraft TNT");
		});

		Xo.every(0.25, XoMod::checkPickup);

		// Demo (75 s recording)
		Xo.demo(0, () -> {
			Xo.gta("time", "h", 12, "m", 0);
			Xo.gta("weather", "w", "EXTRASUNNY");
		});
		Xo.demo(1, () -> Xo.gta("relevel"));
		Xo.demo(4, () -> Xo.gta("relevel"));
		Xo.demo(6, () -> until(XoMod::buildTown, 40));
		Xo.demo(8, () -> {
			for (int i = 0; i < 5; i++) {
				Xo.gta("xo.ped", "id", "walker" + i, "model", "a_m_y_hipster_01", "ahead", 10 + i * 2, "task", "wander");
			}
		});
		Xo.demo(6.5, () -> until(XoMod::newRound, 40));
		Xo.demo(8, () -> {
			MobWar.spawn("zombie", 4, 6, 12, 80, 0, null);
			MobWar.spawn("creeper", 2, 8, 14, 80, 0, null);
		});
		Xo.demo(16, () -> MobWar.spawn("zombie", 3, 6, 12, 180, 0, null));
		Xo.demo(26, () -> {
			// a GTA car blown up by Minecraft: the wreck lights TNT right back
			ServerPlayer p = Xo.player();
			if (p != null) {
				Vec3 at = p.position().add(p.getLookAngle().multiply(9, 0, 9));
				Xo.car("target", "adder", at, 90f, 0);
				Xo.after(3, () -> Xo.boom(at.add(0, 0.5, 0), 4, 1.0));
			}
		});
		Xo.demo(40, () -> walkTo(0));
		Xo.demo(48, () -> walkTo(1));
		Xo.demo(56, () -> walkTo(2));
	}

	private static void put(final ServerLevel level, final BlockPos pos, final Block block) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
		built.add(pos);
	}

	/** The town square: creeper-face wall straight ahead, an oak house on the right, a pig pen on the left. */
	private static void until(final java.util.function.BooleanSupplier fn, final int tries) {
		if (fn.getAsBoolean() || tries <= 0) {
			return;
		}

		Xo.after(1, () -> until(fn, tries - 1));
	}

	private static boolean buildTown() {
		final Vec3 forceForward = null;
		ServerPlayer p = Xo.player();
		if (p == null) {
			return false;
		}

		ServerLevel level = Xo.level();
		clearTown(level);
		Vec3 look = forceForward != null ? forceForward : p.getLookAngle();
		int fx = 0;
		int fz = 0;
		if (Math.abs(look.x) > Math.abs(look.z)) {
			fx = look.x > 0 ? 1 : -1;
		} else {
			fz = look.z > 0 ? 1 : -1;
		}

		int rx = -fz;
		int rz = fx;
		BlockPos base = BlockPos.containing(p.getX(), p.getY(), p.getZ());

		// 1) the creeper wall, 11 wide x 11 tall, 19 blocks ahead
		String[] face = {
			"GGGGGGGGGGG", "GGGGGGGGGGG", "GKKGGGGGKKG", "GKKGGGGGKKG", "GGGGKKKGGGG", "GGGKKKKKGGG",
			"GGGKKKKKGGG", "GGGKKGKKGGG", "GGGGGGGGGGG", "GGGGGGGGGGG", "GGGGGGGGGGG"
		};
		BlockPos wallBase = groundAbove(level, base.offset(fx * 19, 0, fz * 19));
		Xo.log("town: player " + base + " forward " + fx + "," + fz + " wall " + wallBase + " ground below player " + level.getBlockState(base.below()).getBlock());
		if (wallBase != null) {
			for (int row = 0; row < face.length; row++) {
				for (int col = 0; col < 11; col++) {
					char c = face[face.length - 1 - row].charAt(col);
					Block b = c == 'K' ? Blocks.COAL_BLOCK : ((row + col) % 7 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.MOSS_BLOCK);
					put(level, wallBase.offset(rx * (col - 5), row, rz * (col - 5)), b);
				}
			}
		}

		// 2) the house, 6 blocks to the right, 8 ahead
		BlockPos hb = groundAbove(level, base.offset(fx * 8 + rx * 11, 0, fz * 8 + rz * 11));
		if (hb != null) {
			for (int a = 0; a < 6; a++) {
				for (int b = 0; b < 6; b++) {
					boolean edge = a == 0 || b == 0 || a == 5 || b == 5;
					BlockPos floor = hb.offset(rx * a + fx * b, -1 + 0, rz * a + fz * b);
					put(level, floor, Blocks.COBBLESTONE);
					for (int h = 0; h < 4; h++) {
						if (!edge) {
							continue;
						}

						boolean corner = (a == 0 || a == 5) && (b == 0 || b == 5);
						boolean door = a == 2 && b == 0 && h < 2;
						boolean window = h == 1 && !corner && (a == 5 || b == 5 || (b == 0 && a == 4));
						if (door) {
							continue;
						}

						put(level, floor.above(h + 1), corner ? Blocks.OAK_LOG : window ? Blocks.GLASS : Blocks.OAK_PLANKS);
					}
				}
			}

			for (int a = -1; a <= 6; a++) {
				for (int b = -1; b <= 6; b++) {
					put(level, hb.offset(rx * a + fx * b, 4, rz * a + fz * b), Blocks.SPRUCE_PLANKS);
				}
			}
		}

		// 3) a pig pen of fences on the left with animals inside
		BlockPos pb = groundAbove(level, base.offset(fx * 7 - rx * 10, 0, fz * 7 - rz * 10));
		if (pb != null) {
			for (int a = 0; a < 6; a++) {
				for (int b = 0; b < 6; b++) {
					if (a == 0 || b == 0 || a == 5 || b == 5) {
						put(level, pb.offset(rx * a + fx * b, 0, rz * a + fz * b), Blocks.OAK_FENCE);
					}
				}
			}

			for (int i = 0; i < 3; i++) {
				Xo.spawn(EntityTypes.PIG, Vec3.atBottomCenterOf(pb.offset(rx * 2 + fx * (1 + i), 0, rz * 2 + fz * (1 + i))));
			}

			Xo.spawn(EntityTypes.CHICKEN, Vec3.atBottomCenterOf(pb.offset(rx * 3 + fx * 3, 0, rz * 3 + fz * 3)));
			Xo.spawn(EntityTypes.COW, Vec3.atBottomCenterOf(pb.offset(rx * 1 + fx * 4, 0, rz * 1 + fz * 4)));
		}

		if (wallBase == null) {
			return false;
		}

		townBuilt = true;
		Xo.particles(ParticleTypes.CLOUD, Vec3.atBottomCenterOf(base.offset(fx * 8, 1, fz * 8)), 40, 3.0);
		Xo.sound(SoundEvents.ANVIL_PLACE, Vec3.atBottomCenterOf(base), 1f, 0.8f);
		return true;
	}

	private static void clearTown(final ServerLevel level) {
		for (BlockPos pos : built) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}

		built.clear();
	}

	/** Emerald blocks around the player, each a GTA money bag. */
	private static boolean newRound() {
		ServerPlayer p = Xo.player();
		if (p == null) {
			return false;
		}

		ServerLevel level = Xo.level();
		clearRound(level);
		collected = 0;
		var random = level.getRandom();
		for (int i = 0; i < EMERALDS; i++) {
			double a = Math.PI * 2 * i / EMERALDS + random.nextDouble() * 0.5;
			double d = 5 + random.nextDouble() * 4;
			BlockPos column = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
			BlockPos pos = groundAbove(level, column);
			if (pos == null) {
				continue;
			}

			level.setBlockAndUpdate(pos, Blocks.EMERALD_BLOCK.defaultBlockState());
			Xo.mirrorBlock(pos.above(), "prop_money_bag_01");
			emeralds.add(pos);
		}

		Xo.text("Emerald Rush: ~g~0/" + emeralds.size(), 3);
		return !emeralds.isEmpty();
	}

	private static BlockPos groundAbove(final ServerLevel level, final BlockPos column) {
		for (int dy = 4; dy >= -8; dy--) {
			BlockPos below = column.offset(0, dy - 1, 0);
			if (!level.getBlockState(below).isAir() && level.getBlockState(below.above()).isAir()) {
				return below.above();
			}
		}

		return null;
	}

	private static void clearRound(final ServerLevel level) {
		for (BlockPos pos : emeralds) {
			if (level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}

			Xo.unmirrorBlock(pos.above());
		}

		emeralds.clear();
	}

	private static void checkPickup() {
		ServerPlayer p = Xo.player();
		if (p == null || emeralds.isEmpty()) {
			return;
		}

		ServerLevel level = Xo.level();
		for (int i = 0; i < emeralds.size(); i++) {
			BlockPos pos = emeralds.get(i);
			Vec3 c = Vec3.atBottomCenterOf(pos.above());
			if (p.position().distanceTo(c) > 1.8) {
				continue;
			}

			emeralds.remove(i);
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			Xo.unmirrorBlock(pos.above());
			Xo.gtaSound("PICK_UP", "HUD_FRONTEND_DEFAULT_SOUNDSET");
			Xo.ptfx("scr_rcbarry2", "scr_clown_appears", c, 1.0);
			Xo.particles(ParticleTypes.HAPPY_VILLAGER, c.add(0, 0.5, 0), 25, 0.5);
			collected++;
			Xo.text("Emerald Rush: ~g~" + collected + "/" + (collected + emeralds.size()), 3);
			if (emeralds.isEmpty()) {
				rushes++;
				Xo.title("MOB WAVE!", "The emeralds woke the Overworld", 3);
				MobWar.spawn("zombie", 3 + rushes, 12, 20, 180, 0, null);
				MobWar.spawn("creeper", 2, 14, 22, 180, 0, null);
				Xo.gta("police", "stars", 2);
				Xo.after(25, () -> until(XoMod::newRound, 20));
			}

			return;
		}
	}

	private static void walkTo(final int i) {
		if (i >= emeralds.size()) {
			return;
		}

		double[] g = Xo.toGta(Vec3.atBottomCenterOf(emeralds.get(i).above()));
		Xo.gta("walk", "x", g[0], "y", g[1], "z", g[2], "speed", 1.5, "timeout", 7000);
	}
}
