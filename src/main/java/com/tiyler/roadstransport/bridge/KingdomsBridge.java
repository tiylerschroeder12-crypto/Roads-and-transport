package com.tiyler.roadstransport.bridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class KingdomsBridge {
    private final JavaPlugin owner;
    private final Plugin kingdoms;
    private final Object gameData;
    private final Object claimService;
    private final Object currencyService;
    private final Class<?> claimClass;
    private final Class<?> claimTypeClass;
    private final Class<?> chunkPosClass;

    public KingdomsBridge(JavaPlugin owner) throws ReflectiveOperationException {
        this.owner = owner;
        this.kingdoms = Bukkit.getPluginManager().getPlugin("KingdomsAndCurrency");
        if (kingdoms == null || !kingdoms.isEnabled()) {
            throw new IllegalStateException("KingdomsAndCurrency must be installed and enabled.");
        }
        gameData = invoke(kingdoms, "gameData");
        claimService = invoke(kingdoms, "claimService");
        Field currencyField = kingdoms.getClass().getDeclaredField("currencyService");
        currencyField.setAccessible(true);
        currencyService = currencyField.get(kingdoms);
        ClassLoader loader = kingdoms.getClass().getClassLoader();
        claimClass = Class.forName("com.tiyler.kingdoms.model.Claim", true, loader);
        claimTypeClass = Class.forName("com.tiyler.kingdoms.model.ClaimType", true, loader);
        chunkPosClass = Class.forName("com.tiyler.kingdoms.model.ChunkPos", true, loader);
    }

    public boolean withdrawPurse(UUID playerId, long amount) {
        try {
            return (boolean) invoke(currencyService, "withdrawPurse", new Class[]{UUID.class, long.class}, playerId, amount);
        } catch (ReflectiveOperationException ex) {
            fail("withdraw purse", ex);
            return false;
        }
    }

    public void depositPurse(UUID playerId, long amount) {
        if (amount <= 0) return;
        try {
            invoke(currencyService, "depositPurse", new Class[]{UUID.class, long.class}, playerId, amount);
        } catch (ReflectiveOperationException ex) {
            fail("deposit purse", ex);
        }
    }

    public long purse(UUID playerId) {
        try {
            return ((Number) invoke(currencyService, "purse", new Class[]{UUID.class}, playerId)).longValue();
        } catch (ReflectiveOperationException ex) {
            fail("read purse", ex);
            return 0;
        }
    }


    public boolean canBuild(Player player, Location location) {
        Object claim = claimAt(location);
        try {
            return (boolean) invoke(claimService, "canBuild", new Class[]{Player.class, claimClass}, player, claim);
        } catch (ReflectiveOperationException ex) {
            fail("check build permission", ex);
            return false;
        }
    }

    public Object claimAt(Location location) {
        try {
            return invoke(claimService, "claimAt", new Class[]{Location.class}, location);
        } catch (ReflectiveOperationException ex) {
            fail("find claim", ex);
            return null;
        }
    }

    public Object claim(UUID id) {
        if (id == null) return null;
        try {
            return invoke(gameData, "claim", new Class[]{UUID.class}, id);
        } catch (ReflectiveOperationException ex) {
            fail("find claim by id", ex);
            return null;
        }
    }

    public UUID claimId(Object claim) { return uuidValue(claim, "id"); }
    public UUID claimOwner(Object claim) { return uuidValue(claim, "ownerId"); }
    public String claimName(Object claim) { return stringValue(claim, "name"); }

    public String claimType(Object claim) {
        if (claim == null) return null;
        try {
            Object type = invoke(claim, "type");
            return type == null ? null : type.toString();
        } catch (ReflectiveOperationException ex) {
            fail("read claim type", ex);
            return null;
        }
    }

    public String claimRank(Object claim) {
        if (claim == null) return null;
        try {
            Object rank = invoke(claim, "rank");
            return rank == null ? null : rank.toString();
        } catch (ReflectiveOperationException ex) {
            fail("read claim rank", ex);
            return null;
        }
    }

    public boolean isPolitical(Object claim) {
        return booleanValue(claim, "isPoliticalLand");
    }

    public boolean isPersonal(Object claim) {
        return booleanValue(claim, "isPersonal");
    }

    public boolean isOwner(Object claim, UUID playerId) {
        return claim != null && playerId.equals(claimOwner(claim));
    }

    public boolean isKnight(Object claim, UUID playerId) {
        if (claim == null) return false;
        try {
            Object value = invoke(claim, "knights");
            return value instanceof Set<?> set && set.contains(playerId);
        } catch (ReflectiveOperationException ex) {
            fail("read knights", ex);
            return false;
        }
    }

    public boolean isOwnerOrKnight(Object claim, UUID playerId) {
        return isOwner(claim, playerId) || isKnight(claim, playerId);
    }

    public boolean isCitizen(Object claim, UUID playerId) {
        if (claim == null) return false;
        try {
            Object value = invoke(claim, "citizens");
            return value instanceof Set<?> set && set.contains(playerId);
        } catch (ReflectiveOperationException ex) {
            fail("read citizens", ex);
            return false;
        }
    }

    public String realmName(UUID playerId) {
        try {
            Object claim = invoke(claimService, "realmLand", new Class[]{UUID.class}, playerId);
            return claim == null ? null : claimName(claim);
        } catch (ReflectiveOperationException ex) {
            fail("read player realm", ex);
            return null;
        }
    }

    public void depositTreasury(UUID claimId, long amount) {
        if (claimId == null || amount <= 0) return;
        Object claim = claim(claimId);
        if (claim == null) return;
        try {
            invoke(currencyService, "depositTreasury", new Class[]{claimClass, long.class}, claim, amount);
        } catch (ReflectiveOperationException ex) {
            fail("deposit treasury", ex);
        }
    }

    public UUID createHomeClaim(Player player, Location location, String homeName) {
        try {
            UUID id = UUID.randomUUID();
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object personal = Enum.valueOf((Class<? extends Enum>) claimTypeClass.asSubclass(Enum.class), "PERSONAL");
            Constructor<?> constructor = claimClass.getConstructor(UUID.class, String.class, claimTypeClass, UUID.class);
            Object claim = constructor.newInstance(id, "Home - " + player.getName() + " - " + homeName, personal, player.getUniqueId());
            Method of = chunkPosClass.getMethod("of", Location.class);
            Object chunkPos = of.invoke(null, location);
            Object chunks = invoke(claim, "chunks");
            if (!(chunks instanceof Collection collection)) throw new IllegalStateException("Claim chunks is not a collection");
            collection.add(chunkPos);
            invoke(gameData, "addClaim", new Class[]{claimClass}, claim);
            return id;
        } catch (ReflectiveOperationException ex) {
            fail("create home claim", ex);
            return null;
        }
    }

    public boolean removeClaim(UUID claimId) {
        Object claim = claim(claimId);
        if (claim == null) return false;
        try {
            invoke(gameData, "removeClaim", new Class[]{claimClass}, claim);
            return true;
        } catch (ReflectiveOperationException ex) {
            fail("remove home claim", ex);
            return false;
        }
    }

    public boolean claimStillOwnedBy(UUID claimId, UUID ownerId) {
        Object claim = claim(claimId);
        return claim != null && ownerId.equals(claimOwner(claim));
    }

    public OfflinePlayer offlinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private UUID uuidValue(Object target, String method) {
        if (target == null) return null;
        try {
            Object value = invoke(target, method);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException ex) {
            fail("read " + method, ex);
            return null;
        }
    }

    private String stringValue(Object target, String method) {
        if (target == null) return null;
        try {
            Object value = invoke(target, method);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ex) {
            fail("read " + method, ex);
            return null;
        }
    }

    private boolean booleanValue(Object target, String method) {
        if (target == null) return false;
        try {
            return (boolean) invoke(target, method);
        } catch (ReflectiveOperationException ex) {
            fail("read " + method, ex);
            return false;
        }
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return invoke(target, method, new Class[0]);
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private void fail(String operation, Exception ex) {
        owner.getLogger().severe("KingdomsAndCurrency integration could not " + operation + ": " + ex.getMessage());
    }
}
