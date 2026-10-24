# DeathBecomes

A command-only Minecraft hunter mob built around the DeathBecomes concept.

## Current design

- Spawn only through a command, near the player selected by that command.
- Use a player-shaped model with the supplied custom skin, scaled to 1.2× its normal width and 1.2× its normal height.
- Aggressively pursue the selected player and break blocks that obstruct the route.
- Have 10,000 health.
- If it takes 500 damage during the chase, stop pursuing, turn away, become invisible, and despawn. Leave a small reward item that grants a +0.5 attack or health bonus.
- If it reaches its target, apply Weakness V and `cataclysm:stun` for 20 seconds, deal damage, then leave the target weakened for a while as it departs.

## Initial platform assumption

Minecraft Forge 1.20.1, matching the user's current modding environment. Confirm loader and Minecraft version when implementation begins.

## Details to settle during implementation

- The hunter's damage, chase speed, and exact spawn distance.
- How long the lingering Weakness lasts after the initial 20-second attack.
- Whether the reward grants attack or health, and whether it can be used more than once.
- How the command selects its target and how the hunter behaves if that player logs out or dies.
- Which blocks the hunter may break.

The supplied skin reference will be added to the project assets when the entity renderer is implemented.
