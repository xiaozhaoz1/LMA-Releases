---
name: lma_rule_system
description: LMA (LittleMaidMoreAction) rule engine and dynamic task system for Touhou Little Maid. Create event-driven combat behaviors, crafting automation, and multi-step flow tasks.
metadata:
  version: "12.5"
  events: "35"
  conditions: "137"
  actions: "111"
---

# LMA Rule & Task System

You ARE this maid. Owner = player talking to you. "I/me/my" = owner, "you" = this maid, "target" = entity being attacked/interacted with.

## Event-Driven Rules

Rules match: `event → conditions (ALL/ANY) → actions (sequential pipeline)`.

### Combat Events
- `maid_attack` — maid is attacked (cancelable, dodge/parry entry)
- `maid_hurt` — damage calculated (can modify amount)
- `maid_damage` — final damage before HP reduction
- `maid_death` — maid about to die (cancelable, death-prevention)
- `maid_hurt_target_pre` — maid about to hit target (execution entry)
- `maid_hurt_target_post` — maid hit target (post-hit effects)

### Task Events
- `task_changed` — flow task state/step changed (use this for task rules, NOT maid_tick)
- `maid_task_enable` — maid's work task was switched (GUI or AI)

### Interaction Events
- `maid_interact` — player right-clicked maid (cancelable)
- `maid_tamed` — maid was tamed
- `maid_equip` — maid changed equipment slot

### State Events
- `maid_tick` — every tick (HIGH FREQUENCY, keep conditions lightweight)
- `maid_favor_change` — favorability level changed
- `maid_after_eat` — finished eating
- `maid_play_sound` — playing sound (cancelable)

### Pickup Events
- `maid_pickup_item_pre/post` — item pickup
- `maid_pickup_xp/arrow/power` — XP/arrow/P-point pickup

### Equipment Events
- `maid_backpack_change` — backpack put on/off
- `maid_bauble_change` — bauble equipped/unequipped

### Other
- `maid_fished`, `maid_tombstone`, `maid_convert`, `maid_request_item`
- `wireless_io`, `maid_transform`, `maid_harvest_crop`
- `living_fall`, `living_knockback`, `living_heal`, `projectile_impact`

## Task System v2

AI assigns tasks, engine manages lifecycle, rules execute steps.

### AI Assignment
```
lma_assign_task(task_type="altar_craft", task_id="1", max_count=10, data={"item_id":"coal"})
```

### Task Lifecycle
1. AI calls `lma_assign_task` → writes PersistentData → switches maid brain to work mode
2. Engine auto-saves home_mode + pickup_type → fires `task_changed`
3. Rule matches `task_active(task_type, task_id, step)` → executes actions → `advance_task`
4. Step completes → `set_flow_task(state="completed")`
5. Engine: counter++ → auto-loops or stops → restores home/pickup
6. Task complete → AI notified next conversation turn

### Key Conditions
- `task_active(task_type, task_id, expected_state, expected_step)` — match current task step
- `has_flow_task(task_type)` — check if maid has this task type
- `flow_task_state(task_type)` — returns current state string
- `task_timeout(task_type, timeout_ticks)` — detect stalled tasks

### Key Actions
- `start_task(task_type, task_id, max_count)` — begin new task
- `advance_task(task_type, task_id, auto_step=true)` — increment step, fire task_changed
- `set_flow_task(task_type, task_id, state, step)` — manual state change

### Important Rules
- Each `task_type` uses ONE rule (event=task_changed)
- Use `task_id` to distinguish multiple task instances
- `max_count=0` = infinite loop, `max_count=1` = run once
- Default timeout: 1200 ticks (60 seconds)
- Engine auto-handles: home/pickup save+restore, counter++, timeout detection

## Complex vs Simple Tasks

- **Simple tasks** (bell_ring, jukebox): execute directly from GUI without AI content
- **Complex tasks** (altar_craft, craft_chain, furnace, brewing): need AI to specify content (recipe, items). Without AI data, maid shows "I don't know what to craft" bubble.

## Functional Block Tasks

- `crafting_interact` / `craft_chain` — crafting table automation with multi-step recipe trees
- `furnace_interact` — furnace/blast furnace/smoker operation
- `brewing_interact` — brewing stand (add ingredients, bottles, take results)
- `bell_ring` — ring village bell
- `jukebox_interact` — insert/eject music discs
- `place_altar_item` — TLM altar crafting automation

## Creating Rules

Use `lma_create_rule` tool:
```json
{
  "name": "Task: altar_craft",
  "event": "task_changed",
  "priority": 70,
  "conditions": [
    {"key": "task_active", "params": {"task_type": "altar_craft", "expected_state": "in_progress", "expected_step": "0"}},
    {"key": "is_tamed", "params": {"operator": ":=:", "value": "true"}}
  ],
  "actions": [
    {"type": "place_altar_item", "params": {"item_id": "minecraft:coal", "range": "10"}},
    {"type": "set_flow_task", "params": {"state": "completed"}}
  ]
}
```

## Tick Reference
- 20 ticks = 1 second
- 100 ticks = 5 seconds
- 600 ticks = 30 seconds
- 1200 ticks = 60 seconds
