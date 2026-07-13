# LMA I/O Layer — API Reference

> **346 methods** across **20 files**. Pure Java — zero Minecraft boot needed for unit testing.
> Covers: TLM EntityMaid, Minecraft World/Entity/Item, Forge registries.

## Input Layer (Read-only, no side effects)

### MaidStateReader (120 methods)
`compat/vanilla/input/maid/MaidStateReader.java`

| Method | Returns | Source |
|--------|---------|--------|
| `getHealth(m)` | `float` | MC Entity |
| `getMaxHealth(m)` | `float` | MC Entity |
| `getHealthRatio(m)` | `float` | MC + calc |
| `getHunger(m)` | `int` | TLM |
| `getFavorability(m)` | `int` | TLM |
| `getFavorLevel(m)` | `int` | TLM FavorabilityManager |
| `getFavorLevelPercent(m)` | `double` | TLM FavorabilityManager |
| `getNextLevelPoint(m)` | `int` | TLM FavorabilityManager |
| `getExperience(m)` | `int` | TLM |
| `getExperienceReward(m)` | `int` | MC Entity |
| `getLuck(m)` | `float` | TLM |
| `getVoicePitch(m)` | `float` | TLM |
| `isOnFire(m)` | `boolean` | MC Entity |
| `isInWater(m)` | `boolean` | MC Entity |
| `isInLava(m)` | `boolean` | MC Entity |
| `isInRain(m)` | `boolean` | MC + calc |
| `isSitting(m)` | `boolean` | TLM |
| `isSleeping(m)` | `boolean` | MC Entity |
| `isSprinting(m)` | `boolean` | MC Entity |
| `isSwimming(m)` | `boolean` | MC Entity |
| `isSneaking(m)` | `boolean` | MC Entity |
| `isRiding(m)` | `boolean` | MC Entity |
| `isBlocking(m)` | `boolean` | TLM |
| `isBaby(m)` | `boolean` | MC Entity |
| `isTamed(m)` | `boolean` | TLM |
| `isInvulnerable(m)` | `boolean` | TLM |
| `isInvisible(m)` | `boolean` | MC Entity |
| `isGlowing(m)` | `boolean` | MC Entity |
| `isSwinging(m)` | `boolean` | TLM |
| `isAiming(m)` | `boolean` | TLM |
| `isBegging(m)` | `boolean` | TLM |
| `isRideable(m)` | `boolean` | TLM |
| `isStruckByLightning(m)` | `boolean` | TLM |
| `canClimb(m)` | `boolean` | TLM |
| `canBrainMove(m)` | `boolean` | TLM |
| `canUseShield(m)` | `boolean` | TLM |
| `hasTarget(m)` | `boolean` | MC Entity |
| `hasWeapon(m)` | `boolean` | MC + calc |
| `hasShield(m)` | `boolean` | TLM |
| `hasAnyEffect(m)` | `boolean` | MC Entity |
| `hasEffect(m, effectId)` | `boolean` | MC + Forge |
| `hasCurse(m)` | `boolean` | MC + calc |
| `getMainhand(m)` | `ItemStack` | MC Entity |
| `getMainhandId(m)` | `String` | MC + Forge |
| `getOffhand(m)` | `ItemStack` | MC Entity |
| `getOffhandId(m)` | `String` | MC + Forge |
| `getArmor(m)` | `float` | MC Entity |
| `getArmorToughness(m)` | `float` | MC Attribute |
| `getAttackDamage(m)` | `float` | MC Attribute |
| `getMovementSpeed(m)` | `float` | MC Attribute |
| `getAttackSpeed(m)` | `double` | MC Attribute |
| `hasHelmet(m)` | `boolean` | MC Entity |
| `hasChestplate(m)` | `boolean` | MC Entity |
| `hasLeggings(m)` | `boolean` | MC Entity |
| `hasBoots(m)` | `boolean` | MC Entity |
| `getOwnerDistance(m)` | `float` | MC Entity + calc |
| `getOwnerHealth(m)` | `float` | MC Entity |
| `getOwnerHealthRatio(m)` | `float` | MC + calc |
| `isOwnerNearby(m)` | `boolean` | MC + calc |
| `ownerHasAttackTarget(m)` | `boolean` | MC Entity |
| `getOwnerHoldingItem(m)` | `ItemStack` | MC Entity |
| `getOwnerHoldingItemId(m)` | `String` | MC + Forge |
| `getOwnerArmor(m)` | `float` | MC Entity |
| `getOwnerOffhandId(m)` | `String` | MC + Forge |
| `getModelId(m)` | `String` | TLM |
| `getRestrictRadius(m)` | `float` | TLM |
| `getRestrictCenter(m)` | `BlockPos` | TLM |
| `hasRestriction(m)` | `boolean` | TLM |
| `isWithinRestriction(m)` | `boolean` | TLM |
| `getFallDistance(m)` | `float` | MC Entity |
| `getAirSupply(m)` | `int` | MC Entity |
| `getMaxAirSupply(m)` | `int` | MC Entity |
| `isUsingItem(m)` | `boolean` | MC Entity |
| `getTaskUid(m)` | `String` | TLM |
| `isHomeMode(m)` | `boolean` | TLM |
| `getSchedule(m)` | `String` | TLM |
| `getScheduleDetail(m)` | `String` | TLM |
| `isScheduleConfigured(m)` | `boolean` | TLM SchedulePos |
| `getWorkPos(m)` | `BlockPos` | TLM SchedulePos |
| `getIdlePos(m)` | `BlockPos` | TLM SchedulePos |
| `getSleepPos(m)` | `BlockPos` | TLM SchedulePos |
| `getNearestSchedulePos(m)` | `BlockPos` | TLM SchedulePos |
| `isPickup(m)` | `boolean` | TLM |
| `getSoundPackId(m)` | `String` | TLM |
| `getBackpackType(m)` | `String` | TLM |
| `getBackpackSlots(m)` | `int` | TLM |
| `getBackpackFluid(m)` | `String` | TLM |
| `hasBackpack(m)` | `boolean` | TLM |
| `isShowBackpack(m)` | `boolean` | TLM Config |
| `isChatBubbleShow(m)` | `boolean` | TLM Config |
| `getBackpackShowItem(m)` | `ItemStack` | TLM |
| `hasFishingHook(m)` | `boolean` | TLM |
| `isHoldingProjectile(m)` | `boolean` | MC + calc |
| `getBaubleIds(m)` | `String` | TLM |
| `onHurt(m)` | `boolean` | TLM |
| `isFood(m, stack)` | `boolean` | TLM |
| `canBeLeashed(m, player)` | `boolean` | TLM |
| `canPickup(m, entity)` | `boolean` | TLM |
| `canPathReachPos(m, pos)` | `boolean` | TLM |
| `canPathReachEntity(m, target)` | `boolean` | TLM |
| `canSee(m, target)` | `boolean` | TLM |
| `canDestroyBlock(m, pos)` | `boolean` | TLM |
| `canPlaceBlock(m, pos)` | `boolean` | TLM |
| `getSearchRadius(m)` | `float` | TLM |
| `getBrainSearchPos(m)` | `BlockPos` | TLM |
| `getBlockPos(m)` | `BlockPos` | MC Entity |
| `getPosition(m)` | `Vec3` | MC Entity |
| `getLookAngle(m)` | `Vec3` | MC Entity |
| `getEyePosition(m)` | `Vec3` | MC Entity |
| `getMeleeAttackRangeSqr(m, t)` | `double` | TLM |
| `getHandItemsForAnimation(m)` | `ItemStack[]` | TLM |
| `isStructureSpawn(m)` | `boolean` | TLM |
| `getPersistentData(m)` | `CompoundTag` | MC Entity |
| `getPersistentInt(m, key, def)` | `int` | MC NBT |

### TargetStateReader (25 methods)
`compat/vanilla/input/target/TargetStateReader.java`

| Method | Returns | Source |
|--------|---------|--------|
| `getDistance(m, t)` | `double` | MC Entity |
| `getHorizontalDistance(m, t)` | `double` | MC + calc |
| `getVerticalDistance(m, t)` | `double` | MC + calc |
| `getHealth(t)` | `float` | MC Entity |
| `getHealthRatio(t)` | `float` | MC + calc |
| `getMaxHealth(t)` | `float` | MC Entity |
| `getArmor(t)` | `float` | MC Entity |
| `getName(t)` | `String` | MC Entity |
| `getTypeId(t)` | `String` | MC + Forge |
| `getPosition(t)` | `Vec3` | MC Entity |
| `getBlockPos(t)` | `BlockPos` | MC Entity |
| `isAlive(t)` | `boolean` | MC Entity |
| `isBaby(t)` | `boolean` | MC Entity |
| `isBoss(t)` | `boolean` | MC + calc |
| `isMonster(t)` | `boolean` | MC Entity |
| `isAnimal(t)` | `boolean` | MC Entity |
| `isUndead(t)` | `boolean` | MC Entity |
| `isPlayer(t)` | `boolean` | MC Entity |
| `isBurning(t)` | `boolean` | MC Entity |
| `isOnGround(t)` | `boolean` | MC Entity |
| `canSee(m, t)` | `boolean` | MC Entity |
| `wouldLethal(m, t, dmg)` | `boolean` | MC + calc |
| `hasEffect(t, effect)` | `boolean` | MC Entity |
| `getHoldingItem(t)` | `ItemStack` | MC Entity |
| `getHoldingItemId(t)` | `String` | MC + Forge |

### WorldStateReader (19 methods)
`compat/vanilla/input/world/WorldStateReader.java`

| Method | Returns | Source |
|--------|---------|--------|
| `getDimension(w)` | `String` | MC Level |
| `getTime(w)` | `long` | MC Level |
| `isDay(w)` | `boolean` | MC Level |
| `isNight(w)` | `boolean` | MC Level |
| `isRaining(w)` | `boolean` | MC Level |
| `isThundering(w)` | `boolean` | MC Level |
| `getMoonPhase(w)` | `int` | MC Level |
| `getDifficulty(w)` | `int` | MC Level |
| `getBiome(w, pos)` | `String` | MC Level |
| `getLightLevel(w, pos)` | `int` | MC Level |
| `hasDaylight(w, pos)` | `boolean` | MC Level |
| `getBlockState(w, pos)` | `BlockState` | MC Level |
| `isCriticalAttack(src)` | `boolean` | MC DamageSource |
| `bypassesArmor(src)` | `boolean` | MC DamageSource |
| `getDamageType(src)` | `String` | MC DamageSource |
| `getPlayerByUUID(level, uuid)` | `Player` | MC Level |
| `getEntitiesInRange(w, pos, r, class, filter)` | `List<T>` | MC Level |

---

## Output Layer (Write, side effects)

### MaidStateWriter (53 methods)
`compat/vanilla/output/maid/MaidStateWriter.java`

| Method | Returns | Source |
|--------|---------|--------|
| `setSitting(m, v)` | `void` | TLM |
| `setInvisible(m, v)` | `void` | MC Entity |
| `setGlowing(m, v)` | `void` | MC Entity |
| `setInvulnerable(m, v)` | `void` | TLM |
| `setSilent(m, v)` | `void` | MC Entity |
| `setHome(m, pos)` | `void` | TLM |
| `setHomeMode(m, v)` | `void` | TLM |
| `setHunger(m, v)` | `void` | TLM |
| `setFavor(m, v)` | `void` | TLM |
| `setFavorMax(m)` | `void` | TLM |
| `addFavor(m, amount)` | `void` | TLM |
| `reduceFavor(m, amount)` | `void` | TLM |
| `maxFavor(m)` | `void` | TLM |
| `setModel(m, id)` | `void` | TLM |
| `setTask(m, uid)` | `void` | TLM TaskManager |
| `forceTarget(m, target)` | `void` | MC Entity |
| `forceTargetByMode(m, mode)` | `void` | TLM + MC |
| `clearRestriction(m)` | `void` | TLM |
| `setAiming(m, v)` | `void` | TLM |
| `setBegging(m, v)` | `void` | TLM |
| `setRideable(m, v)` | `void` | TLM |
| `setCanClimb(m, v)` | `void` | TLM |
| `setSwinging(m, v)` | `void` | TLM |
| `swapHands(m)` | `void` | MC Entity |
| `dropHandItem(m)` | `void` | MC Entity |
| `dropHandItem(m, slot)` | `void` | MC Entity |
| `clearInventory(target)` | `void` | MC Entity |
| `setBackpackShowItem(m, id)` | `void` | TLM + Forge |
| `repairItem(m, slot, amount)` | `void` | MC ItemStack |
| `repairHandItemWithXp(m)` | `boolean` | TLM + MC |
| `setSchedulePos(m, pos, mode)` | `void` | TLM SchedulePos |
| `setBauble(m, id, slot)` | `void` | TLM + Forge |
| `teleportToOwner(m)` | `void` | TLM + MC |
| `setExperience(m, v)` | `void` | TLM |
| `addExperience(m, v)` | `void` | TLM |
| `setSoundPack(m, id)` | `void` | TLM |
| `setSchedule(m, schedule)` | `void` | TLM |
| `setPickup(m, v)` | `void` | TLM |
| `setPickup(m, v, type)` | `void` | TLM |
| `setPersistentInt(m, k, v)` | `void` | MC NBT |
| `setPersistentFloat(m, k, v)` | `void` | MC NBT |
| `setPersistentLong(m, k, v)` | `void` | MC NBT |
| `setPersistentString(m, k, v)` | `void` | MC NBT |
| `setPersistentBoolean(m, k, v)` | `void` | MC NBT |
| `removePersistent(m, k)` | `void` | MC NBT |
| `saveAndSwitchTask(m, name)` | `void` | TLM |
| `restorePreviousTask(m)` | `boolean` | TLM |
| `giveItem(m, id, count, nbt, target)` | `void` | Forge + MC |
| `dropFromSlot(m, source, count)` | `void` | MC Entity |
| `modifyAttribute(m, key, mode, amount)` | `void` | LMA MaidAttrRegistry |

### CombatOutput (24 methods)
`compat/vanilla/output/combat/CombatOutput.java`

| Method | Returns | Source |
|--------|---------|--------|
| `damage(t, s, amount)` | `void` | MC |
| `damageByRatio(t, s, ratio)` | `void` | MC |
| `damagePercent(t, s, pct)` | `void` | MC |
| `magicDamage(t, s, amount)` | `void` | MC |
| `genericDamage(t, s, amount)` | `void` | MC |
| `trueDamage(t, s, amount)` | `void` | MC |
| `executionKill(t, s)` | `void` | MC |
| `bleed(t, dmg, ticks)` | `void` | MC NBT |
| `damageNearby(center, range, dmg, hostile)` | `void` | MC |
| `heal(t, amount)` | `void` | MC |
| `healByRatio(t, ratio)` | `void` | MC |
| `healPercent(t, pct)` | `void` | MC |
| `knockback(t, s, strength)` | `void` | MC |
| `knockbackWithVertical(t, s, h, v)` | `void` | MC |
| `launch(t, s, h, v)` | `void` | MC |
| `push(t, s, strength)` | `void` | MC |
| `pull(t, s, strength)` | `void` | MC |
| `setFire(t, seconds)` | `void` | MC |
| `extinguish(t)` | `void` | MC |
| `shieldEffect(t, amount, dur)` | `void` | MC Effect |
| `shield(m, absorb)` | `void` | MC |
| `lifeSteal(t, s, dmg, ratio)` | `void` | MC |
| `launchProjectile(s, t, id, speed, inacc)` | `void` | MC + Forge |
| `doHurtTarget(m, t)` | `void` | TLM |

### WorldOutput (24 methods)
`compat/vanilla/output/world/WorldOutput.java`

| Method | Returns | Source |
|--------|---------|--------|
| `breakBlock(w, pos, drop)` | `void` | MC |
| `setBlock(w, pos, state, flags)` | `boolean` | MC |
| `placeBlock(w, pos, id)` | `void` | MC + Forge |
| `spawnEntity(w, id, pos)` | `void` | MC + Forge |
| `spawnEntityAt(w, id, pos, n, spread, nbt)` | `void` | MC + Forge |
| `summonLightning(w, pos)` | `void` | MC |
| `summonLightning(w, pos, cosmetic)` | `void` | MC |
| `createExplosion(w, pos, power, fire, destroy)` | `void` | MC |
| `createExplosion(w, source, pos, power, fire, destroy)` | `void` | MC |
| `setTime(w, time)` | `void` | MC |
| `setWeather(w, weather)` | `void` | MC |
| `setWeather(w, weather, duration)` | `void` | MC |
| `executeCommand(w, cmd)` | `void` | MC |
| `executeCommand(w, m, t, cmd, as, at)` | `void` | MC |
| `sendChat(p, msg)` | `void` | MC |
| `sendChatBroadcast(msg)` | `void` | MC + Forge |
| `sendBubble(m, text)` | `void` | TLM |
| `sendBubble(m, text, duration)` | `void` | TLM |
| `sendBubbleIfTimeout(m, text, timeout)` | `long` | TLM |
| `clearBubble(m, id)` | `void` | TLM |
| `openGui(w, p, pos)` | `boolean` | MC |
| `interactBlock(w, pos, face)` | `void` | MC |
| `breakBlockAt(w, pos, drop)` | `void` | MC |
| `tradeWithVillager(w, pos, prof, range, p)` | `boolean` | MC |

### MovementOutput (16 methods)
`compat/vanilla/output/movement/MovementOutput.java`

| Method | Returns | Source |
|--------|---------|--------|
| `teleport(e, pos)` | `void` | MC |
| `teleportOffset(e, rel, dir, dist)` | `void` | MC |
| `teleportWithMode(e, rel, mode, dist, side)` | `void` | MC |
| `dash(m, speed)` | `void` | MC |
| `dashToward(m, t, dist, mult)` | `void` | MC |
| `leap(m, h, v)` | `void` | MC |
| `leapToward(m, t, h, v)` | `void` | MC |
| `setMotion(e, x, y, z)` | `void` | MC |
| `faceTarget(m, t)` | `void` | MC Brain |
| `swapPosition(m, other)` | `void` | MC |
| `slow(t, ticks, amp)` | `void` | MC Effect |
| `freezeAi(m, frozen)` | `void` | MC Brain |
| `freezeAi(e, ticks)` | `void` | MC Brain + NBT |
| `followOwner(m)` | `void` | TLM + MC Brain |
| `guardPos(m, pos)` | `void` | TLM |
| `guardPos(m, pos, distance)` | `void` | TLM |

### VisualOutput (12 methods)
`compat/vanilla/output/visual/VisualOutput.java`

| Method | Returns | Source |
|--------|---------|--------|
| `playAnim(m, name, mode, setup)` | `void` | MC NBT |
| `playWeaponAnim(m, weaponId)` | `void` | MC NBT |
| `resetAnimation(m)` | `void` | MC NBT |
| `playSound(m, id, vol, pitch)` | `void` | MC + Forge |
| `playSoundAt(w, pos, id, vol, pitch)` | `void` | MC + Forge |
| `playSoundRandom(m, id, vol, pitch)` | `void` | MC + Forge |
| `playSoundModRandom(m, ns, vol, pitch)` | `void` | MC + Forge |
| `spawnParticle(w, id, pos, count, spread)` | `void` | MC |
| `spawnParticleAny(w, id, pos, count, spread)` | `void` | MC Registry |
| `spawnParticleServer(w, id, x, y, z, n, dx, dy, dz, spd)` | `void` | MC ServerLevel |
| `spawnHeartParticle(m)` | `void` | MC |
| `spawnHeartParticle(m, count)` | `void` | MC |

### EffectOutput (3 methods)
| Method | Returns | Source |
|--------|---------|--------|
| `apply(t, effectId, dur, amp, ambient)` | `void` | MC + Forge |
| `clearAll(t)` | `void` | MC |
| `clearEffect(t, effectId)` | `void` | MC |

### ItemOutput (15 methods)
`compat/vanilla/output/item/ItemOutput.java`

| Method | Returns | Source |
|--------|---------|--------|
| `repair(stack, amount)` | `void` | MC |
| `shrink(stack, amount)` | `void` | MC |
| `grow(stack, amount)` | `void` | MC |
| `split(stack, amount)` | `ItemStack` | MC |
| `setCount(stack, count)` | `void` | MC |
| `isEmpty(stack)` | `boolean` | MC |
| `isDamaged(stack)` | `boolean` | MC |
| `getMaxDamage(stack)` | `int` | MC |
| `getDamageValue(stack)` | `int` | MC |
| `isEnchanted(stack)` | `boolean` | MC |
| `getEnchantments(stack)` | `Map<RL,Int>` | MC |
| `hasEnchantment(stack, id)` | `boolean` | MC |
| `giveToPlayer(player, stack)` | `boolean` | MC |
| `extractXpAsBottles(m, ratio, max)` | `void` | TLM |

### ContainerOutput (2 methods)
| Method | Returns | Source |
|--------|---------|--------|
| `depositItem(m, container, item, count)` | `boolean` | MC + Forge |
| `withdrawItem(m, container, item, count)` | `boolean` | MC + Forge |

### EntityOutput (4 methods)
| Method | Returns | Source |
|--------|---------|--------|
| `hurt(target, source, amount)` | `void` | MC |
| `kill(target)` | `void` | MC |
| `setPos(e, x, y, z)` | `void` | MC |
| `setPos(e, pos)` | `void` | MC |

---

## Registry + API Layer

### ItemResolver (2 methods)
| Method | Returns | Source |
|--------|---------|--------|
| `resolve(id)` | `Item` | Forge Registry |
| `exists(id)` | `boolean` | Forge Registry |

### ParamExtractor (factory + accessors)
| Method | Returns | Source |
|--------|---------|--------|
| `from(raw, params)` | `ParamExtractor` | LMA Core |
| `getDouble(name)` / `getInt(name)` / `getString(name)` / `getBool(name)` | various | LMA Core |
| `resolveTarget(maid, ctxTarget)` | `LivingEntity` | LMA Core |
| `getMaid(maid)` | `EntityMaid` | LMA Core |

---

## Extension Points

- **InventoryReaderProvider** — add item sources (backpack, wireless chest, etc.)
- **InventorySpaceProvider** — add storage space calculations
- **ItemOutputProvider** — add item delivery mechanisms
- **BaseStateMachine** — multi-tick state machine framework
- **BrainHelper** — brain memory operations

---

> v11 2026-07-13 | 346 methods | 20 files | Compiled + 153 tests green
