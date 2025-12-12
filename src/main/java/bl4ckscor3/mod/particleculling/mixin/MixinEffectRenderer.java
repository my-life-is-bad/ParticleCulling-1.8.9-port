package bl4ckscor3.mod.particleculling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bl4ckscor3.mod.particleculling.CullCheck;
import bl4ckscor3.mod.particleculling.ParticleCulling;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;

@Mixin(EffectRenderer.class)
public class MixinEffectRenderer {
	@Redirect(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;FFFFFF)V"))
	private void particleculling$maybeCullParticle(EntityFX particle, WorldRenderer buffer, Entity entity, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {	//BufferBuilder
		if (!((CullCheck) particle).isCulled())
			particle.renderParticle(buffer, entity, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
	}

	@Redirect(method = "renderLitParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;FFFFFF)V"))
	private void particleculling$maybeCullLitParticle(EntityFX particle, WorldRenderer buffer, Entity entity, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
		if (!((CullCheck) particle).isCulled())
			particle.renderParticle(buffer, entity, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
	}

	@Inject(method = "addEffect", at = @At("TAIL"))
	private void particleculling$initializeCullState(EntityFX particle, CallbackInfo ci) {
		((CullCheck) particle).setCulled(!ParticleCulling.isParticleIgnored(particle.getClass())); //make sure particles that have not been cull-checked yet don't flicker on the screen for a short moment
	}
}
