# DeathBecomes

A command-only Minecraft hunter mob built around the DeathBecomes concept.

## Current design

- Spawn only through a command, near the player selected by that command.
- Use a player-shaped model with the supplied custom skin, scaled to 1.2× its normal width and 1.2× its normal height.
- Aggressively pursue the selected player and break blocks that obstruct the route.
- Have 10,000 health.
- If it takes 500 damage during the chase, stop pursuing, turn away, become invisible, and despawn. Leave a consumable reward that permanently increases the user's maximum health by 1 HP when used.
- If it reaches its target, apply Weakness V and `cataclysm:stun` for 12 seconds. Deal a burst of damage equal to 90% of the target's current health, then deal 0.5 damage ticks without reducing the target below 0.5 health.
- Show the target a red actionbar message: “How can you live with yourself?” After 5 seconds, turn away, become invisible, and despawn without dropping a reward.

## Initial platform assumption

Minecraft Forge 1.20.1, matching the user's current modding environment. Confirm loader and Minecraft version when implementation begins.

## Details to settle during implementation

- The hunter's damage, chase speed, and exact spawn distance.
- Whether the +1 max-health consumable can stack and whether using it also heals 1 HP.
- How the command selects its target and how the hunter behaves if that player logs out or dies.
- Which blocks the hunter may break.

The supplied skin reference will be added to the project assets when the entity renderer is implemented.
