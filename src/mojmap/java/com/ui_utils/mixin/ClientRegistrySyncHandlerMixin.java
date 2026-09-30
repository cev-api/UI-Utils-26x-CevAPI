package com.ui_utils.mixin;

import com.ui_utils.uiutils.RegistrySyncBypass;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before Fabric rejects mismatched server registry data. */
@Mixin(targets = "net.fabricmc.fabric.impl.client.registry.sync.ClientRegistrySyncHandler",
	remap = false)
public abstract class ClientRegistrySyncHandlerMixin {
	@Inject(
		method = "checkRemoteRemap(Lnet/fabricmc/fabric/impl/registry/sync/packet/RegistrySyncPayload;)V",
		at = @At("HEAD"))
	private static void uiutils$prepareRegistrySync(RegistrySyncPayload payload,
		CallbackInfo ci) {
		if (payload != null)
			RegistrySyncBypass.prepareIncomingMap(payload.registryMap());
	}
}
