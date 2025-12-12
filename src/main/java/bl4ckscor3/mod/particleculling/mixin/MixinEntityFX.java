package bl4ckscor3.mod.particleculling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import bl4ckscor3.mod.particleculling.CullCheck;
import net.minecraft.client.particle.EntityFX;

@Mixin(EntityFX.class)
public class MixinEntityFX implements CullCheck {
	@Unique
	private boolean culled;

	@Override
	public void setCulled(boolean culled) {
		this.culled = culled;
	}

	@Override
	public boolean isCulled() {
		return culled;
	}
}
