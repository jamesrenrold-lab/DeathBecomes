# DeathBecomes

A command-only Minecraft hunter mob built around the DeathBecomes concept.

## Current design

- Spawn only through a command, near the player selected by that command.
- Use a player-shaped model with the supplied custom skin, scaled to 1.2× its normal width and 1.2× its normal height.
- Aggressively pursue the selected player at about 1.3× normal player sprint speed and break blocks that obstruct the route.
- Limit a chase to about 2 minutes; despawn early if the target gets more than 120 blocks away.
- Use the supplied “Lust For Blood (DUSTTRUST X) - Leminade” audio as chase music. Start it when the chase begins and stop it when the chase ends. The provided audio is 125.4 seconds, so the 120-second chase limit will end playback before the track finishes.
- Spawn a few blocks from the target while invisible, with red particles marking the hunter. Then reveal him, show a red “RUN!” title, briefly shake the camera, and begin the chase.
- Have 10,000 health.
- If it takes 500 damage during the chase, stop pursuing, turn away, become invisible, and despawn. Leave a consumable reward that permanently increases the user's maximum health by 1 HP when used.
- If it reaches its target, apply Weakness V and `cataclysm:stun` for 12 seconds. Deal a burst equal to 90% of the target's current health, then deal 0.5 damage ticks until the target is at 0.5 health; these ticks must never kill the target.
- Once the target is at 0.5 health, show a red actionbar message: “How can you live with yourself?” Five seconds later, turn away, become invisible, and despawn without dropping a reward. After the hunter has left and the 12-second stun expires, apply Weakness III and Slowness II to the target for 45 seconds.

## Initial platform assumption

Minecraft Forge 1.20.1, matching the user's current modding environment. Confirm loader and Minecraft version when implementation begins.

## Details to settle during implementation

- The hunter's damage and exact spawn distance/reveal delay.
- How the command selects its target and how the hunter behaves if that player logs out or dies.
- Which blocks the hunter may break.

The supplied skin reference will be added to the project assets when the entity renderer is implemented.
