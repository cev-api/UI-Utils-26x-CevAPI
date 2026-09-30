/*
 * Adapted from Wurst7's RegistrySyncBypass (GPL-3.0-or-later).
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 */
package com.ui_utils.uiutils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.ui_utils.mixin.MappedRegistryFrozenAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Applies Wurst's cautious registry-sync workaround when the user enables it. */
public final class RegistrySyncBypass {
	private static final int MAX_LOGGED_REGISTRIES = 24;

	private RegistrySyncBypass() {
	}

	public static boolean isEnabled() {
		return UiUtilsSettings.get().ignoreRegistrySync
			&& !UiUtilsMultiplayerCompat.hasExternalRegistrySyncControl();
	}

	/** Called before Fabric remaps the incoming registry payload. */
	public static void prepareIncomingMap(
		Map<Identifier, Object2IntMap<Identifier>> registryMap) {
		if (!isEnabled() || registryMap == null || registryMap.isEmpty())
			return;

		List<Identifier> unknownRegistries = new ArrayList<>();
		List<Identifier> spoofedRegistries = new ArrayList<>();
		List<String> unsyncedRegistries = new ArrayList<>();
		Iterator<Map.Entry<Identifier, Object2IntMap<Identifier>>> registries =
			registryMap.entrySet().iterator();

		while (registries.hasNext()) {
			Map.Entry<Identifier, Object2IntMap<Identifier>> entry =
				registries.next();
			Identifier registryId = entry.getKey();
			Object2IntMap<Identifier> remoteIds = entry.getValue();
			Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(registryId);

			if (registry == null) {
				unknownRegistries.add(registryId);
				registries.remove();
				continue;
			}

			if (remoteIds == null) {
				registries.remove();
				continue;
			}

			int unknownEntries = countUnknownEntries(registry, remoteIds);
			if (unknownEntries == 0)
				continue;

			if (spoofUnknownEntries(registry, remoteIds)) {
				spoofedRegistries.add(registryId);
				continue;
			}

			unsyncedRegistries.add(registryId + " (" + unknownEntries
				+ " unknown entries)");
			registries.remove();
		}

		logResult(unknownRegistries, spoofedRegistries, unsyncedRegistries);
	}

	private static int countUnknownEntries(Registry<?> registry,
		Object2IntMap<Identifier> remoteIds) {
		int count = 0;
		for (Identifier id : remoteIds.keySet())
			if (!registry.containsKey(id))
				count++;
		return count;
	}

	private static boolean spoofUnknownEntries(Registry<?> registry,
		Object2IntMap<Identifier> remoteIds) {
		if (registry != BuiltInRegistries.MOB_EFFECT)
			return false;

		for (Identifier id : remoteIds.keySet())
			if (!registry.containsKey(id) && !registerMobEffect(id))
				return false;
		return true;
	}

	/** Registers an inert mob-effect placeholder to keep remote raw IDs aligned. */
	@SuppressWarnings("unchecked")
	private static boolean registerMobEffect(Identifier id) {
		MappedRegistry<MobEffect> registry =
			(MappedRegistry<MobEffect>)(Object)BuiltInRegistries.MOB_EFFECT;
		MappedRegistryFrozenAccessor frozen =
			(MappedRegistryFrozenAccessor)(Object)registry;
		boolean wasFrozen = frozen.uiutils$isFrozen();
		frozen.uiutils$setFrozen(false);
		try {
			Registry.register(registry, id, new PlaceholderMobEffect());
			return true;
		} catch (Throwable e) {
			UiUtils.LOGGER.warn("Couldn't add registry-sync placeholder {}", id, e);
			return false;
		} finally {
			frozen.uiutils$setFrozen(wasFrozen);
		}
	}

	private static void logResult(List<Identifier> unknownRegistries,
		List<Identifier> spoofedRegistries, List<String> unsyncedRegistries) {
		if (unknownRegistries.isEmpty() && spoofedRegistries.isEmpty()
			&& unsyncedRegistries.isEmpty())
			return;

		UiUtils.LOGGER.warn("Registry sync bypass: {} registries spoofed, {} left"
			+ " unsynced, {} unknown registries ignored", spoofedRegistries.size(),
			unsyncedRegistries.size(), unknownRegistries.size());
		logList("Spoofed entries in ", toNames(spoofedRegistries));
		logList("Left unsynced (server IDs may not match): ", unsyncedRegistries);
		logList("Ignored unknown registry ", toNames(unknownRegistries));
	}

	private static List<String> toNames(List<Identifier> ids) {
		List<String> names = new ArrayList<>(ids.size());
		ids.forEach(id -> names.add(id.toString()));
		return names;
	}

	private static void logList(String prefix, List<String> items) {
		int limit = Math.min(items.size(), MAX_LOGGED_REGISTRIES);
		for (int i = 0; i < limit; i++)
			UiUtils.LOGGER.warn("Registry sync: {}{}", prefix, items.get(i));
		if (items.size() > limit)
			UiUtils.LOGGER.warn("Registry sync: ...and {} more", items.size() - limit);
	}

	private static final class PlaceholderMobEffect extends MobEffect {
		private PlaceholderMobEffect() {
			super(MobEffectCategory.NEUTRAL, 0xFFFFFF);
		}
	}
}
