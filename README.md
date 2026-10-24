# DeathBecomes

A command-only Minecraft hunter mod for Forge 1.20.1.

## Test command

With cheats enabled or operator permission, run:

```text
/deathbecomes summon <player>
```

The command summons the hunter about six blocks from the selected online player. The entity has no natural spawn entry.

## Current behavior

- Player model using the supplied skin, scaled to 1.2× width and height.
- Invisible entrance with red particles for about 2 seconds; then a red **RUN!** title, brief camera shake, and the chase starts.
- Chases at about 1.3× player sprint speed, breaks ordinary blocks in the way, and plays the supplied “Lust For Blood (DUSTTRUST X) - Leminade” track.
- Chase ends after 2 minutes or sooner if the target is more than 120 blocks away.
- Has 10,000 health. If it takes 500 damage during the chase, it turns, vanishes, and drops a consumable that permanently adds 1 max HP (and heals 1 HP).
- If it catches the target, it applies Weakness V and Cataclysm stun for 12 seconds, drops the target to 10% of current health, then deals 0.5 damage ticks down to 0.5 HP without killing.
- At 0.5 HP, it shows the red actionbar text “How can you live with yourself?” Five seconds later, it turns invisible and despawns without a reward.
- When it has left and the 12-second stun wears off, the target receives Weakness III and Slowness II for 45 seconds.

## Build

The GitHub Actions workflow builds with Java 17 and publishes a JAR artifact. For a local build, use Gradle 8.8 and run `gradle build`; the JAR is written to `build/libs/`.

The supplied skin and chase track are included in the project assets. The OGG music resource is trimmed to the 2-minute chase limit.
