package bl4ckscor3.mod.particleculling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import bl4ckscor3.mod.particleculling.ParticleCulling.Configuration;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockPos.MutableBlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class CullThread extends Thread {
	private double sleepOverhead = 0.0D;
	private final List<EntityFX> temporaryParticleStorage = new ArrayList<>();

	public CullThread() {
		setName("Particle Culling");
		setDaemon(true);
	}

	@Override
	public void run() {
		Minecraft mc = Minecraft.getMinecraft();

		while (!Thread.currentThread().isInterrupted()) {
			try {
				long start = System.nanoTime();

				if (mc.theWorld != null) {
					for (int i = 0; i < 4; i++) {
						for (int j = 0; j < 2; j++) {
							iterateParticles(mc, mc.effectRenderer.fxLayers[i][j]);
						}
					}
				}

				double d = (System.nanoTime() - start) / 1_000_000.0D + sleepOverhead;
				long sleepTime = 10 - (long) d;

				sleepOverhead = d % 1.0D;

				if (sleepTime > 0)
					Thread.sleep(sleepTime);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	//thanks to Meldexun, minimizes CMEs and NSEEs when iterating over an fx layer
	private void iterateParticles(Minecraft mc, List<EntityFX> deque) {//ArrayDeque
		try {
			Iterator<EntityFX> iterator = deque.iterator();

			while (iterator.hasNext()) {
				EntityFX particle;

				try {
					particle = iterator.next();
				}
				catch (ConcurrentModificationException | NoSuchElementException e) {
					break; //break the loop, as continuing it would just lead to more exceptions as the list backing the iterator got changed
				}

				if (particle != null)
					temporaryParticleStorage.add(particle);
			}

			for (EntityFX particle : temporaryParticleStorage) {
				((CullCheck) particle).setCulled(shouldCullParticle(particle, mc));
			}
		}
		finally {
			temporaryParticleStorage.clear();
		}
	}

	private boolean shouldCullParticle(EntityFX particle, Minecraft mc) {
		try {
			if (!Configuration.cullingEnabled || !Configuration.cullInSpectator && mc.thePlayer.isSpectator())
				return false;

			ICamera camera = ((CameraHolder) mc.renderGlobal).getCamera();

			if (camera == null || ParticleCulling.isParticleIgnored(particle.getClass()))
				return false;

			if (camera.isBoundingBoxInFrustum(particle.getEntityBoundingBox())) {//getBoundingBox
				if (Configuration.cullBehindBlocks) {
					Entity entity = mc.getRenderViewEntity();

					if (entity != null)
						return shouldCull(entity.worldObj, new Vec3(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ), new Vec3(particle.posX, particle.posY, particle.posZ));
				}

				return false;
			}

			return true;
		}
		catch (Exception e) {
			//make sure particles that error are never culled
			return false;
		}
	}

	//adapted from World#rayTraceBlocks to be able to ray trace through blocks
	private boolean shouldCull(World world, Vec3 from, Vec3 to) {
		if (!Double.isNaN(from.xCoord) && !Double.isNaN(from.yCoord) && !Double.isNaN(from.zCoord) && !Double.isNaN(to.xCoord) && !Double.isNaN(to.yCoord) && !Double.isNaN(to.zCoord)) {
			boolean opacityCheck = false;
			int blocks = 0;
			int toX = MathHelper.floor_double(to.xCoord);
			int toY = MathHelper.floor_double(to.yCoord);
			int toZ = MathHelper.floor_double(to.zCoord);
			int checkX = MathHelper.floor_double(from.xCoord);
			int checkY = MathHelper.floor_double(from.yCoord);
			int checkZ = MathHelper.floor_double(from.zCoord);
			MutableBlockPos pos = new MutableBlockPos(checkX, checkY, checkZ);
			IBlockState state = world.getBlockState(pos);
			Block block = state.getBlock();

			//state.getCollisionBoundingBox							Block.NULL_AABB									state
			if (block.getCollisionBoundingBox(world, pos, state) != null && block.canCollideCheck(state, false) && block.collisionRayTrace(world, pos, from, to) != null) {
				blocks++;														//state					state
				opacityCheck = opacityCheck || Configuration.cullBehindGlass || (block.isFullCube() && block.isOpaqueCube());
			}

			int maxIterations = 50;

			while (maxIterations-- >= 0) {
				if (checkX == toX && checkY == toY && checkZ == toZ)
					return opacityCheck && (++blocks > Configuration.blockBuffer);

				boolean wasXChanged = true;
				boolean wasYChanged = true;
				boolean wasZChanged = true;
				double d0 = 999.0D;
				double d1 = 999.0D;
				double d2 = 999.0D;

				if (toX > checkX)
					d0 = checkX + 1.0D;
				else if (toX < checkX)
					d0 = checkX + 0.0D;
				else
					wasXChanged = false;

				if (toY > checkY)
					d1 = checkY + 1.0D;
				else if (toY < checkY)
					d1 = checkY + 0.0D;
				else
					wasYChanged = false;

				if (toZ > checkZ)
					d2 = checkZ + 1.0D;
				else if (toZ < checkZ)
					d2 = checkZ + 0.0D;
				else
					wasZChanged = false;

				double d3 = 999.0D;
				double d4 = 999.0D;
				double d5 = 999.0D;
				double d6 = to.xCoord - from.xCoord;
				double d7 = to.yCoord - from.yCoord;
				double d8 = to.zCoord - from.zCoord;

				if (wasXChanged)
					d3 = (d0 - from.xCoord) / d6;

				if (wasYChanged)
					d4 = (d1 - from.yCoord) / d7;

				if (wasZChanged)
					d5 = (d2 - from.zCoord) / d8;

				if (d3 == -0.0D)
					d3 = -1.0E-4D;

				if (d4 == -0.0D)
					d4 = -1.0E-4D;

				if (d5 == -0.0D)
					d5 = -1.0E-4D;

				EnumFacing facing;

				if (d3 < d4 && d3 < d5) {
					facing = toX > checkX ? EnumFacing.WEST : EnumFacing.EAST;
					from.xCoord = d0;
					from.yCoord += d7 * d3;
					from.zCoord += d8 * d3;
				}
				else if (d4 < d5) {
					facing = toY > checkY ? EnumFacing.DOWN : EnumFacing.UP;
					from.xCoord += d6 * d4;
					from.yCoord = d1;
					from.zCoord += d8 * d4;
				}
				else {
					facing = toZ > checkZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
					from.xCoord += d6 * d5;
					from.yCoord += d7 * d5;
					from.zCoord = d2;
				}

				checkX = MathHelper.floor_double(from.xCoord) - (facing == EnumFacing.EAST ? 1 : 0);
				checkY = MathHelper.floor_double(from.yCoord) - (facing == EnumFacing.UP ? 1 : 0);
				checkZ = MathHelper.floor_double(from.zCoord) - (facing == EnumFacing.SOUTH ? 1 : 0);
				pos.set(checkX, checkY, checkZ);//setPos
				state = world.getBlockState(pos);
				block = state.getBlock();
				//state																								Block.NULL_AABB								state
				if (block.getMaterial() == Material.portal || block.getCollisionBoundingBox(world, pos, state) != null && block.canCollideCheck(state, false) && block.collisionRayTrace(world, pos, from, to) != null) {
					opacityCheck = opacityCheck || Configuration.cullBehindGlass || (block.isFullCube() && block.isOpaqueCube());	//state

					if (++blocks > Configuration.blockBuffer)
						return opacityCheck;
				}
			}

			return opacityCheck && blocks > Configuration.blockBuffer;
		}

		return false;
	}
}
