package io.github.apace100.origins.component;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.origin.Origin;
import io.github.apace100.origins.origin.OriginRegistry;
import io.github.apace100.origins.power.Power;
import io.github.apace100.origins.power.PowerType;
import io.github.apace100.origins.registry.ModComponents;
import io.github.apace100.origins.registry.ModRegistries;
import nerdhub.cardinal.components.api.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PlayerOriginComponent implements OriginComponent {

    private PlayerEntity player;
    private Origin origin;
    private HashMap<PowerType<?>, Power> powers = new HashMap<>();

    private boolean hadOriginBefore = false;

    public PlayerOriginComponent(PlayerEntity player) {
        this.player = player;
        this.origin = Origin.EMPTY;
    }

    @Override
    public boolean hasOrigin() {
        return origin != null && origin != Origin.EMPTY;
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    @Override
    public boolean hadOriginBefore() {
        return hadOriginBefore;
    }

    @Override
    public boolean hasPower(PowerType<?> powerType) {
        return powers.containsKey(powerType);
    }

    @Override
    public <T extends Power> T getPower(PowerType<T> powerType) {
        if(powers.containsKey(powerType)) {
            return (T)powers.get(powerType);
        }
        return null;
    }

    @Override
    public List<Power> getPowers() {
        List<Power> list = new LinkedList<>();
        list.addAll(powers.values());
        return list;
    }

    @Override
    public <T extends Power> List<T> getPowers(Class<T> powerClass) {
        return getPowers(powerClass, false);
    }

    @Override
    public <T extends Power> List<T> getPowers(Class<T> powerClass, boolean includeInactive) {
        List<T> list = new LinkedList<>();
        for(Power power : powers.values()) {
            if(powerClass.isAssignableFrom(power.getClass()) && (includeInactive || power.isActive())) {
                list.add((T)power);
            }
        }
        return list;
    }

    @Override
    public void setOrigin(Origin origin) {
        if(this.origin == origin) {
            return;
        }
        if(this.origin != null) {
            for (Power power: powers.values()) {
                power.onRemoved();
            }
            powers.clear();
        }
        this.origin = origin;
        origin.getPowerTypes().forEach(powerType -> {
            Power power = powerType.create(player);
            this.powers.put(powerType, power);
            power.onAdded();
        });
        if(this.origin != null && this.origin != Origin.EMPTY) {
            this.hadOriginBefore = true;
        }
    }

    @Override
    public void fromTag(CompoundTag compoundTag) {
        this.fromTag(compoundTag, true);
    }

    private void fromTag(CompoundTag compoundTag, boolean callPowerOnAdd) {
        if(player == null) {
            Origins.LOGGER.error("Player was null in `fromTag`! This is a bug!");
        }
        if(this.origin != null) {
            if(callPowerOnAdd) {
                for (Power power: powers.values()) {
                    power.onRemoved();
                }
            }
            powers.clear();
        }
        try {
            this.origin = OriginRegistry.get(Identifier.tryParse(compoundTag.getString("Origin")));
        } catch(IllegalArgumentException e) {
            Origins.LOGGER.warn("Player " + player.getDisplayName().asString() + " had unregistered origin: " + compoundTag.getString("Origin"));
            this.origin = Origin.EMPTY;
        }
        this.hadOriginBefore = compoundTag.getBoolean("HadOriginBefore");
        ListTag powerList = (ListTag)compoundTag.get("Powers");
        for(int i = 0; i < powerList.size(); i++) {
            CompoundTag powerTag = powerList.getCompound(i);
            PowerType<?> type = ModRegistries.POWER_TYPE.get(Identifier.tryParse(powerTag.getString("Type")));
            if(origin.hasPowerType(type)) {
                Tag data = powerTag.get("Data");
                Power power = type.create(player);
                power.fromTag(data);
                this.powers.put(type, power);
                if(callPowerOnAdd) {
                    power.onAdded();
                }
            }
        }
        this.origin.getPowerTypes().forEach(pt -> {
            if(!this.powers.containsKey(pt)) {
                Power power = pt.create(player);
                this.powers.put(pt, power);
            }
        });
    }

    @Override
    public CompoundTag toTag(CompoundTag compoundTag) {
        compoundTag.putString("Origin", this.origin.getIdentifier().toString());
        compoundTag.putBoolean("HadOriginBefore", this.hadOriginBefore);
        ListTag powerList = new ListTag();
        for(Map.Entry<PowerType<?>, Power> powerEntry : powers.entrySet()) {
            CompoundTag powerTag = new CompoundTag();
            powerTag.putString("Type", ModRegistries.POWER_TYPE.getId(powerEntry.getKey()).toString());
            powerTag.put("Data", powerEntry.getValue().toTag());
            powerList.add(powerTag);
        }
        compoundTag.put("Powers", powerList);
        return compoundTag;
    }

    @Override
    public void readFromPacket(PacketByteBuf buf) {
        CompoundTag compoundTag = buf.readCompoundTag();
        if(compoundTag != null) {
            this.fromTag(compoundTag, false);
        }
    }

    @Override
    public Entity getEntity() {
        return this.player;
    }

    @Override
    public ComponentType<?> getComponentType() {
        return ModComponents.ORIGIN;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("OriginComponent(" + this.origin.getIdentifier() + ")[\n");
        for (Map.Entry<PowerType<?>, Power> powerEntry : powers.entrySet()) {
            str.append("\t").append(ModRegistries.POWER_TYPE.getId(powerEntry.getKey())).append(": ").append(powerEntry.getValue().toTag().toString()).append("\n");
        }
        str.append("]");
        return str.toString();
    }
}
